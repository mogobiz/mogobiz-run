/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.implicits

import com.mogobiz.run.model.Mogobiz.DeliveryStatus.DeliveryStatus
import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnit
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnit
import com.mogobiz.run.model.Mogobiz.{DeliveryStatus, _}
import com.mogobiz.run.model.Mogobiz.ReductionRuleType.ReductionRuleType
import com.mogobiz.run.model.Mogobiz.ReturnStatus.ReturnStatus
import com.mogobiz.run.model.Mogobiz.ReturnedItemStatus.ReturnedItemStatus
import org.json4s._
import org.json4s.ext.JodaTimeSerializers
import spray.httpx.Json4sSupport

/**
  *
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

  class ProductTypeSerializer
      extends CustomSerializer[ProductType](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                ProductType.withName(x.values)
            },
                // serialisation
                {
              case x: ProductType =>
                JString(x.toString)
            }
          ))

  class ProductCalendarSerializer
      extends CustomSerializer[ProductCalendar](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                ProductCalendar.withName(x.values)
            },
                // serialisation
                {
              case x: ProductCalendar =>
                JString(x.toString)
            }
          ))

  class WeightUnitSerializer
      extends CustomSerializer[WeightUnit](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                WeightUnit.withName(x.values)
            },
                // serialisation
                {
              case x: WeightUnit =>
                JString(x.toString)
            }
          ))

  class LinearUnitSerializer
      extends CustomSerializer[LinearUnit](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                LinearUnit.withName(x.values)
            },
                // serialisation
                {
              case x: LinearUnit =>
                JString(x.toString)
            }
          ))

  class ReductionRuleTypeSerializer
      extends CustomSerializer[ReductionRuleType](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                ReductionRuleType.withName(x.values)
            },
                // serialisation
                {
              case x: ReductionRuleType =>
                JString(x.toString)
            }
          ))

  class TransactionStatusSerializer
      extends CustomSerializer[TransactionStatus](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                TransactionStatus.withName(x.values)
            },
                // serialisation
                {
              case x: TransactionStatus =>
                JString(x.toString)
            }
          ))

  class DeliveryStatusSerializer
      extends CustomSerializer[DeliveryStatus](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                DeliveryStatus.withName(x.values)
            },
                // serialisation
                {
              case x: DeliveryStatus =>
                JString(x.toString)
            }
          ))

  class ReturnedItemStatusSerializer
      extends CustomSerializer[ReturnedItemStatus](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                ReturnedItemStatus.withName(x.values)
            },
                // serialisation
                {
              case x: ReturnedItemStatus =>
                JString(x.toString)
            }
          ))

  class ReturnStatusSerializer
      extends CustomSerializer[ReturnStatus](
          format =>
            (
                // deserialisation
                {
              case x: JString =>
                ReturnStatus.withName(x.values)
            },
                // serialisation
                {
              case x: ReturnStatus =>
                JString(x.toString)
            }
          ))

  implicit def json4sFormats: Formats =
    DefaultFormats ++ JodaTimeSerializers.all +
      new ProductTypeSerializer() +
      new ProductCalendarSerializer() +
      new WeightUnitSerializer() +
      new LinearUnitSerializer() +
      new ReductionRuleTypeSerializer() +
      new TransactionStatusSerializer() +
      new DeliveryStatusSerializer() +
      new ReturnedItemStatusSerializer() +
      new ReturnStatusSerializer()
}
