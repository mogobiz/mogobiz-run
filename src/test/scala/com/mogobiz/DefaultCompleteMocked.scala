/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import com.mogobiz.run.config.DefaultComplete
import com.typesafe.scalalogging.LazyLogging
import spray.routing.{Directives, Route}

import scala.util.Try

/**
 */
trait DefaultCompleteMocked extends DefaultComplete with LazyLogging {
  this : Directives =>
  override def handleCall[T](call: => T, handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    logger.debug("overriden handleCall")
    complete("true")
  }

  override def handleComplete[T](call: Try[Try[T]], handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    logger.debug("overriden handleComplete")
    complete("true")
  }
}
