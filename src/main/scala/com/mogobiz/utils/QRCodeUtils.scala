package com.mogobiz.utils

import java.io.OutputStream

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

import java.awt.Color;
import java.awt.image.BufferedImage
import javax.imageio.ImageIO;
/**
 * Created by Christophe on 09/07/2014.
 */
object QRCodeUtils {

  def createQrCode(outputStream:OutputStream, content:String , qrCodeSize:Int , imageFormat:String ) {
    try {
      // Create the ByteMatrix for the QR-Code that encodes the given String.

      val hintMap = new java.util.Hashtable[EncodeHintType, ErrorCorrectionLevel]();
      hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

      val qrCodeWriter = new QRCodeWriter();
      val byteMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hintMap);
      // Make the BufferedImage that are to hold the QRCode
      val matrixWidth = byteMatrix.getWidth();

      val image = new BufferedImage(matrixWidth, matrixWidth, BufferedImage.TYPE_INT_RGB);
      image.createGraphics();
      val graphics = image.getGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, matrixWidth, matrixWidth);

    // Paint and save the image using the ByteMatrix
    graphics.setColor(Color.BLACK);

    for (i <- 0 until matrixWidth)
    {
      for (j <- 0 until matrixWidth)
      {
        //if (byteMatrix.get(i, j) == 0)
        if (byteMatrix.get(i, j) == false)
        {
          graphics.fillRect(i, j, 1, 1);
        }
      }
    }

    ImageIO.write(image, imageFormat, outputStream);
    } catch {
      case t: Throwable => {
      t.printStackTrace()
      throw new Exception("Unable to create QR Code", t)
      }
    }
  }
}
