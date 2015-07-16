/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import com.mogobiz.run.config.DefaultComplete
import spray.routing.{Directives, Route}

import scala.util.Try

/**
 * Created by Christophe on 16/06/2015.
 */
trait DefaultCompleteMocked extends DefaultComplete {
  this : Directives =>
  override def handleCall[T](call: => T, handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    println("overriden handleCall")
    complete("true")
  }

  override def handleComplete[T](call: Try[Try[T]], handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    println("overriden handleComplete")
    complete("true")
  }
}
