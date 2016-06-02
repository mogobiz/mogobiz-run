package com.mogobiz.run.model

import com.mogobiz.run.model.Mogobiz.DeliveryStatus._

case class WebHookData(shipmentId: String, newDeliveryStatus: DeliveryStatus)
