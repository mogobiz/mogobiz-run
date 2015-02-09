package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._


import scala.concurrent.ExecutionContext
import scala.util.Try

class FacetService (storeCode: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
  import akka.pattern.ask
  import akka.util.Timeout

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {

    pathPrefix("facets") {
      getFacets
    }
  }

  import shapeless._

  lazy val getFacets = pathEnd{
    get {
      val facetParam = parameters('priceInterval.as[Long] ::
        'xtype.? ::
        'name.? ::
        'code.? ::
        'categoryPath.? ::
        'brandId.? ::
        'tags.? ::
        'notations.? ::
        'priceRange.? ::
        'creationDateMin.? ::
        'featured.?.as[Option[Boolean]] ::
        'lang ? "_all" ::
        'promotionId.? ::
        'hasPromotion.?.as[Option[Boolean]] ::
        'property.? ::
        'features.? ::
        'variations.? ::
        'brandName.? ::
        'categoryName.? ::
        'multiCategory.?.as[Option[Boolean]] ::
        'multiBrand.?.as[Option[Boolean]] ::
        'multiTag.?.as[Option[Boolean]] ::
        'multiFeatures.?.as[Option[Boolean]] ::
        'multiVariations.?.as[Option[Boolean]] ::
        'multiNotation.?.as[Option[Boolean]] ::
        'multiPrices.?.as[Option[Boolean]] ::
        HNil
      )

      facetParam.happly {
        case (priceInterval :: xtype :: name :: code :: categoryPath :: brandId :: tags :: notations :: priceRange ::
          creationDateMin :: featured :: lang :: promotionId :: hasPromotion :: property :: features :: variations ::
          brandName :: categoryName :: multiCategory :: multiBrand :: multiTag :: multiFeatures :: multiVariations ::
          multiNotation :: multiPrices :: HNil) =>

          val param = new FacetRequest(priceInterval, xtype, name, code, categoryPath, brandId, tags, notations, priceRange,
            creationDateMin, featured, lang, promotionId, hasPromotion, property, features, variations,
            brandName, categoryName, multiCategory, multiBrand, multiTag, multiFeatures, multiVariations,
            multiNotation, multiPrices)

          handleCall(facetHandler.getProductCriteria(storeCode, param),(json:JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
