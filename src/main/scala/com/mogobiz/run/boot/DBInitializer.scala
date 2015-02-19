package com.mogobiz.run.boot

import com.mogobiz.es.EsClient
import com.mogobiz.run.config.{HandlersConfig, Settings}
import com.mogobiz.run.es.Mapping
import com.mogobiz.run.handlers._
import com.sksamuel.elastic4s.ElasticDsl.{create => createES, _}
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException
import scalikejdbc._

/**
 * Created by yoannbaudy on 12/11/2014.
 */
object DBInitializer {
  def apply(fillWithFixtures: Boolean = false): Unit = {
    def createIndex(indexName: String) : Boolean = {
      try {
        EsClient.client.sync.execute(createES index indexName)
        println(s"Index $indexName has been created.")
        return true
      } catch {
        case e: RemoteTransportException if e.getCause().isInstanceOf[IndexAlreadyExistsException] =>
          println(s"Index $indexName was not created because it already exists.")
        case e: Throwable => e.printStackTrace()
      }
      return false
    }

    val backofficeIndexCreated = createIndex(Settings.backoffice.EsIndex)
    val cartIndexCreated = createIndex(Settings.cart.EsIndex)
    try {
      //if (backofficeIndexCreated) Mapping.set(List("BOCart"));
      if (cartIndexCreated) Mapping.set(List("StoreCart"));
      if (backofficeIndexCreated && fillWithFixtures) fillDB()
    }
    catch {
      case e: Throwable => e.printStackTrace()
    }
  }

  /**
   * Créer les ventes de tests.
   * ATTENTION : Fonctionne uniquement sur la base de test (fichier acmesport.sql du projet selenium)
   * et si l'index acmesport est présent dans ES
   */
  private def fillDB(): Unit = {
    val storeCode = "acmesport"
    val companyId = 31260
    DB localTx { implicit session =>
      sql"""INSERT INTO b_o_cart (id, buyer, company_fk, currency_code, currency_rate, xdate, date_created, last_updated, price, status, transaction_uuid, uuid) VALUES (217643, 'client@merchant.com', $companyId, 'EUR', 0.01, '2015-02-19 14:02:55.165', '2015-02-19 14:02:55.165', '2015-02-19 14:02:55.165', 2160, 'PENDING', NULL, 'e6d5a3ea-07c0-4770-bc13-1be349ac1f43');""".update.apply()
      sql"""INSERT INTO b_o_cart (id, buyer, company_fk, currency_code, currency_rate, xdate, date_created, last_updated, price, status, transaction_uuid, uuid) VALUES (217647, 'client@merchant.com', $companyId, 'EUR', 0.01, '2015-02-19 14:04:01.645', '2015-02-19 14:04:01.645', '2015-02-19 14:04:19.28', 2160, 'COMPLETE', '4c7a5788-0079-4781-b823-047cbef84198', '6faede3a-744e-426f-b7a3-e79ef4228293');""".update.apply()
      sql"""INSERT INTO b_o_cart (id, buyer, company_fk, currency_code, currency_rate, xdate, date_created, last_updated, price, status, transaction_uuid, uuid) VALUES (217652, 'client@merchant.com', $companyId, 'EUR', 0.01, '2015-02-19 14:05:17.567', '2015-02-19 14:05:17.567', '2015-02-19 14:05:31.961', 1680, 'COMPLETE', 'f9f71371-17f3-4dcd-bf8f-5d313470ccdf', '3429ca0e-0e8e-4749-99c2-0822d91b4b3a');""".update.apply()
      sql"""INSERT INTO b_o_cart (id, buyer, company_fk, currency_code, currency_rate, xdate, date_created, last_updated, price, status, transaction_uuid, uuid) VALUES (217657, 'client@merchant.com', $companyId, 'EUR', 0.01, '2015-02-19 14:06:08.872', '2015-02-19 14:06:08.872', '2015-02-19 14:06:23.111', 1188000, 'COMPLETE', '931eedc2-a4cd-431f-ba9c-aba4ed68806c', 'c133b2a8-682d-4a4e-b349-13e3ef7c6f57');""".update.apply()

      sql"""INSERT INTO b_o_delivery (id, b_o_cart_fk, date_created, extra, last_updated, status, tracking, uuid) VALUES (217645, 217643, '2015-02-19 14:02:55.197', '{"road":"road","road2":"road2","city":"Paris","zipCode":"75000","extra":"extra","civility":"MR","firstName":"Active Client 1","lastName":"Active TEST","telephone":{"phone":"+33123456789","lphone":"0123456789","isoCode":"FR","pinCode3":"000","status":"ACTIVE"},"country":"FR","admin1":"FR.A8","admin2":"FR.A8.75"}', '2015-02-19 14:02:55.197', 'NOT_STARTED', NULL, '069fd31c-ddd3-42c4-a110-38e2930e7be9');""".update.apply()
      sql"""INSERT INTO b_o_delivery (id, b_o_cart_fk, date_created, extra, last_updated, status, tracking, uuid) VALUES (217649, 217647, '2015-02-19 14:04:01.654', '{"road":"road","road2":"road2","city":"Paris","zipCode":"75000","extra":"extra","civility":"MR","firstName":"Active Client 1","lastName":"Active TEST","telephone":{"phone":"+33123456789","lphone":"0123456789","isoCode":"FR","pinCode3":"000","status":"ACTIVE"},"country":"FR","admin1":"FR.A8","admin2":"FR.A8.75"}', '2015-02-19 14:04:01.654', 'NOT_STARTED', NULL, '8096f126-090a-42f1-992f-359d92bd2a6a');""".update.apply()
      sql"""INSERT INTO b_o_delivery (id, b_o_cart_fk, date_created, extra, last_updated, status, tracking, uuid) VALUES (217654, 217652, '2015-02-19 14:05:17.574', '{"road":"road","road2":"road2","city":"Paris","zipCode":"75000","extra":"extra","civility":"MR","firstName":"Active Client 1","lastName":"Active TEST","telephone":{"phone":"+33123456789","lphone":"0123456789","isoCode":"FR","pinCode3":"000","status":"ACTIVE"},"country":"FR","admin1":"FR.A8","admin2":"FR.A8.75"}', '2015-02-19 14:05:17.574', 'NOT_STARTED', NULL, 'b60db270-a16f-450e-b037-aae07759ed51');""".update.apply()
      sql"""INSERT INTO b_o_delivery (id, b_o_cart_fk, date_created, extra, last_updated, status, tracking, uuid) VALUES (217660, 217657, '2015-02-19 14:06:09.066', '{"road":"road","road2":"road2","city":"Paris","zipCode":"75000","extra":"extra","civility":"MR","firstName":"Active Client 1","lastName":"Active TEST","telephone":{"phone":"+33123456789","lphone":"0123456789","isoCode":"FR","pinCode3":"000","status":"ACTIVE"},"country":"FR","admin1":"FR.A8","admin2":"FR.A8.75"}', '2015-02-19 14:06:09.066', 'NOT_STARTED', NULL, '896629d2-8b5d-41b8-a791-1dcf45bde5dd');""".update.apply()

      sql"""INSERT INTO b_o_cart_item (id, b_o_cart_fk, code, date_created, end_date, end_price, hidden, last_updated, price, quantity, start_date, tax, total_end_price, total_price, uuid, ticket_type_fk, b_o_delivery_fk) VALUES (217646, 217643, 'SALE_217643_217644', '2015-02-19 14:02:55.218', NULL, 2160, false, '2015-02-19 14:02:55.218', 1800, 1, NULL, 20, 2160, 1800, '75bc871b-045c-41d1-b4d0-ee22ed72b700', 214957, 217645);""".update.apply()
      sql"""INSERT INTO b_o_cart_item (id, b_o_cart_fk, code, date_created, end_date, end_price, hidden, last_updated, price, quantity, start_date, tax, total_end_price, total_price, uuid, ticket_type_fk, b_o_delivery_fk) VALUES (217650, 217647, 'SALE_217647_217648', '2015-02-19 14:04:01.656', NULL, 2160, false, '2015-02-19 14:04:01.656', 1800, 1, NULL, 20, 2160, 1800, '93e823c8-6b72-4142-9c87-2308d45e355d', 214957, 217649);""".update.apply()
      sql"""INSERT INTO b_o_cart_item (id, b_o_cart_fk, code, date_created, end_date, end_price, hidden, last_updated, price, quantity, start_date, tax, total_end_price, total_price, uuid, ticket_type_fk, b_o_delivery_fk) VALUES (217655, 217652, 'SALE_217652_217653', '2015-02-19 14:05:17.576', NULL, 1680, false, '2015-02-19 14:05:17.576', 1400, 1, NULL, 20, 1680, 1400, '06ea872a-2011-4d76-b2b7-5c262cb438a5', 214942, 217654);""".update.apply()
      sql"""INSERT INTO b_o_cart_item (id, b_o_cart_fk, code, date_created, end_date, end_price, hidden, last_updated, price, quantity, start_date, tax, total_end_price, total_price, uuid, ticket_type_fk, b_o_delivery_fk) VALUES (217661, 217657, 'SALE_217657_217658', '2015-02-19 14:06:09.069', NULL, 1188000, false, '2015-02-19 14:06:09.069', 990000, 1, NULL, 20, 1188000, 990000, 'da843b20-f187-4afa-b8a3-9c5deeac1041', 214707, 217660);""".update.apply()

      sql"""INSERT INTO b_o_product (id, acquittement, date_created, last_updated, price, principal, product_fk, uuid) VALUES (217644, false, '2015-02-19 14:02:55.183', '2015-02-19 14:02:55.183', 2160, true, 32689, '98200af2-5606-4227-9522-8575817f33f9');""".update.apply()
      sql"""INSERT INTO b_o_product (id, acquittement, date_created, last_updated, price, principal, product_fk, uuid) VALUES (217648, false, '2015-02-19 14:04:01.651', '2015-02-19 14:04:01.651', 2160, true, 32689, '67050e06-8c5b-4e35-a5eb-81e39b0d260b');""".update.apply()
      sql"""INSERT INTO b_o_product (id, acquittement, date_created, last_updated, price, principal, product_fk, uuid) VALUES (217653, false, '2015-02-19 14:05:17.572', '2015-02-19 14:05:17.572', 1680, true, 32709, '1a2c9c39-2175-4e32-8b09-a458a556f9c3');""".update.apply()
      sql"""INSERT INTO b_o_product (id, acquittement, date_created, last_updated, price, principal, product_fk, uuid) VALUES (217658, false, '2015-02-19 14:06:08.877', '2015-02-19 14:06:08.877', 1188000, true, 31938, '70d8497d-f740-4276-9264-5c8988db04af');""".update.apply()

      sql"""INSERT INTO b_o_cart_item_b_o_product (b_o_products_fk, boproduct_id) VALUES (217646, 217644);""".update.apply()
      sql"""INSERT INTO b_o_cart_item_b_o_product (b_o_products_fk, boproduct_id) VALUES (217650, 217648);""".update.apply()
      sql"""INSERT INTO b_o_cart_item_b_o_product (b_o_products_fk, boproduct_id) VALUES (217655, 217653);""".update.apply()
      sql"""INSERT INTO b_o_cart_item_b_o_product (b_o_products_fk, boproduct_id) VALUES (217661, 217658);""".update.apply()

      sql"""INSERT INTO b_o_ticket_type (id, age, b_o_product_fk, birthdate, date_created, email, end_date, firstname, last_updated, lastname, phone, price, qrcode, qrcode_content, quantity, short_code, start_date, ticket_type, uuid) VALUES (217659, 15, 217658, '2000-01-01 01:00:00', '2015-02-19 14:06:09.061', 'client@merchant.com', NULL, 'TEST', '2015-02-19 14:06:09.061', 'Client 1', '0123456789', 1188000, '', '', 1, 'P217658T217659', NULL, 'VIP Seat', '312f5f0f-f6d4-4d8e-9f3d-dae78eabd587');""".update.apply()

    }
    DB localTx { implicit session =>
      HandlersConfig.cartHandler.exportBOCartIntoES(storeCode, BOCartDao.load("e6d5a3ea-07c0-4770-bc13-1be349ac1f43").get)
      HandlersConfig.cartHandler.exportBOCartIntoES(storeCode, BOCartDao.load("6faede3a-744e-426f-b7a3-e79ef4228293").get)
      HandlersConfig.cartHandler.exportBOCartIntoES(storeCode, BOCartDao.load("3429ca0e-0e8e-4749-99c2-0822d91b4b3a").get)
      HandlersConfig.cartHandler.exportBOCartIntoES(storeCode, BOCartDao.load("c133b2a8-682d-4a4e-b349-13e3ef7c6f57").get)
    }
  }
}
