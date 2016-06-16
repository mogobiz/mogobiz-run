/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.json.JacksonConverter
import com.mogobiz.run.handlers.BoService
import com.mogobiz.utils.GlobalUtil._
import org.joda.time.DateTime
import scalikejdbc._

object WishlistVisibility extends Enumeration {

  type WishlistVisibility = Value

  val PUBLIC = Value("Public")
  val PRIVATE = Value("Private")
  val SHARED = Value("Shared")
}

class WishlistVisibilityRef extends TypeReference[WishlistVisibility.type]

import com.mogobiz.run.model.WishlistVisibility._

case class WishIdea(uuid: String = newUUID, name: String)

case class WishBrand(uuid: String = newUUID, name: String, brand: String)

case class WishCategory(uuid: String = newUUID, name: String, category: String)

case class WishItem(uuid: String = newUUID, name: String, product: String, sku: Option[String] = None)

case class WishlistOwner(email: String, name: Option[String] = None, dayOfBirth: Option[Int] = None, monthOfBirth: Option[Int] = None, description: Option[String] = None)

case class Wishlist(uuid: String = newUUID,
  name: String,
  @JsonScalaEnumeration(classOf[WishlistVisibilityRef]) visibility: WishlistVisibility = WishlistVisibility.PRIVATE,
  default: Boolean = false,
  token: String = newUUID,
  externalCode: Option[String] = None,
  ideas: List[WishIdea] = List(),
  items: List[WishItem] = List(),
  brands: List[WishBrand] = List(), // Not yet available
  categories: List[WishCategory] = List(), // Not yet available
  alert: Boolean = false, // Ignored
  var dateCreated: Date = null,
  var lastUpdated: Date = null)

case class WishlistList(uuid: String = newUUID,
  wishlists: List[Wishlist] = List(),
  owner: WishlistOwner,
  var dateCreated: Date = null,
  var lastUpdated: Date = null)

case class AddWishlistCommand(name: String, visibility: WishlistVisibility = WishlistVisibility.PRIVATE, defaultIndicator: Boolean = false, owner_email: String, externalCode: Option[String])

case class AddItemCommand(name: String, product: String, owner_email: String, product_sku: Option[String] = None)

case class AddBrandCommand(name: String, brand: String, owner_email: String)

case class AddCategoryCommand(name: String, category: String, owner_email: String)

case class BOWishList(id: Long,
  uuid: String,
  company: String,
  extra: String,
  dateCreated: DateTime,
  lastUpdated: DateTime)

object BOWishListDao extends SQLSyntaxSupport[BOWishList] with BoService {

  override val tableName = "b_o_wishlist"

  def apply(rn: ResultName[BOWishList])(rs: WrappedResultSet): BOWishList = BOWishList(
    rs.get(rn.id),
    rs.get(rn.uuid),
    rs.get(rn.company),
    rs.get(rn.extra),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated)
  )

  def load(company: String, uuid: String)(implicit session: DBSession): Option[BOWishList] = {
    val t = BOWishListDao.syntax("t")
    val whishList = withSQL {
      select.from(BOWishListDao as t).where.eq(t.uuid, uuid)
    }.map(BOWishListDao(t.resultName)).single().apply()
    whishList.filter { c => c.company == company }
  }

  private def getExtra(wishlist: WishlistList): String = {
    JacksonConverter.serialize(wishlist)
  }

  def save(wishlist: BOWishList, wishlistExtra: WishlistList)(implicit session: DBSession): BOWishList = {
    val updatedWishlist = wishlist.copy(lastUpdated = DateTime.now(), extra = getExtra(wishlistExtra))

    withSQL {
      update(BOWishListDao).set(
        BOWishListDao.column.id -> updatedWishlist.id,
        BOWishListDao.column.uuid -> updatedWishlist.uuid,
        BOWishListDao.column.company -> updatedWishlist.company,
        BOWishListDao.column.extra -> updatedWishlist.extra,
        BOWishListDao.column.dateCreated -> updatedWishlist.dateCreated,
        BOWishListDao.column.lastUpdated -> updatedWishlist.lastUpdated
      ).where.eq(BOWishListDao.column.id, updatedWishlist.id)
    }.update.apply()

    updatedWishlist
  }

  def create(company: String, wishlistExtra: WishlistList)(implicit session: DBSession): BOWishList = {

    val newWishlist = new BOWishList(
      newId(),
      wishlistExtra.uuid,
      company,
      getExtra(wishlistExtra),
      DateTime.now,
      DateTime.now)

    applyUpdate {
      insert.into(BOWishListDao).namedValues(
        BOWishListDao.column.id -> newWishlist.id,
        BOWishListDao.column.uuid -> newWishlist.uuid,
        BOWishListDao.column.company -> newWishlist.company,
        BOWishListDao.column.extra -> newWishlist.extra,
        BOWishListDao.column.dateCreated -> newWishlist.dateCreated,
        BOWishListDao.column.lastUpdated -> newWishlist.lastUpdated
      )
    }
    newWishlist
  }
  /*
  def delete(uuid: String)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(CommentDao).where.eq(CommentDao.column.uuid, uuid)
    }.update.apply()
  }
*/
}