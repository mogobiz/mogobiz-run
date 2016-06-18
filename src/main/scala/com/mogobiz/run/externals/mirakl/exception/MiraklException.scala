package com.mogobiz.run.externals.mirakl.exception

abstract class MiraklException(msg: String) extends Exception

class MiraklCreateOrderException(msg: String) extends MiraklException(msg)

