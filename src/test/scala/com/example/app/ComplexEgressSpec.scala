package com.example.app

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import cloudflow.akkastream.testkit.scaladsl.AkkaStreamletTestKit
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

  "A ComplexEgress" should {
    "call synchronous mock method" in {
      val testEgress = new ComplexEgress with MockedSyncTransformer
      (testEgress.syncTransformer.transform _).expects(testData(0).id).returning("pi").once
      (testEgress.syncTransformer.transform _).expects(testData(1).id).returning("e").once
      (testEgress.syncTransformer.transform _).expects(testData(2).id).returning("absolute zero").once
      (testEgress.syncTransformer.transform _).expects(testData(3).id).returning("speed-o-light").once

      val testKit = AkkaStreamletTestKit(actorSystem, materializer)
      val src     = Source(testData)
      val in      = testKit.inletFromSource(testEgress.inlet, src)
      //testKit.run(testEgress, in, () => {}) //FIXME - this would implicitly stop the streamlet execution, but mock validation ALWAYS fails (regardless of extra wait times) when using this
      val streamlet = testKit.run(testEgress, List(in), List.empty)
      Await.result(streamlet.ready, 3 seconds)

      //FIXME - how to give the Streamlet adquate time to process all messages
      //Await.result(streamlet.completed, 10 seconds) //FIXME - Streamlet never completes - why?

      //FIXME - dirty hack to allow the Streamlet to do some actual processing! :(
      Await.result(Future { Thread.sleep(1000) }, 2 seconds) // without this extra time, the mocks never get called and, hence, throw
    }

    "call asynchronous mock method" in {
      val testEgress = new ComplexEgress with MockedAsyncTransformer
      (testEgress.asyncTransformer.transformAsync _).expects(testData(0).id.reverse).returning(Future("pi")).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(1).id.reverse).returning(Future("e")).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(2).id.reverse).returning(Future("absolute zero")).once
      (testEgress.asyncTransformer.transformAsync _).expects(testData(3).id.reverse).returning(Future("speed-o-light")).once

      val testKit = AkkaStreamletTestKit(actorSystem, materializer)
      val src     = Source(testData)
      val in      = testKit.inletFromSource(testEgress.inlet, src)
      //testKit.run(testEgress, in, () => {}) //FIXME - this would implicitly stop the streamlet execution, but mock validation ALWAYS fails (regardless of extra wait times) when using this
      val streamlet = testKit.run(testEgress, List(in), List.empty)
      Await.result(streamlet.ready, 3 seconds)

      //FIXME - how to give the Streamlet adquate time to process all messages
      //Await.result(streamlet.completed, 10 seconds) //FIXME - Streamlet never completes - why?

      //FIXME - dirty hack to allow the Streamlet to do some actual processing! :(
      Await.result(Future { Thread.sleep(1000) }, 2 seconds) // without this extra time, the mocks never get called and, hence, throw
    }

    "call function mock method" in {
      val testEgress = new ComplexEgress with MockedTransformFunc
      testEgress.transformFunc.expects(testData(0).id.reverse.toUpperCase).returning("pi").once
      testEgress.transformFunc.expects(testData(1).id.reverse.toUpperCase).returning("e").once
      testEgress.transformFunc.expects(testData(2).id.reverse.toUpperCase).returning("absolute zero").once
      testEgress.transformFunc.expects(testData(3).id.reverse.toUpperCase).returning("speed-o-light").once

      val testKit = AkkaStreamletTestKit(actorSystem, materializer)
      val src     = Source(testData)
      val in      = testKit.inletFromSource(testEgress.inlet, src)
      testKit.run(testEgress, in, () => {}) //FIXME - Why does this one work here, but not with object mocks (see above)?? - enabling it in the middle test will blow this one... very weird!!
//      val streamlet = testKit.run(testEgress, List(in), List.empty)
//      Await.result(streamlet.ready, 3 seconds)
//
//      //FIXME - how to give the Streamlet adquate time to process all messages
//      //Await.result(streamlet.completed, 10 seconds) //FIXME - Streamlet never completes - why?
//
//      //FIXME - dirty hack to allow the Streamlet to do some actual processing! :(
//      Await.result(Future { Thread.sleep(1000) }, 2 seconds) // without this extra time, the mocks never get called and, hence, throw
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
}
