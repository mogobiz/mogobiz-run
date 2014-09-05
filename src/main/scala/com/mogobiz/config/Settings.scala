package com.mogobiz.config

import com.typesafe.config.ConfigFactory
import java.io.File
import scalikejdbc.config._

object Settings {

  val config = ConfigFactory.load()

  val Interface    = config getString  "spray.can.server.interface"
  val Port         = config getInt     "spray.can.server.port"

  val ApplicationSecret = config getString "session.application.secret"
  val SessionFolder = new File(config getString "session.folder")
  val SessionCookieName = config getString "session.cookie.name"
  val SessionCookieMaxAge = config getLong "session.cookie.maxage"
  val RememberCookieName = config getString "session.remember.cookie.name"
  val RememberCookieMaxAge = config getLong "session.remember.cookie.maxage"

  object clickatell {
    val user = ""
    val password = ""
    val apiId = ""
    val sender = ""
  }

  DBs.setupAll()
  //DBsWithEnv("development").setupAll()

  object DB {
    val EsHost = "localhost"
    val EsHttpPort = 9200
    val EsPort = 9300
    val Index = "mogopay"
    val EsCluster = "elasticsearch"
    val EsFullUrl = EsHost + ":" + EsHttpPort
  }



  require(ApplicationSecret.nonEmpty, "application.secret must be non-empty")
  require(SessionCookieName.nonEmpty, "session.cookie.name must be non-empty")
  require(RememberCookieName.nonEmpty, "session.remember.cookie.name must be non-empty")
  //  require(Interface.nonEmpty, "interface must be non-empty")
  //  require(0 < Port && Port < 65536, "illegal port")
}

