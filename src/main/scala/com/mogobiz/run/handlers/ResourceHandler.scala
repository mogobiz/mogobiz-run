/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.File

import com.mogobiz.run.config.Settings._
import com.mogobiz.utils.ImageUtils
import com.mogobiz.utils.ImageUtils._

class ResourceHandler {
  def queryResource(storeCode: String, resourceId: String, size: Option[String]): Option[String] = {
    val dir  = s"$ResourcesRootPath/resources/$storeCode/image"
    val path = s"$dir/$resourceId"
    val file = new File(path)
    if (file.exists()) {
      size match {
        case Some(s) =>
          Some(ImageUtils.getFile(file, imageSizes.get(s.toUpperCase), create = true).getAbsolutePath)
        case _ =>
          Some(path)
      }
    } else {
      None
    }
  }

}
