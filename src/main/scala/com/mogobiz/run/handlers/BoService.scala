package com.mogobiz.run.handlers

import com.mogobiz.run.config.Settings
import com.mogobiz.run.config.Settings.NextVal
import scalikejdbc._

/**
 * 
 * Created by Christophe on 07/05/2014.
 */
trait BoService {
  def newId()(implicit session: DBSession):Int={
    val res = session.connection.createStatement().executeQuery(NextVal)
    res.next()
    res.getInt(1)
  }
}
