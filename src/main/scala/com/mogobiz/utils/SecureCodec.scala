package com.mogobiz.utils

import java.security.NoSuchAlgorithmException
import javax.crypto.{NoSuchPaddingException, Cipher}
import javax.crypto.spec.SecretKeySpec

/**
 * Created by Christophe on 09/07/2014.
 */
object SecureCodec {
  def encrypt(text: String, seed: String): String = {
    var keyspec: SecretKeySpec = null
    var cipher: Cipher = null
    keyspec = new SecretKeySpec(hexToBytes(seed), "AES")
    try {
      cipher = Cipher.getInstance("AES")
      cipher.init(Cipher.ENCRYPT_MODE, keyspec)
      if (text == null || text.length == 0) throw new Exception("Empty string")
      var encrypted: Array[Byte] = null
      encrypted = cipher.doFinal(text.getBytes)
      return bytesToHex(encrypted)
    }
    catch {
      case e: NoSuchAlgorithmException => {
        e.printStackTrace
      }
      case e: NoSuchPaddingException => {
        e.printStackTrace
      }
    }
    return null
  }

  def bytesToHex(data: Array[Byte]): String = {
    if (data == null) {
      return null
    }

    val len: Int = data.length
    var str: String = ""
    for(i <- 0 until len)
    {
      if ((data(i) & 0xFF) < 16) str = str + "0" + java.lang.Integer.toHexString(data(i) & 0xFF)
      else str = str + java.lang.Integer.toHexString(data(i) & 0xFF)
    }

    return str
  }

  def hexToBytes(str: String): Array[Byte] = {
    if (str == null) {
      return null
    }
    else if (str.length < 2) {
      return null
    }
    else {
      val len: Int = str.length / 2


      val buffer = new Array[Byte](len)
      for(i <- 0 until len)
      {
        buffer(i) = Integer.parseInt(str.substring(i * 2, i * 2 + 2), 16).asInstanceOf[Byte]
      }

      return buffer
    }
  }
}
