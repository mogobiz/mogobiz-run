/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.config

import com.mogobiz.utils.EmailHandler.MailConfig
import com.typesafe.config.{ Config, ConfigFactory }
import scalikejdbc.config._

import collection.JavaConversions._
import scala.util.Try

trait MogobizTypesafeConfig extends TypesafeConfig {

  lazy val config: Config = ConfigFactory.load("mogobiz").withFallback(ConfigFactory.load("default-mogobiz"))
}

case class MogobizDBsWithEnv(envValue: String) extends DBs with TypesafeConfigReader with MogobizTypesafeConfig with EnvPrefix {

  override val env = Option(envValue)
}

object Settings {

  //val default = ConfigFactory.load()

  lazy val config: Config = ConfigFactory.load("mogobiz").withFallback(ConfigFactory.load("default-mogobiz"))

  val CookieTracking = config getString "mogobiz.cookie-tracking"

  val CakeClass = config getString "mogobiz.handlers.class"

  //  val ApplicationSecret = config getString "session.application.secret"
  //  val SessionFolder = new File(config getString "session.folder")
  //  val SessionCookieName = config getString "session.cookie.name"
  //  val SessionCookieMaxAge = config getLong "session.cookie.maxage"
  //  val RememberCookieName = config getString "session.remember.cookie.name"
  //  val RememberCookieMaxAge = config getLong "session.remember.cookie.maxage"

  val AccessUrl = config getString "mogobiz.accessUrl"
  val jahiaClearCacheUrl = config getString "mogobiz.jahiaClearCacheUrl"

  object Externals {
    val providers = config getString "mogobiz.externals.providers"
    def mirakl = providers.contains("mirakl")

    object Mirakl {
      val url = config getString "mogobiz.externals.mirakl.url"
      val frontApiKey = config getString "mogobiz.externals.mirakl.frontApiKey"
    }
  }

  object Cart {
    val Lifetime = config.getInt("mogobiz.cart.lifetime")

    object CleanJob {
      val Delay = config.getInt("mogobiz.cart.cleanJob.delay")
      val Interval = config.getInt("mogobiz.cart.cleanJob.interval")
      val QuerySize = config.getInt("mogobiz.cart.cleanJob.querySize")
    }

  }

  object VisitedProduct {
    val Max = config getLong "mogobiz.visited-product.max"
  }

  object clickatell {
    val user = ""
    val password = ""
    val apiId = ""
    val sender = ""
  }

  object Learning {
    val Rotate = config.getString("learning.index.rotate")
  }

  object Dashboard {
    val Rotate = config.getString("dashboard.index.rotate")
  }

  object Mail {

    object Smtp {
      val Host = config.getString("mail.smtp.host")
      val Port = config.getInt("mail.smtp.port")
      val SslPort = config.getInt("mail.smtp.sslport")
      val Username = config.getString("mail.smtp.username")
      val Password = config.getString("mail.smtp.password")
      val IsSSLEnabled = config.getBoolean("mail.smtp.ssl")
      val IsSSLCheckServerIdentity = config.getBoolean("mail.smtp.checkserveridentity")
      val IsStartTLSEnabled = config.getBoolean("mail.smtp.starttls")

      implicit val MailSettings = MailConfig(
        host = Host,
        port = Port,
        sslPort = SslPort,
        username = Username,
        password = Password,
        sslEnabled = IsSSLEnabled,
        sslCheckServerIdentity = IsSSLCheckServerIdentity,
        startTLSEnabled = IsStartTLSEnabled
      )
    }

    val DefaultFrom = config getString "mail.from"
    val MaxAge = (config getInt "mail.confirmation.maxage")
  }

  val Dialect = if (config hasPath "dialect") config getString "dialect" else "test"
  val NextVal = config getString s"$Dialect.db.default.nextval"

  MogobizDBsWithEnv(Dialect).setupAll()

  val EsHost = config getString "elastic.host"
  val EsHttpPort = config getInt "elastic.httpPort"
  val EsPort = config getInt "elastic.port"
  val EsIndex = config getString "elastic.index"
  val EsMLIndex = config getString "elastic.mlindex"
  val EsCluster = config getString "elastic.cluster"
  val EsFullUrl = s"$EsHost:$EsHttpPort"
  val EsDebug = config getBoolean "elastic.debug"
  val EsEmbedded = {
    val e = config getString "elastic.embedded"
    Try(getClass.getResource(e).getPath) getOrElse e
  }

  val ResourcesRootPath = config getString "resources.rootPath"
  val TemplatesPath = config.getString("templates.path")

  //  require(ApplicationSecret.nonEmpty, "application.secret must be non-empty")
  //  require(SessionCookieName.nonEmpty, "session.cookie.name must be non-empty")
  //  require(RememberCookieName.nonEmpty, "session.remember.cookie.name must be non-empty")
  //  require(Interface.nonEmpty, "interface must be non-empty")
  //  require(0 < Port && Port < 65536, "illegal port")
  require(ResourcesRootPath.nonEmpty, "resources.rootPath must be non-empty")

  val ResourcesTimeout = config.getLong("resources.timeout")
}

