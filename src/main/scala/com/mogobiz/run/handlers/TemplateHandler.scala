package com.mogobiz.run.handlers

import java.io.File

import com.mogobiz.run.config.Settings
import com.mogobiz.template.Mustache

/**
 * Created by yoannbaudy on 28/11/2014.
 */
class TemplateHandler {

  def getTemplate(storeCode: String, templateName: String) = {
    val templateFile = new File(new File(Settings.TemplatesPath, storeCode), "mail-cart.mustache")
    if (templateFile.exists()) {
      val source = scala.io.Source.fromFile(templateFile)
      val lines = source.mkString
      source.close()
      lines
    }
    else {
      scala.io.Source.fromInputStream(getClass.getResourceAsStream("/template/mail-cart.mustache")).mkString
    }
  }

  def mustache(template: String, jsonString: String): String = {
    Mustache(template, jsonString)
  }
}
