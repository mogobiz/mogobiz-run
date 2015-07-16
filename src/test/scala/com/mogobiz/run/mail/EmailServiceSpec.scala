/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.mail

import com.mogobiz.run.config.Settings
import com.mogobiz.run.utils.MailTemplateUtils
import com.mogobiz.system.BootedMogobizSystem
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._

/**
 *
 * Created by Christophe on 27/08/2014.
 */
class EmailServiceSpec extends Specification with NoTimeConversions with BootedMogobizSystem {

  val emailService = EmailService(system, "emailService")

  "should send email" in {

    val smtpConfig = SmtpConfig(
      ssl = Settings.Mail.Smtp.IsSSLEnabled,
      port = Settings.Mail.Smtp.Port,
      host = Settings.Mail.Smtp.Host,
      user = Settings.Mail.Smtp.Username,
      password = Settings.Mail.Smtp.Password
    )

    val qrcodeBaseUrl : String = Settings.accessUrl



      val msg = EmailMessage(
        subject = "Your ticket : "+ "{eventName}",
        recipient = "christophe.galant@gmail.com",
        from = Settings.Mail.defaultFrom,
        html = Some(MailTemplateUtils.ticket(
          "emailData(eventName).asInstanceOf[String]",
          "emailData(startDate).asInstanceOf[Option[DateTime]].getOrElse()",
          "emailData(stopDate).asInstanceOf[Option[DateTime]].getOrElse()",
          "emailData(location).asInstanceOf[String]",
          "emailData(price).asInstanceOf[Long].toString",
          "emailData(type).asInstanceOf[Option[String]].getOrElse()",
          qrcodeBaseUrl + "emailData(qrcode).asInstanceOf[Option[String]].getOrElse()"
        )),
        //            html = Some(MailTemplateUtils.ticket("","","","","","","")),

        smtpConfig = smtpConfig,
        retryOn = 30.seconds,
        deliveryAttempts=10
      )
      EmailService.send(msg)

    true must beTrue
  }
}
