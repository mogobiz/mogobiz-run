/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.sql

object Sql {

  import java.util.Date

  case class WishlistList(id: Long, uuid: java.util.UUID, extra: String, dateCreated: Date, lastUpdated: Date)

  case class Comment(id: Long, uuid: java.util.UUID, extra: String, dateCreated: Date, lastUpdated: Date)

  case class Notation(id: Long, uuid: java.util.UUID, extra: String, dateCreated: Date, lastUpdated: Date)

}
