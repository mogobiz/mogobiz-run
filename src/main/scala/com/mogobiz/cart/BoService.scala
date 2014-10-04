package com.mogobiz.cart

import scalikejdbc._
import com.mogobiz.config.Settings.NextVal

/**
 * 
 * Created by Christophe on 07/05/2014.
 */
trait BoService {
  def newId()(implicit session: DBSession):Int={
    val res = sql"$NextVal".map(rs => rs.int(1)).single().apply()
    res.get
  }
}
