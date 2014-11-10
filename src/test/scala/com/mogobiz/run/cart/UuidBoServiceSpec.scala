package com.mogobiz.run.cart

import com.mogobiz.run.config.MogobizDBsWithEnv
import org.specs2.mutable.Specification
import java.util.UUID
import scalikejdbc.config.DBsWithEnv

/**
 *
 * Created by Christophe on 06/05/2014.
 */
class UuidBoServiceSpec extends Specification {

  MogobizDBsWithEnv("test").setupAll()

  val service = UuidBoService

  "store and get the stored cart" in {
    val uuid = UUID.randomUUID.toString
    //println(s"uuid=${uuid}")
    val cart = new CartVO(uuid=uuid, userUuid = None)
    service.setCart(cart)

    val data = service.getCart(uuid, None)
    println(data)
    data must not beNone
    val getCart = data.get
    getCart.uuid must be_==(cart.uuid)

  }
}
