/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Calendar

import com.mogobiz.run.config.Settings
import Settings._
import com.mogobiz.utils.{HashTools, ImageUtils}
import com.mogobiz.utils.ImageUtils._
import java.io.{ FileOutputStream, File }

import com.mogobiz.es.EsClient._
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.bytes.ChannelBufferBytesReference

class ResourceHandler {

  def queryResource(storeCode: String, resourceId: String, size: Option[String]): Option[String] = {
    val dir = s"$ResourcesRootPath/resources/$storeCode/image"
    val path = s"$dir/$resourceId"
    val file = new File(path)
    if (!file.exists()) {
      loadRaw(get id resourceId from storeCode -> "resource" fields "content" fetchSourceContext true) match {
        case Some(response) =>
          if (response.getFields.containsKey("content")) {
            val content = response.getField("content").getValue.asInstanceOf[ChannelBufferBytesReference]
            new File(dir).mkdirs()
            content.writeTo(new FileOutputStream(file))
          }
        case _ =>
      }
    }
    else if((Calendar.getInstance().getTimeInMillis - file.lastModified()) > ResourcesTimeout){
      loadRaw(get id resourceId from storeCode -> "resource" fields ("content", "md5") fetchSourceContext true) match {
        case Some(response) =>
          if (response.getFields.containsKey("md5")) {
            val md5 = response.getField("md5").getValue.asInstanceOf[String]
            if(!HashTools.generateFileMD5(file).getOrElse("unknown").equals(md5) && response.getFields.containsKey("content")){
              val content = response.getField("content").getValue.asInstanceOf[ChannelBufferBytesReference]
              content.writeTo(new FileOutputStream(file))
            }
          }
        case _ => file.delete()
      }
    }
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
