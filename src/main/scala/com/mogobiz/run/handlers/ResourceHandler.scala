package com.mogobiz.run.handlers

import com.mogobiz.run.config.Settings
import Settings._
import com.mogobiz.utils.ImageUtils
import com.mogobiz.utils.ImageUtils._
import java.io.File

class ResourceHandler {

  def queryResource(storeCode: String, resourceId:String, size:Option[String]): Option[String] = {
    val file = new File(s"$ResourcesRootPath/resources/$storeCode/image/$resourceId")
    if(file.exists()){
      size match {
        case Some(s) =>
          Some(ImageUtils.getFile(file, imageSizes.get(s.toUpperCase), create = true).getAbsolutePath)
        case _ =>
          Some(file.getAbsolutePath)
      }
    }
    else{
      None
    }
  }

}
