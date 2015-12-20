/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.sql

import java.util.{ Date, UUID }

import com.mogobiz.json.JacksonConverter
import com.mogobiz.run.sql.Sql.WishlistList
import scalikejdbc._

object WishlistListDAO extends SQLSyntaxSupport[WishlistList] with SqlService {
  override val tableName = "wishlist"

  //  implicit val uuidTypeBinder: TypeBinder[UUID] = new TypeBinder[UUID] {
  //    def apply(rs: ResultSet, label: String): UUID = UUID.fromString(rs.getString(label))
  //    def apply(rs: ResultSet, index: Int): UUID = UUID.fromString(rs.getString(index))
  //  }

  def apply(rn: ResultName[WishlistList])(rs: WrappedResultSet): WishlistList = WishlistList(
    rs.get(rn.id),
    UUID.fromString(rs.get(rn.uuid)),
    rs.get(rn.extra),
    rs.date(rn.dateCreated),
    rs.date(rn.lastUpdated))

  def create(wishlistList: com.mogobiz.run.model.WishlistList)(implicit session: DBSession): WishlistList = {
    val newWishlist = new WishlistList(newId(), UUID.fromString(wishlistList.uuid), JacksonConverter.serialize(WishlistList),
      new Date, new Date)

    applyUpdate {
      insert.into(WishlistListDAO).namedValues(
        WishlistListDAO.column.id -> newWishlist.id,
        WishlistListDAO.column.uuid -> newWishlist.uuid.toString,
        WishlistListDAO.column.extra -> newWishlist.extra,
        WishlistListDAO.column.dateCreated -> newWishlist.dateCreated,
        WishlistListDAO.column.lastUpdated -> newWishlist.lastUpdated
      )
    }

    newWishlist
  }

  def upsert(wishlistList: com.mogobiz.run.model.WishlistList): Unit = {
    DB localTx { implicit session =>
      val updateResult = update(wishlistList)
      if (updateResult == 0) create(wishlistList)
    }
  }

  def update(wishlistList: com.mogobiz.run.model.WishlistList): Int = {
    DB localTx { implicit session =>
      applyUpdate {
        QueryDSL.update(WishlistListDAO).set(
          WishlistListDAO.column.extra -> JacksonConverter.serialize(wishlistList),
          WishlistListDAO.column.lastUpdated -> new Date
        ).where.eq(WishlistListDAO.column.uuid, wishlistList.uuid)
      }
    }
  }
}
