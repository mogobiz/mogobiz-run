package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing._

import com.mogobiz.run.config.HandlersConfig._

class BrandService(storeCode: String) extends Directives with DefaultComplete {

  import akka.util.Timeout

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("brands") {
      brands
    }
  }

  lazy val brands = pathEnd {
    get {
      parameters('hidden ? false, 'categoryPath.?, 'lang ? "_all", 'promotionId.?, 'size.as[Option[Int]]) {
        (hidden, categoryPath, lang, promotionId, size) =>
          handleCall(brandHandler.queryBrands(storeCode, hidden, categoryPath, lang, promotionId, size),
            (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
