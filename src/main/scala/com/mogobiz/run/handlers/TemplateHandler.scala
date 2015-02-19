package com.mogobiz.run.handlers

import java.io.File

import com.mogobiz.run.config.Settings
import com.mogobiz.template.Mustache

/**
 * Created by yoannbaudy on 28/11/2014.
 */
class TemplateHandler {

  def getTemplate(storeCode: String, templateName: String) = {
    val templateFile = new File(new File(Settings.TemplatesPath, storeCode), templateName)
    if (templateFile.exists()) {
      val source = scala.io.Source.fromFile(templateFile)
      val lines = source.mkString
      source.close()
      lines
    }
    else {
      scala.io.Source.fromInputStream(getClass.getResourceAsStream(s"/template/$templateName")).mkString
    }
  }

  def mustache(template: String, jsonString: String): (String, String) = {
    val mailContent = Mustache(template, jsonString)
    val eol = mailContent.indexOf('\n')
    require(eol > 0, "No new line found in mustache file to distinguish subject from body")
    val subject = mailContent.substring(0, eol)
    val body = mailContent.substring(eol + 1)
    (subject, body)

  }
}
