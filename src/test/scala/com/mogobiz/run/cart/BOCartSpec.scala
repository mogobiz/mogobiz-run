package com.mogobiz.run.cart

import com.mogobiz.run.cart.transaction.{TransactionStatus, BOCart}
import com.mogobiz.run.config.MogobizDBsWithEnv
import com.mogobiz.utils.GlobalUtil
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import scalikejdbc.DB
import scalikejdbc.config.DBsWithEnv


class BOCartSpec  extends Specification {

  MogobizDBsWithEnv("test").setupAll()

  "the BO DAO " should {

    val currencyCode = "EUR"
    val rate = 1f
    val companyId = 9

    "be able to insert data" in {
      val data = BOCart(
        id = 0,
        buyer = "christophe.galant@ebiznext.com",
        transactionUuid = GlobalUtil.newUUID,
        xdate = DateTime.now,
        price = 10000,
        status = TransactionStatus.PENDING,
        currencyCode = currencyCode,
        currencyRate = rate,
        companyFk = companyId
      )

      val res = DB localTx { implicit session =>
        BOCart.insertTo(data)
      }
      //println(res)
      res.uuid must be_==(data.uuid)
      res.transactionUuid must_== data.transactionUuid
      res.id must_!=(0)
    }
  }

}
