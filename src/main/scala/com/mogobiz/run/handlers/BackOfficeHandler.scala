package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
import java.util.UUID

import com.mogobiz.run.cart.BoService
import com.mogobiz.run.config.Settings
import com.mogobiz.run.model.{StoreCart, Mogobiz, Render, Currency}
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.utils.Utils
import com.mogobiz.utils.{QRCodeUtils, SymmetricCrypt}
import com.sun.org.apache.xml.internal.security.utils.Base64
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scalikejdbc._

/**
 * Created by yoannbaudy on 26/11/2014.
 */
class BackOfficeHandler {

  def failedTransaction(boCart: BOCart) : Unit = {
    // Mise à jour du statut
    DB localTx { implicit session =>
      withSQL {
        update(BOCartDao).set(
          BOCartDao.column.status -> TransactionStatus.FAILED.toString,
          BOCartDao.column.lastUpdated -> DateTime.now
        ).where.eq(BOCartDao.column.id, boCart.id)
      }.update.apply()
    }
  }

  def deletePendingTransaction(uuid: String) = {
    DB localTx { implicit session =>
      // On supprime tout ce qui concerne l'ancien BOCart (s'il est en attente)
      val boCart = BOCartDao.findByTransactionUuidAndStatus(uuid, TransactionStatus.PENDING)
      if (boCart.isDefined) {
        BOCartItemDao.findByBOCart(boCart.get).foreach { boCartItem =>
          BOCartItemDao.bOProducts(boCartItem).foreach { boProduct =>
            sql"delete from b_o_cart_item_b_o_product where boproduct_id=${boProduct.id}".update.apply()
            BOTicketTypeDao.findByBOProduct(boProduct.id).foreach { boTicketType =>
              BOTicketTypeDao.delete(boTicketType)
            }
            BOProductDao.delete(boProduct)
          }
          BOCartItemDao.delete(boCartItem)
        }
        BOCartDao.delete(boCart.get)
      }
    }
  }

  def createTransaction(storeCart: StoreCart, storeCode: String, cart: Render.Cart, transactionUuid: String, rate: Currency, buyer: String, company: Company)(implicit session: DBSession) : StoreCart = {
    val boCart = BOCartDao.create(buyer, company.id, rate, cart.finalPrice, transactionUuid)

    val newStoreCartItems = cart.cartItemVOs.map { cartItem =>
      val storeCartItem = storeCart.cartItems.find(i => (i.productId == cartItem.productId) && (i.skuId == cartItem.skuId)).get

      val productAndSku = ProductDao.getProductAndSku(storeCode, cartItem.skuId)
      val product = productAndSku.get._1
      val sku = productAndSku.get._2

      // Création du BOProduct correspondant au produit principal
      val boProduct = BOProductDao.create(cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice), true, cartItem.productId)

      val newStoreRegistedCartItems = cartItem.registeredCartItemVOs.map { registeredCartItem =>
        val storeRegistedCartItem = storeCartItem.registeredCartItems.find(r => r.email == registeredCartItem.email).get
        val boTicketId = BOTicketTypeDao.newId()

        val shortCodeAndQrCode = product.xtype match {
          case ProductType.SERVICE => {
            val startDateStr = cartItem.startDate.map(d => d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))
            val shortCode = "P" + boProduct.id + "T" + boTicketId
            val qrCodeContent = "EventId:" + product.id + ";BoProductId:" + boProduct.id + ";BoTicketId:" + boTicketId +
              ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
              registeredCartItem.firstname.getOrElse("") + ";LastName:" + registeredCartItem.lastname.getOrElse("") +
              ";Phone:" + registeredCartItem.phone.getOrElse("") + ";TicketType:" + sku.name + ";shortCode:" + shortCode

            val encryptedQrCodeContent = SymmetricCrypt.encrypt(qrCodeContent, company.aesPassword, "AES")
            val output = new ByteArrayOutputStream()
            QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
            val qrCodeBase64 = Base64.encode(output.toByteArray)

            (Some(shortCode), Some(qrCodeBase64), Some(encryptedQrCodeContent))
          }
          case _ => (None, None, None)
        }

        BOTicketTypeDao.create(boTicketId, sku, cartItem, registeredCartItem, shortCodeAndQrCode._1, shortCodeAndQrCode._2, shortCodeAndQrCode._3, boProduct.id)
        if (shortCodeAndQrCode._3.isDefined)
          storeRegistedCartItem.copy(qrCodeContent = Some(product.name + ":" + registeredCartItem.email + "||" + shortCodeAndQrCode._3.get))
        else storeRegistedCartItem
      }

      //create Sale
      BOCartItemDao.create(sku, cartItem, boCart, boProduct.id)

      storeCartItem.copy(registeredCartItems = newStoreRegistedCartItems.toList)
    }

    storeCart.copy(cartItems = newStoreCartItems.toList)
  }

  def completeTransaction(boCart: BOCart, transactionUuid: String)(implicit session: DBSession) : Unit = {
    // update status and transactionUUID
    withSQL {
      update(BOCartDao).set(
        BOCartDao.column.transactionUuid -> transactionUuid,
        BOCartDao.column.status -> TransactionStatus.COMPLETE.toString,
        BOCartDao.column.lastUpdated -> DateTime.now
      ).where.eq(BOCartDao.column.id, boCart.id)
    }.update.apply()
  }
}

object BOCartDao extends SQLSyntaxSupport[BOCart] with BoService {

  override val tableName = "b_o_cart"

  def apply(rn: ResultName[BOCart])(rs: WrappedResultSet): BOCart = BOCart(
    rs.get(rn.id),
    rs.get(rn.buyer),
    rs.get(rn.companyFk),
    rs.get(rn.currencyCode),
    rs.get(rn.currencyRate),
    rs.get(rn.xdate),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.price),
    TransactionStatus.valueOf(rs.get(rn.status)),
    rs.get(rn.transactionUuid),
    rs.get(rn.uuid))

  def findByTransactionUuid(uuid:String):Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    DB readOnly { implicit session =>
      withSQL {select.from(BOCartDao as t).where.eq(t.transactionUuid, uuid)}.map(BOCartDao(t.resultName)).single().apply()
    }
  }

  def findByTransactionUuidAndStatus(uuid:String, status:TransactionStatus)(implicit session : DBSession):Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    withSQL {
      select.from(BOCartDao as t).where.eq(t.transactionUuid, uuid).and.eq(t.status, status.toString)
    }.map(BOCartDao(t.resultName)).single().apply()
  }

  def create(buyer: String, companyId: Long, rate: Currency, price: Long, transactionUuid: String)(implicit session: DBSession):BOCart = {

    val newBoCart = new BOCart(
      newId(),
      buyer,
      companyId,
      rate.code,
      rate.rate,
      DateTime.now,
      DateTime.now,
      DateTime.now,
      price,
      TransactionStatus.PENDING,
      transactionUuid,
      UUID.randomUUID().toString
    )

    applyUpdate {
      insert.into(BOCartDao).namedValues(
        BOCartDao.column.id -> newBoCart.id,
        BOCartDao.column.buyer -> newBoCart.buyer,
        BOCartDao.column.companyFk -> newBoCart.companyFk,
        BOCartDao.column.currencyCode -> newBoCart.currencyCode,
        BOCartDao.column.currencyRate -> newBoCart.currencyRate,
        BOCartDao.column.xdate -> newBoCart.xdate,
        BOCartDao.column.dateCreated -> newBoCart.dateCreated,
        BOCartDao.column.lastUpdated -> newBoCart.lastUpdated,
        BOCartDao.column.price -> newBoCart.price,
        BOCartDao.column.status -> newBoCart.status.toString(),
        BOCartDao.column.transactionUuid -> newBoCart.transactionUuid,
        BOCartDao.column.uuid -> newBoCart.uuid
      )
    }

    newBoCart
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.id,  boCart.id)
    }.update.apply()
  }

}

object BOCartItemDao extends SQLSyntaxSupport[BOCartItem] with BoService {

  override val tableName = "b_o_cart_item"

  def apply(rn: ResultName[BOCartItem])(rs:WrappedResultSet): BOCartItem = new BOCartItem(
    rs.get(rn.id),
    rs.get(rn.code),
    rs.get(rn.price),
    rs.get(rn.tax),
    rs.get(rn.endPrice),
    rs.get(rn.totalPrice),
    rs.get(rn.totalEndPrice),
    rs.get(rn.hidden),
    rs.get(rn.quantity),
    rs.get(rn.startDate),
    rs.get(rn.endDate),
    rs.get(rn.ticketTypeFk),
    rs.get(rn.bOCartFk),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.uuid))

  def create(sku: Mogobiz.Sku, cartItem : Render.CartItem, boCart: BOCart, boProductId : Long)(implicit session: DBSession) : BOCartItem = {
    val newBOCartItem = new BOCartItem(
      newId(),
      "SALE_" + boCart.id + "_" + boProductId,
      cartItem.price,
      cartItem.tax.getOrElse(0f).toDouble,
      cartItem.saleEndPrice.getOrElse(cartItem.salePrice),
      cartItem.saleTotalPrice,
      cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
      false,
      cartItem.quantity,
      cartItem.startDate,
      cartItem.endDate,
      sku.id,
      boCart.id,
      DateTime.now,
      DateTime.now,
      UUID.randomUUID().toString
    )

    applyUpdate {
      insert.into(BOCartItemDao).namedValues(
        BOCartItemDao.column.id -> newBOCartItem.id,
        BOCartItemDao.column.code -> newBOCartItem.code,
        BOCartItemDao.column.price -> newBOCartItem.price,
        BOCartItemDao.column.tax -> newBOCartItem.tax,
        BOCartItemDao.column.endPrice -> newBOCartItem.endPrice,
        BOCartItemDao.column.totalPrice -> newBOCartItem.totalPrice,
        BOCartItemDao.column.totalEndPrice -> newBOCartItem.totalEndPrice,
        BOCartItemDao.column.hidden -> newBOCartItem.hidden,
        BOCartItemDao.column.quantity -> newBOCartItem.quantity,
        BOCartItemDao.column.startDate -> newBOCartItem.startDate,
        BOCartItemDao.column.endDate -> newBOCartItem.endDate,
        BOCartItemDao.column.ticketTypeFk -> newBOCartItem.ticketTypeFk,
        BOCartItemDao.column.bOCartFk -> newBOCartItem.bOCartFk,
        BOCartItemDao.column.dateCreated -> newBOCartItem.dateCreated,
        BOCartItemDao.column.lastUpdated -> newBOCartItem.lastUpdated,
        BOCartItemDao.column.uuid -> newBOCartItem.uuid
      )
    }

    sql"insert into b_o_cart_item_b_o_product(b_o_products_fk, boproduct_id) values(${newBOCartItem.id},$boProductId)"
      .update.apply()

    newBOCartItem
  }

    def findByBOCart(boCart:BOCart)(implicit session: DBSession):List[BOCartItem] = {
      val t = BOCartItemDao.syntax("t")
      withSQL {
        select.from(BOCartItemDao as t).where.eq(t.bOCartFk, boCart.id)
      }.map(BOCartItemDao(t.resultName)).list().apply()
    }

    def bOProducts(boCartItem: BOCartItem)(implicit session: DBSession): List[BOProduct] = {
        sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCartItem.id}"
          .map(rs => BOProductDao(rs)).list().apply()
    }

    def delete(boCartItem : BOCartItem)(implicit session: DBSession) = {
      withSQL {
        deleteFrom(BOCartItemDao).where.eq(BOCartItemDao.column.id, boCartItem.id)
      }.update.apply()
    }
}

object BOTicketTypeDao extends SQLSyntaxSupport[BOTicketType] with BoService {

  override val tableName = "b_o_ticket_type"

  def apply(rn: ResultName[BOTicketType])(rs:WrappedResultSet): BOTicketType = new BOTicketType(
    rs.get(rn.id),
    rs.get(rn.quantity),
    rs.get(rn.price),
    rs.get(rn.shortCode),
    rs.get(rn.ticketType),
    rs.get(rn.firstname),
    rs.get(rn.lastname),
    rs.get(rn.email),
    rs.get(rn.phone),
    rs.get(rn.age),
    rs.get(rn.birthdate),
    rs.get(rn.startDate),
    rs.get(rn.endDate),
    rs.get(rn.qrcode),
    rs.get(rn.qrcodeContent),
    rs.get(rn.bOProductFk),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.uuid))

  def create(boTicketId: Long, sku: Sku, cartItem: Render.CartItem, registeredCartItem: Render.RegisteredCartItem, shortCode: Option[String], qrCode: Option[String], qrCodeContent: Option[String], boProductId: Long)(implicit session: DBSession) : BOTicketType = {
    val newBOTicketType = new BOTicketType(
      boTicketId,
      1,  // Un seul ticket par bénéficiaire
      cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
      shortCode,
      Some(sku.name),
      registeredCartItem.firstname,
      registeredCartItem.lastname,
      Some(registeredCartItem.email),
      registeredCartItem.phone,
      Utils.computeAge(registeredCartItem.birthdate),
      registeredCartItem.birthdate,
      cartItem.startDate,
      cartItem.endDate,
      qrCode,
      qrCodeContent,
      boProductId,
      DateTime.now,
      DateTime.now,
      UUID.randomUUID().toString
    )

    applyUpdate {
      insert.into(BOTicketTypeDao).namedValues(
        BOTicketTypeDao.column.uuid -> newBOTicketType.uuid,
        BOTicketTypeDao.column.id -> newBOTicketType.id,
        BOTicketTypeDao.column.shortCode -> newBOTicketType.shortCode,
        BOTicketTypeDao.column.quantity -> newBOTicketType.quantity,
        BOTicketTypeDao.column.price -> newBOTicketType.price,
        BOTicketTypeDao.column.ticketType -> newBOTicketType.ticketType,
        BOTicketTypeDao.column.firstname -> newBOTicketType.firstname,
        BOTicketTypeDao.column.lastname -> newBOTicketType.lastname,
        BOTicketTypeDao.column.email -> newBOTicketType.email,
        BOTicketTypeDao.column.phone -> newBOTicketType.phone,
        BOTicketTypeDao.column.age -> newBOTicketType.age,
        BOTicketTypeDao.column.birthdate -> newBOTicketType.birthdate,
        BOTicketTypeDao.column.startDate -> newBOTicketType.startDate,
        BOTicketTypeDao.column.endDate -> newBOTicketType.endDate,
        BOTicketTypeDao.column.qrcode -> newBOTicketType.qrcode,
        BOTicketTypeDao.column.qrcodeContent -> newBOTicketType.qrcodeContent,
        BOTicketTypeDao.column.dateCreated -> newBOTicketType.dateCreated,
        BOTicketTypeDao.column.lastUpdated -> newBOTicketType.lastUpdated,
        BOTicketTypeDao.column.bOProductFk -> newBOTicketType.bOProductFk
      )
    }

    newBOTicketType
  }

  def findByBOProduct(boProductId:Long)(implicit session: DBSession):List[BOTicketType]={
    val t = BOTicketTypeDao.syntax("t")
    withSQL {
      select.from(BOTicketTypeDao as t).where.eq(t.bOProductFk, boProductId)
    }.map(BOTicketTypeDao(t.resultName)).list().apply()
  }

  def delete(boTicketType:BOTicketType)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOTicketTypeDao).where.eq(BOTicketTypeDao.column.id, boTicketType.id)
    }.update.apply()
  }
}

object BOProductDao extends SQLSyntaxSupport[BOProduct] with BoService {

  override val tableName = "b_o_product"

  def apply(rs:WrappedResultSet): BOProduct = new BOProduct(
    rs.long("id"),
    rs.boolean("acquittement"),
    rs.long("price"),
    rs.boolean("principal"),
    rs.long("product_fk"),
    rs.get("date_created"),
    rs.get("last_updated"),
    rs.get("uuid"))

  def create(price:Long, principal:Boolean, productId:Long)(implicit session: DBSession) : BOProduct = {
    val newBOProduct = new BOProduct(
      newId(),
      false,
      price,
      principal,
      productId,
      DateTime.now(),
      DateTime.now(),
      UUID.randomUUID().toString
    )

    var boProductId = 0
    applyUpdate {
      insert.into(BOProductDao).namedValues(
        BOProductDao.column.id -> newBOProduct.id,
        BOProductDao.column.acquittement -> newBOProduct.acquittement,
        BOProductDao.column.price -> newBOProduct.price,
        BOProductDao.column.principal -> newBOProduct.principal,
        BOProductDao.column.productFk -> newBOProduct.productFk,
        BOProductDao.column.dateCreated -> newBOProduct.dateCreated,
        BOProductDao.column.lastUpdated -> newBOProduct.lastUpdated,
        BOProductDao.column.uuid -> newBOProduct.uuid
      )
    }

    newBOProduct
  }

  def delete(boProduct:BOProduct)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOProductDao).where.eq(BOProductDao.column.id, boProduct.id)
    }.update.apply()
  }
}