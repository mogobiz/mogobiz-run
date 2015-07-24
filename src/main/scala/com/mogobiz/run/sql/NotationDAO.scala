/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.sql

import java.util.{Date, UUID}

import com.mogobiz.json.JacksonConverter
import com.mogobiz.run.sql.Sql.Notation
import scalikejdbc._

object NotationDAO extends SQLSyntaxSupport[Notation] with SqlService {
  override val tableName = "notation"

  //  implicit val uuidTypeBinder: TypeBinder[UUID] = new TypeBinder[UUID] {
  //    def apply(rs: ResultSet, label: String): UUID = UUID.fromString(rs.getString(label))
  //    def apply(rs: ResultSet, index: Int): UUID = UUID.fromString(rs.getString(index))
  //  }

  def apply(rn: ResultName[Notation])(rs: WrappedResultSet): Notation = Notation(
    rs.get(rn.id),
    UUID.fromString(rs.get(rn.uuid)),
    rs.get(rn.extra),
    rs.date(rn.dateCreated),
    rs.date(rn.lastUpdated))

  def create(notation: com.mogobiz.run.model.Notation)(implicit session: DBSession): Notation = {
    val newNotation = new Notation(newId(), UUID.fromString(notation.uuid), JacksonConverter.serialize(notation), new Date, new Date)

    applyUpdate {
      insert.into(NotationDAO).namedValues(
        NotationDAO.column.id -> newNotation.id,
        NotationDAO.column.uuid -> newNotation.uuid.toString,
        NotationDAO.column.extra -> newNotation.extra,
        NotationDAO.column.dateCreated -> newNotation.dateCreated,
        NotationDAO.column.lastUpdated -> newNotation.lastUpdated
      )
    }
    newNotation
  }

  def upsert(notation: com.mogobiz.run.model.Notation): Unit = {
    DB localTx { implicit session =>
      val updateResult = update(notation)
      if (updateResult == 0) create(notation)
    }
  }

  def update(notation: com.mogobiz.run.model.Notation): Int = {
    DB localTx { implicit session =>
      applyUpdate {
        QueryDSL.update(NotationDAO).set(
          NotationDAO.column.extra -> JacksonConverter.serialize(notation),
          NotationDAO.column.lastUpdated -> new Date
        ).where.eq(NotationDAO.column.uuid, notation.uuid)
      }
    }
  }
}
