/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.activation.{URLDataSource, DataSource}
import javax.mail.util.ByteArrayDataSource

import com.mogobiz.run.config.Settings
import com.mogobiz.utils.QRCodeUtils
import org.apache.commons.mail.DataSourceResolver
import org.apache.commons.mail.resolver.DataSourceUrlResolver

/**
 * From https://gist.github.com/mariussoutier/3436111
 */
object EmailHandler {

  sealed abstract class MailType

  case object Plain extends MailType

  case object Rich extends MailType

  case object MultiPart extends MailType

  case class Mail(from: (String, String), // (email -> name)
                  to: Seq[String],
                  cc: Seq[String] = Seq.empty,
                  bcc: Seq[String] = Seq.empty,
                  subject: String,
                  message: String,
                  richMessage: Option[String] = None,
                  attachment: Option[(java.io.File)] = None)

  object Send {
    def to(mail: Mail) {
      import org.apache.commons.mail._

      val format =
        if (mail.attachment.isDefined) MultiPart
        else if (mail.richMessage.isDefined) Rich
        else Plain

      val commonsMail: Email = format match {
        case Plain => new SimpleEmail().setMsg(mail.message)
        case Rich => {
          val dataSourceUrlResolver = new Base64ImageDataSource()
          val email = new ImageHtmlEmail();
          email.setDataSourceResolver(dataSourceUrlResolver)
          email.setHtmlMsg(mail.richMessage.get)
        }
        case MultiPart => {
          val attachment = new EmailAttachment()
          attachment.setPath(mail.attachment.get.getAbsolutePath)
          attachment.setDisposition(EmailAttachment.ATTACHMENT)
          attachment.setName(mail.attachment.get.getName)
          new MultiPartEmail().attach(attachment).setMsg(mail.message)
        }
      }

      commonsMail.setHostName(Settings.Mail.Smtp.Host)
      commonsMail.setSmtpPort(Settings.Mail.Smtp.Port)
      commonsMail.setSslSmtpPort(Settings.Mail.Smtp.SslPort.toString)
      if (Settings.Mail.Smtp.Username.length > 0) {
        commonsMail.setAuthenticator(new DefaultAuthenticator(
          Settings.Mail.Smtp.Username,
          Settings.Mail.Smtp.Password))
      }
      commonsMail.setSSLOnConnect(Settings.Mail.Smtp.IsSSLEnabled)
      commonsMail.setSSLCheckServerIdentity(Settings.Mail.Smtp.IsSSLCheckServerIdentity)
      commonsMail.setStartTLSEnabled(Settings.Mail.Smtp.IsStartTLSEnabled)

      // Can't add these via fluent API because it produces exceptions
      mail.to foreach (commonsMail.addTo(_))
      mail.cc foreach (commonsMail.addCc(_))
      mail.bcc foreach (commonsMail.addBcc(_))

      try {
        commonsMail.
          setFrom(mail.from._1, mail.from._2).
          setSubject(mail.subject).
          send()
      }
      catch {
        case e: EmailException =>
          e.printStackTrace()
      }
    }
  }

}

class Base64ImageDataSource extends DataSourceResolver {
  override def resolve(s: String): DataSource = {
    val tabs = s.split("||");
    val output = new ByteArrayOutputStream()
    QRCodeUtils.createQrCode(output, tabs(2), 256, "png")
    val ds = new ByteArrayDataSource(output.toByteArray, "image/png")
    ds.setName(tabs(1))
    ds
  }

  override def resolve(s: String, b: Boolean): DataSource = {
    return resolve(s)
  }
}