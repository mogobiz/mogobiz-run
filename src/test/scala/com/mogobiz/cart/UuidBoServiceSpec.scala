package com.mogobiz.cart

import org.specs2.mutable.Specification
import com.mogobiz.cart.CartVO
import java.util.UUID
import scalikejdbc.config.DBs

/**
 * Created by Christophe on 06/05/2014.
 */
class UuidBoServiceSpec extends Specification {

  DBs.setupAll()

  val service = UuidBoService

  "store an empty cart" in {
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = new CartVO(uuid=uuid)
    val res = service.set(cart)
    println(res)
    res.id must not beNone
  }

  "get the stored cart" in {
    val uuid = UUID.randomUUID.toString
    //println(s"uuid=${uuid}")
    val cart = new CartVO(uuid=uuid)
    val res = service.set(cart)
    res.id must not beNone

    val data = service.get(uuid)
    println(data)
    data must not beNone
    val getCart = data.get
    getCart.uuid must be_==(cart.uuid)

  }
}
