/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.config

object MogobizHandlers {
  val handlers: MogobizCake =
    if (Settings.CakeClass != null && Settings.CakeClass.trim.length > 0)
      Class.forName(Settings.CakeClass.trim).newInstance().asInstanceOf[MogobizCake]
    else
      new DefaultMogobizCake()
}
