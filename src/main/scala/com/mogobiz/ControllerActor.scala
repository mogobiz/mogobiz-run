package com.mogobiz

import akka.actor.Actor
import org.json4s._
import spray.httpx.Json4sSupport
import com.mogobiz.cart.AddToCartCommand
import org.joda.time.DateTime
import com.mogobiz.cart.AddToCartCommand
import java.util.TimeZone
import org.joda.time.format.ISODateTimeFormat

/**
 * Created by dach on 18/02/2014.
 */
class ControllerActor extends Actor with StoreService {

  def actorRefFactory = context

  def receive = runRoute(storeRoutesWithCookie)

}

object Json4sProtocol extends Json4sSupport {

  implicit def json4sFormats: Formats = StoreDefaultFormats

  object StoreDefaultFormats extends DefaultFormats {
    override val customSerializers: List[Serializer[_]] = List(new JodaDateTimeSerialize())
  }

  class JodaDateTimeSerialize extends CustomSerializer[DateTime](format => (
    // deserialisation
    { case x: JString => ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(x.values) },
    // serialisation
    { case x: DateTime => JString(ISODateTimeFormat.dateOptionalTimeParser().print(x)) }
    ))
}