/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.run.config.Settings
import com.mogobiz.run.config.Settings.NextVal
import scalikejdbc._

/**
  *
  */
trait BoService {
  def newId()(implicit session: DBSession): Int = {
    val res = session.connection.createStatement().executeQuery(NextVal)
    res.next()
    res.getInt(1)
  }
}
