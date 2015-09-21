/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.{File, FileOutputStream}

import com.mogobiz.es.EsClient._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.config.Settings._
import com.mogobiz.utils.ImageUtils
import com.mogobiz.utils.ImageUtils._
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.bytes.ChannelBufferBytesReference

class LogoHandler {

  def queryLogo(storeCode: String, brandId:String, size:Option[String]): Option[String] = {
    val dir = s"$ResourcesRootPath/logo/$storeCode/image"
    val path = s"$dir/$brandId"
    val file = new File(path)
    if(!file.exists()){
      loadRaw(get id brandId from storeCode -> "brand" fields "content" fetchSourceContext true) match {
        case Some(response) =>
          if(response.getFields.containsKey("content")){
            val content = response.getField("content").getValue.asInstanceOf[ChannelBufferBytesReference]
            new File(dir).mkdirs()
            content.writeTo(new FileOutputStream(file))
          }
        case _ =>
      }
    }
    if(file.exists()){
      size match {
        case Some(s) =>
          Some(ImageUtils.getFile(file, imageSizes.get(s.toUpperCase), create = true).getAbsolutePath)
        case _ =>
          Some(path)
      }
    }
    else{
      None
    }
  }

}