package com.mogobiz

import akka.actor.Actor
import org.json4s._
import spray.httpx.Json4sSupport
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.mogobiz.cart.CartItemVO
import com.mogobiz.cart.ProductType
import com.mogobiz.cart.ProductType.ProductType
import com.mogobiz.cart.ProductCalendar
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import com.mogobiz.cart.WeightUnit
import com.mogobiz.cart.WeightUnit.WeightUnit
import com.mogobiz.cart.LinearUnit
import com.mogobiz.cart.LinearUnit.LinearUnit
import com.mogobiz.cart.ReductionRuleType
import com.mogobiz.cart.ReductionRuleType.ReductionRuleType
import com.mogobiz.cart.TransactionStatus
import com.mogobiz.cart.TransactionStatus.TransactionStatus

/**
 * Created by dach on 18/02/2014.
 */
class ControllerActor extends Actor with StoreService {

  def actorRefFactory = context

  def receive = runRoute(storeRoutesWithCookie)

}

object Json4sProtocol extends Json4sSupport {

  class JodaDateTimeSerializer extends CustomSerializer[DateTime](format => (
    // deserialisation
    { case x: JString => ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(x.values) },
    // serialisation
    { case x: DateTime => JString(ISODateTimeFormat.dateOptionalTimeParser().print(x)) }
    ))

  class ProductTypeSerializer extends CustomSerializer[ProductType](format => (
    // deserialisation
    { case x: JString =>
      ProductType.valueOf(x.values)
    },
    // serialisation
    { case x: ProductType =>
      JString(x.toString)
    }
    ))

  class ProductCalendarSerializer extends CustomSerializer[ProductCalendar](format => (
    // deserialisation
    { case x: JString =>
      ProductCalendar.valueOf(x.values)
    },
    // serialisation
    { case x: ProductCalendar =>
      JString(x.toString)
    }
    ))

  class WeightUnitSerializer extends CustomSerializer[WeightUnit](format => (
    // deserialisation
    { case x: JString =>
      WeightUnit(x.values)
    },
    // serialisation
    { case x: WeightUnit =>
      JString(x.toString)
    }
    ))

  class LinearUnitSerializer extends CustomSerializer[LinearUnit](format => (
    // deserialisation
    { case x: JString =>
      LinearUnit(x.values)
    },
    // serialisation
    { case x: LinearUnit =>
      JString(x.toString)
    }
    ))

  class ReductionRuleTypeSerializer extends CustomSerializer[ReductionRuleType](format => (
    // deserialisation
    { case x: JString =>
      ReductionRuleType(x.values)
    },
    // serialisation
    { case x: LinearUnit =>
      JString(x.toString)
    }
    ))

  class TransactionStatusSerializer extends CustomSerializer[TransactionStatus](format => (
    // deserialisation
    { case x: JString =>
      TransactionStatus.valueOf(x.values)
    },
    // serialisation
    { case x: LinearUnit =>
      JString(x.toString)
    }
    ))

  implicit def json4sFormats: Formats = DefaultFormats +
    new JodaDateTimeSerializer() +
    new ProductTypeSerializer() +
    new ProductCalendarSerializer() +
    new WeightUnitSerializer() +
    new LinearUnitSerializer() +
    new ReductionRuleTypeSerializer() +
  new TransactionStatusSerializer()
}