/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.implicits

import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnit
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnit
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Mogobiz.ReductionRuleType.ReductionRuleType
import org.json4s._
import org.json4s.ext.JodaTimeSerializers
import spray.httpx.Json4sSupport

/**
 *
 * Created by smanciot on 06/09/14.
 */
object Json4sProtocol extends Json4sSupport {

  /*
  class JodaDateTimeSerializer extends CustomSerializer[DateTime](format => (
    // deserialisation
    { case x: JString => ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(x.values) },
    // serialisation
    { case x: DateTime => JString(ISODateTimeFormat.dateOptionalTimeParser().print(x)) }
    ))
    */

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

  implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all +
    //new JodaDateTimeSerializer() +
    new ProductTypeSerializer() +
    new ProductCalendarSerializer() +
    new WeightUnitSerializer() +
    new LinearUnitSerializer() +
    new ReductionRuleTypeSerializer() +
    new TransactionStatusSerializer()
}