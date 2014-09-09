package com.mogobiz.mail

import com.mogobiz.actors.BootedMogobizSystem
import com.mogobiz.config.Settings
import com.mogobiz.utils.MailTemplateUtils
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
      ssl = Settings.Emailing.SMTP.IsSSLEnabled,
      port = Settings.Emailing.SMTP.Port,
      host = Settings.Emailing.SMTP.Hostname,
      user = Settings.Emailing.SMTP.Username,
      password = Settings.Emailing.SMTP.Password
    )

    val qrcodeBaseUrl : String = Settings.MogobizAdmin.QrCodeAccessUrl



      val msg = EmailMessage(
        subject = "Your ticket : "+ "{eventName}",
        recipient = "christophe.galant@gmail.com",
        from = Settings.Emailing.defaultFrom,
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
