package com.mogobiz.config

import java.io.File

import com.typesafe.config.ConfigFactory
import scalikejdbc.config._

import scala.util.Try

object Settings {

  val default = ConfigFactory.load()

  val config  = ConfigFactory.load("mogobiz").withFallback(default)

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

  object Emailing {

    /* from iper
    grails.mail.host = 'smtp.gmail.com'
    grails.mail.port = 465
    grails.mail.username = 'mogobiz@gmail.com'
    grails.mail.password = 'e-z12B24'
    grails.mail.props = ['mail.smtp.auth'                  : 'true',
                         'mail.smtp.socketFactory.port'    : '465',
                         'mail.smtp.socketFactory.class'   : 'javax.net.ssl.SSLSocketFactory',
                         'mail.smtp.socketFactory.fallback': 'false']
    grails.mail.default.from = 'mogobiz@gmail.com'

    // email confirmation
    emailConfirmation.from = 'mogobiz@gmail.com'
    // 24hr 1000 * 60 * 60 * 24
    emailConfirmation.maxAge = 86400000
     */

    object SMTP {
      val Hostname     = config getString "mail.smtp.hostname"
      val Port         = config getInt "mail.smtp.port"
      val Username     = config getString "mail.smtp.username"
      val Password     = config getString "mail.smtp.password"
      val IsSSLEnabled = config getBoolean "mail.smtp.sslEnabled"
    }

    val defaultFrom = config getString "mail.defaultFrom"
    val MaxAge      = (config getInt "mail.maxAgeInHours") * 3600
  }

  object MogobizAdmin {

    val QrCodeAccessUrl = config getString "mogobiz.admin.qrCodeAccessUrl"
  }

  DBs.setupAll()
  //DBsWithEnv("development").setupAll()

  val EsHost     = config getString "elastic.host"
  val EsHttpPort = config getInt "elastic.httpPort"
  val EsPort     = config getInt "elastic.port"
  val EsIndex    = config getString "elastic.index"
  val EsCluster  = config getString "elastic.cluster"
  val EsFullUrl  = s"$EsHost:$EsHttpPort"
  val EsDebug    = config getBoolean "elastic.debug"
  val EsEmbedded = {
    val e = config getString "elastic.embedded"
    Try(getClass.getResource(e).getPath) getOrElse e
  }

  require(ApplicationSecret.nonEmpty, "application.secret must be non-empty")
  require(SessionCookieName.nonEmpty, "session.cookie.name must be non-empty")
  require(RememberCookieName.nonEmpty, "session.remember.cookie.name must be non-empty")
  //  require(Interface.nonEmpty, "interface must be non-empty")
  //  require(0 < Port && Port < 65536, "illegal port")
}

