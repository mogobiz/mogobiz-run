package com.mogobiz.run.services

import java.util.{Date, UUID}

import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.RequestParameters.{CommentPutRequest, CommentRequest,NoteCommentRequest}
import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 30/06/2015.
 */
class ProductServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new ProductService() with DefaultCompleteMocked).route

  " product route " should {

    val rootPath = "/store/" + STORE + "/products"
    " be successful when getting a product detail " in {
      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when getting a product comments " in {
      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId + "/comments"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when creating a product comment " in {

      import Json4sProtocol._

      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId + "/comments"
      val comment = CommentRequest(5, "comment title", "comment body", None)
      Post(path, comment) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when updating a product comment " in {

      import Json4sProtocol._

      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val commentId = 4567
      val path = rootPath + "/" + productId + "/comments/"+commentId
      val comment = CommentPutRequest(Some("title"), Some("body"), Some(5))
      Put(path, comment) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when updating a product comment notation" in {

      import Json4sProtocol._

      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val commentId = 4567
      val path = rootPath + "/" + productId + "/comments/"+commentId
      val note = NoteCommentRequest(5)
      Post(path, note) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when deleting a product comment " in {

      import Json4sProtocol._

      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val commentId = 4567
      val path = rootPath + "/" + productId + "/comments/"+commentId
      Delete(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }


    " be successful when finding a product with default parameters " in {
      val path = rootPath + "/find?query=somethingtosearch"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when comparing products with default parameters " in {
      val path = rootPath + "/compare?ids=123,456"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when getting products by notation with default parameters " in {
      val path = rootPath + "/notation"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when getting a product suggestions " in {
      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId + "/suggestions"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when getting a product dates " in {
      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId + "/dates"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when getting a product times " in {
      //val productUuid = UUID.randomUUID().toString
      val productId = 1234
      val path = rootPath + "/" + productId + "/times"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

  }


  " history route " should {

    val rootPath = "/store/" + STORE + "/history"
    " respond when ask for history " in {
      Get(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }
  }

}