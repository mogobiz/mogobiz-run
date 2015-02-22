package com.mogobiz.run

import akka.stream.scaladsl._

import scala.collection.mutable.ListBuffer

/**
 *
 * Created by smanciot on 22/02/15.
 */
package object learning {

  val combinationsFlow = Flow(){implicit b =>
    import FlowGraphImplicits._

    val undefinedSource = UndefinedSource[Seq[Long]]
    val undefinedSink = UndefinedSink[List[Seq[Long]]]

    undefinedSource ~> Flow[Seq[Long]].map[List[Seq[Long]]](s => {
      val combinations : ListBuffer[Seq[Long]] = ListBuffer.empty
      1 to s.length foreach {i => combinations ++= s.combinations(i)/*.map(_.sorted)*/.toList}
      combinations.toList
    }) ~> undefinedSink

    (undefinedSource, undefinedSink)
  }

  val sumSink = FoldSink[Int, Int](0)(_ + _)

}
