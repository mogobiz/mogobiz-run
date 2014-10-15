package com.mogobiz.utils

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 *
 * Created by smanciot on 15/10/14.
 */
object ImageUtils {

  def resizeImage(inputFile:File){
    val originalImage:BufferedImage = ImageIO.read(inputFile)
    var height:Int = originalImage.getHeight
    var width:Int = originalImage.getWidth
    val _type:Int = originalImage.getType match {
      case 0 => BufferedImage.TYPE_INT_ARGB
      case _ => originalImage.getType
    }
    for (size <- imageSizes.values) {
      val bufferedImage:BufferedImage = new BufferedImage(size.width, size.height, _type)
      val g:Graphics2D = bufferedImage.createGraphics()
      if (width > height) {
        height = (height * size.height) / width
        width = size.width
      }
      else {
        width = (width * size.width)/ height
        height = size.height
      }
      g.drawImage(originalImage, 0, 0, width, height, null)
      g.dispose()
      val outputFile:File = getFile(inputFile, Some(size), create = false)
      ImageIO.write(bufferedImage, "jpg", outputFile)
    }
  }

  def getFile(inputFile:File, size:Option[ImageSize], create:Boolean):File = {
    size match {
      case Some(s) =>
        val file : File = new File(s"${inputFile.getAbsolutePath}.${s.width}x${s.height}.jpg")
        if (!file.exists() && create) {
          resizeImage(inputFile)
        }
        file
      case _ => inputFile
    }
  }

  val SMALL = "SMALL"

  val ICON = "ICON"

  val imageSizes = Map[String, ImageSize](ICON -> Icon, SMALL -> Small)

  trait ImageSize {
    def width  : Int
    def height : Int
  }

  case object Icon extends ImageSize{
    val width  = 32
    val height = 32
  }

  case object Small extends ImageSize{
    val width  = 240
    val height = 240
  }

}
