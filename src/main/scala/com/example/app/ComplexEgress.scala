package com.example.app

import cloudflow.akkastream._
import cloudflow.akkastream.scaladsl._
import cloudflow.streamlets._
import cloudflow.streamlets.avro._

import scala.concurrent.{ ExecutionContext, Future }

class ComplexEgress extends AkkaStreamlet {
  val inlet = AvroInlet[Data]("in")
  val shape = StreamletShape.withInlets(inlet)

  // 3 simple targets for mocking
  val syncTransformer = new SyncTransformer {
    override def transform(s: String) =
      s.reverse
  }

  val asyncTransformer = new AsyncTransformer {
    import scala.concurrent.ExecutionContext.Implicits.global

    def transformAsync(s: String): Future[String] = Future {
      s.toUpperCase
    }
  }

  val transformFunc = (s: String) => s.toLowerCase

  override def createLogic = new RunnableGraphStreamletLogic() {
    def runnableGraph =
      sourceWithOffsetContext(inlet).via(flow).to(committableSink)

    val flow = FlowWithCommittableContext[Data]
      .map { data =>
        println(s"got ${data.id}")
        data.id
      }
      .map(syncTransformer.transform)
      .mapAsync(1)(asyncTransformer.transformAsync)
      .map(transformFunc)
  }
}

trait SyncTransformer {
  def transform(s: String): String
}

trait AsyncTransformer {
  def transformAsync(s: String): Future[String]
}
