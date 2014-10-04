package com.mogobiz.cart

import org.specs2.mutable.Specification
import scalikejdbc.config.DBsWithEnv
import com.mogobiz.cart.domain._

/**
 *
 * Created by Christophe on 07/07/2014.
 */
class ProductSpec extends Specification {

  DBsWithEnv("test").setupAll()


  "get by id" in {
    var taxRateId = 13
    val id = 61
    val res = Product.get(id)

    //res must beSome(coupon)
    res must beSome[Product]
    val product = res.get
    product.id must be_==(id)
    product.taxRate must beSome[TaxRate]
    product.taxRate.get.id must be_==(taxRateId)

  }
}
