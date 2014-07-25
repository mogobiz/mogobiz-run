package com.mogobiz

import com.mogobiz.actors.{BootedMogobizSystem, MogobizActors}


object Cli extends App with MogobizActors with BootedMogobizSystem
