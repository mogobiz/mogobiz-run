/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.File

import com.mogobiz.run.config.Settings
import com.mogobiz.template.Mustache
import org.apache.commons.lang.LocaleUtils

/**
 */
class TemplateHandler {

  def getTemplate(storeCode: String, templateName: String, locale: Option[String]) = {
    def findExternalTemplate(templateName: String) = {
      val file = new File(new File(Settings.TemplatesPath, storeCode), s"$templateName.mustache")
      if (file.exists()) {
        val source = scala.io.Source.fromFile(file)
        val lines = source.mkString
        source.close()
        Some(lines)
      }
      else None
    }

    def defaultTemplate() = scala.io.Source.fromInputStream(getClass.getResourceAsStream(s"/template/$templateName.mustache")).mkString

    locale.map { l =>
      findExternalTemplate(s"${templateName}_$l") getOrElse {
        findExternalTemplate(s"${templateName}_${LocaleUtils.toLocale(l).getLanguage}") getOrElse {
          findExternalTemplate(templateName) getOrElse {
            defaultTemplate()
          }
        }
      }
    } getOrElse {
      findExternalTemplate(templateName) getOrElse {
        defaultTemplate()
      }
    }
  }

  def mustache(template: String, jsonString: String): (String, String) = {
    println(jsonString)
    val mailContent = Mustache(template, jsonString)
    val eol = mailContent.indexOf('\n')
    require(eol > 0, "No new line found in mustache file to distinguish subject from body")
    val subject = mailContent.substring(0, eol)
    val body = mailContent.substring(eol + 1)
    (subject, body)

  }
}
