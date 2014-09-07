package com.mogobiz.session

import javax.crypto._
import javax.crypto.spec.SecretKeySpec

import com.mogobiz.config.Settings


trait Crypto {


  def sign(message: String, key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message.getBytes("utf-8")))
  }

  def sign(message: String): String =
    sign(message, Settings.ApplicationSecret.getBytes("utf-8"))

}


object Crypto extends Crypto
