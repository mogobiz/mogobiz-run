package com.mogobiz.run.handlers

import java.util.{Calendar, UUID}

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.boot.DBInitializer
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 12/11/2014.
 */
class UuidHandlerSpec extends MogobizRouteTest {

  "UuidHandler" should {

    //node.client().admin().indices().prepareRefresh().execute().actionGet()

    DBInitializer()

    "create UuidData and find created UuidData" in {
      val now = Calendar.getInstance().getTime
      val uuidData = UuidData(UUID.randomUUID().toString, UUID.randomUUID().toString, None, "Cart", "content", now, now, now)

      UuidDataDao.save(uuidData)

      val uuidDataOpt = UuidDataDao.findByUuidAndXtype(uuidData.dataUuid, uuidData.userUuid, uuidData.xtype)

      uuidDataOpt must beSome

      val uuidDataDB = uuidDataOpt.get
      uuidDataDB must not beNull
      //uuidDataDB.uuid must be_==(uuidData.uuid)
    }
  }
}
