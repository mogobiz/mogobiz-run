package com.mogobiz.cart
import scalikejdbc._
/**
 * 
 * Created by Christophe on 07/05/2014.
 */
trait BoService {
  def newId()(implicit session: DBSession):Int={
    // TODO dialect
    val res = sql"select nextVal('hibernate_sequence')".map(rs => rs.int("nextval")).single().apply()
    res.get
  }
}
