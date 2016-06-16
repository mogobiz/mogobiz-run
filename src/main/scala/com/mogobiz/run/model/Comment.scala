/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.json.JacksonConverter
import com.mogobiz.run.handlers.BoService
import org.joda.time.DateTime
import scalikejdbc._

case class Comment(id: String,
  userId: String,
  surname: String,
  notation: Int,
  subject: String,
  comment: String,
  externalCode: Option[String],
  created: Date,
  productId: Long,
  useful: Int = 0,
  notuseful: Int = 0)

case class BOComment(id: Long,
  uuid: String,
  company: String,
  extra: String,
  dateCreated: DateTime,
  lastUpdated: DateTime)

object BOCommentDao extends SQLSyntaxSupport[BOComment] with BoService {

  override val tableName = "b_o_comment"

  def apply(rn: ResultName[BOComment])(rs: WrappedResultSet): BOComment = BOComment(
    rs.get(rn.id),
    rs.get(rn.uuid),
    rs.get(rn.company),
    rs.get(rn.extra),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated)
  )

  def load(company: String, uuid: String)(implicit session: DBSession): Option[BOComment] = {
    val t = BOCommentDao.syntax("t")
    val comment = withSQL {
      select.from(BOCommentDao as t).where.eq(t.uuid, uuid)
    }.map(BOCommentDao(t.resultName)).single().apply()
    comment.filter { c => c.company == company }
  }

  private def getExtra(comment: Comment): String = {
    JacksonConverter.serialize(comment)
  }

  def save(comment: BOComment, commentExtra: Comment)(implicit session: DBSession): BOComment = {
    val updatedComment = comment.copy(lastUpdated = DateTime.now(), extra = getExtra(commentExtra))

    withSQL {
      update(BOCommentDao).set(
        BOCommentDao.column.id -> updatedComment.id,
        BOCommentDao.column.uuid -> updatedComment.uuid,
        BOCommentDao.column.company -> updatedComment.company,
        BOCommentDao.column.extra -> updatedComment.extra,
        BOCommentDao.column.dateCreated -> updatedComment.dateCreated,
        BOCommentDao.column.lastUpdated -> updatedComment.lastUpdated
      ).where.eq(BOCommentDao.column.id, updatedComment.id)
    }.update.apply()

    updatedComment
  }

  def create(company: String, commentExtra: Comment)(implicit session: DBSession): BOComment = {

    val newComment = new BOComment(
      newId(),
      commentExtra.id,
      company,
      getExtra(commentExtra),
      DateTime.now,
      DateTime.now)

    applyUpdate {
      insert.into(BOCommentDao).namedValues(
        BOCommentDao.column.id -> newComment.id,
        BOCommentDao.column.uuid -> newComment.uuid,
        BOCommentDao.column.company -> newComment.company,
        BOCommentDao.column.extra -> newComment.extra,
        BOCommentDao.column.dateCreated -> newComment.dateCreated,
        BOCommentDao.column.lastUpdated -> newComment.lastUpdated
      )
    }
    newComment
  }

  def delete(uuid: String)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCommentDao).where.eq(BOCommentDao.column.uuid, uuid)
    }.update.apply()
  }

}