package com.example.app

import akka.NotUsed

import scala.concurrent.duration._
import akka.actor.{ ActorRef, ActorSystem }
import akka.kafka.ConsumerMessage.Committable
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.testkit.{ TestKit, TestProbe }
import cloudflow.akkastream.scaladsl.RunnableGraphStreamletLogic
import cloudflow.akkastream.testkit.scaladsl.{ AkkaStreamletTestKit, Completed }
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpec }

import scala.concurrent.{ Await, ExecutionContext, Future }

class ComplexEgressSpec extends WordSpec with MustMatchers with ScalaFutures with BeforeAndAfterAll with MockFactory {
  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit val actorSystem: ActorSystem        = ActorSystem("test-system")
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(actorSystem)

  val testData = List(Data("sepp", 3.14d), Data("hat", 2.718d), Data("gelbe", -273.15d), Data("Eier", 299.792d))
  val expected = List("pi", "e", "absolute zero", "speed-o-light")

  "A ComplexEgress" should {
    "call synchronous mock method" in {
      val testEgress = new ComplexEgress with MockedSyncTransformer with ProbedSink
      (testEgress.syncTransformer.transform _).expects(testData(0).id).returning(expected(0)).once
      (testEgress.syncTransformer.transform _).expects(testData(1).id).returning(expected(1)).once
      (testEgress.syncTransformer.transform _).expects(testData(2).id).returning(expected(2)).once
      (testEgress.syncTransformer.transform _).expects(testData(3).id).returning(expected(3)).once

      val testKit = AkkaStreamletTestKit(actorSystem, materializer)
      val src     = Source(testData)
      val in      = testKit.inletFromSource(testEgress.inlet, src)

      val streamlet = testKit.run(testEgress, List(in), List.empty)
      testEgress.sinkProbe.fishForSpecificMessage(10 seconds) {
        case completed @ ProbedSink.Completed => completed
      }
      streamlet.stop()

//      //testKit.run(testEgress, in, () => {}) //FIXME - this would implicitly stop the streamlet execution, but mock validation ALWAYS fails (regardless of extra wait times) when using this
//      val streamlet = testKit.run(testEgress, List(in), List.empty)
//      Await.result(streamlet.ready, 3 seconds)
//
//      //FIXME - how to give the Streamlet adquate time to process all messages
//      //Await.result(streamlet.completed, 10 seconds) //FIXME - Streamlet never completes - why?
//
//      //FIXME - dirty hack to allow the Streamlet to do some actual processing! :(
//      Await.result(Future { Thread.sleep(1000) }, 2 seconds) // without this extra time, the mocks never get called and, hence, throw
    }

    "call asynchronous mock method" in {
      val testEgress = new ComplexEgress with MockedAsyncTransformer with ProbedSink
      (testEgress.asyncTransformer.transformAsync _).expects(testData(0).id.reverse).returning(Future(expected(0))).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(1).id.reverse).returning(Future(expected(1))).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(2).id.reverse).returning(Future(expected(2))).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(3).id.reverse).returning(Future(expected(0))).once

      val testKit = AkkaStreamletTestKit(actorSystem, materializer)
      val src     = Source(testData)
      val in      = testKit.inletFromSource(testEgress.inlet, src)

      val streamlet = testKit.run(testEgress, List(in), List.empty)
      testEgress.sinkProbe.fishForSpecificMessage(10 seconds) {
        case completed @ ProbedSink.Completed => completed
      }
      streamlet.stop()

//      //testKit.run(testEgress, in, () => {}) //FIXME - this would implicitly stop the streamlet execution, but mock validation ALWAYS fails (regardless of extra wait times) when using this
//      val streamlet = testKit.run(testEgress, List(in), List.empty)
//      Await.result(streamlet.ready, 3 seconds)
//
//      //FIXME - how to give the Streamlet adquate time to process all messages
//      //Await.result(streamlet.completed, 10 seconds) //FIXME - Streamlet never completes - why?
//
//      //FIXME - dirty hack to allow the Streamlet to do some actual processing! :(
//      Await.result(Future { Thread.sleep(1000) }, 2 seconds) // without this extra time, the mocks never get called and, hence, throw
    }

    "call function mock" in {
      val testEgress = new ComplexEgress with MockedTransformFunc with ProbedSink
      testEgress.transformFunc.expects(testData(0).id.reverse.toUpperCase).returning(expected(0)).once
      testEgress.transformFunc.expects(testData(1).id.reverse.toUpperCase).returning(expected(1)).once
      testEgress.transformFunc.expects(testData(2).id.reverse.toUpperCase).returning(expected(2)).once
      testEgress.transformFunc.expects(testData(3).id.reverse.toUpperCase).returning(expected(3)).once

      val testKit   = AkkaStreamletTestKit(actorSystem, materializer)
      val src       = Source(testData)
      val in        = testKit.inletFromSource(testEgress.inlet, src)
      val streamlet = testKit.run(testEgress, List(in), List.empty)

      testEgress.sinkProbe.fishForSpecificMessage(10 seconds) {
        case completed @ ProbedSink.Completed => completed
      }
      streamlet.stop()
    }

    "call probed function (insted of mocked)" in {
      val testEgress = new ComplexEgress with ProbedTransformFunc
      val testKit    = AkkaStreamletTestKit(actorSystem, materializer)
      val src        = Source(testData)
      val in         = testKit.inletFromSource(testEgress.inlet, src)
      val streamlet  = testKit.run(testEgress, List(in), List.empty)
      testEgress.probe.expectMsg(testData(0).id.reverse.toUpperCase)
      testEgress.probe.expectMsg(testData(1).id.reverse.toUpperCase)
      testEgress.probe.expectMsg(testData(2).id.reverse.toUpperCase)
      testEgress.probe.expectMsg(testData(3).id.reverse.toUpperCase)
      testEgress.probe.expectNoMessage()
      streamlet.stop()
      Await.ready(streamlet.completed, 10 seconds) //actually not required because not mocks are waiting for assertions in this test
    }
  }

  trait MockedSyncTransformer {
    self: ComplexEgress =>
    override val syncTransformer = mock[SyncTransformer]
  }

  trait MockedAsyncTransformer {
    self: ComplexEgress =>
    override val asyncTransformer = mock[AsyncTransformer]
  }

  trait MockedTransformFunc {
    self: ComplexEgress =>
    override val transformFunc = mockFunction[String, String]
  }

  trait ProbedTransformFunc {
    self: ComplexEgress =>

    val probe = TestProbe()

    override val transformFunc = (s: String) => {
      probe.ref ! s
      s.toLowerCase
    }
  }

  object ProbedSink {
    case object Completed
  }

  trait ProbedSink {
    self: ComplexEgress =>
    val sinkProbe = TestProbe()

    override def sink[T](logic: RunnableGraphStreamletLogic): Sink[(T, Committable), NotUsed] =
      Flow[(T, Committable)].alsoTo(Sink.actorRef(sinkProbe.ref, ProbedSink.Completed)).to(logic.committableSink)
  }
}
