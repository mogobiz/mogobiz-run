/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.sql

import java.util.{ Date, UUID }

import com.mogobiz.json.JacksonConverter
import com.mogobiz.pay.model
import com.mogobiz.run.sql.Sql.Comment
import scalikejdbc._

object CommentDAO extends SQLSyntaxSupport[Comment] with SqlService {
  override val tableName = "comment"

  //  implicit val uuidTypeBinder: TypeBinder[UUID] = new TypeBinder[UUID] {
  //    def apply(rs: ResultSet, label: String): UUID = UUID.fromString(rs.getString(label))
  //    def apply(rs: ResultSet, index: Int): UUID = UUID.fromString(rs.getString(index))
  //  }

  def apply(rn: ResultName[Comment])(rs: WrappedResultSet): Comment = Comment(
    rs.get(rn.id),
    UUID.fromString(rs.get(rn.uuid)),
    rs.get(rn.extra),
    rs.date(rn.dateCreated),
    rs.date(rn.lastUpdated))

  def create(comment: com.mogobiz.run.model.Comment)(implicit session: DBSession): Comment = {
    val newComment = new Comment(newId(), UUID.fromString(comment.id), JacksonConverter.serialize(comment),
      new Date, new Date)

    applyUpdate {
      insert.into(CommentDAO).namedValues(
        CommentDAO.column.id -> newComment.id,
        CommentDAO.column.uuid -> newComment.uuid.toString,
        CommentDAO.column.extra -> newComment.extra,
        CommentDAO.column.dateCreated -> newComment.dateCreated,
        CommentDAO.column.lastUpdated -> newComment.lastUpdated
      )
    }

    newComment
  }

  def upsert(comment: com.mogobiz.run.model.Comment): Unit = {
    DB localTx { implicit session =>
      val updateResult = update(comment)
      if (updateResult == 0) create(comment)
    }
  }

  def update(comment: com.mogobiz.run.model.Comment): Int = {
    DB localTx { implicit session =>
      applyUpdate {
        QueryDSL.update(CommentDAO).set(
          CommentDAO.column.extra -> JacksonConverter.serialize(comment),
          CommentDAO.column.lastUpdated -> new Date
        ).where.eq(CommentDAO.column.uuid, comment.id)
      }
    }
  }
}
