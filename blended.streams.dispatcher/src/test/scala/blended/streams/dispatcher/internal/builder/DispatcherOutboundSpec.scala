package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, Graph, Materializer, SinkShape}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.worklist.WorklistState.WorklistState
import blended.streams.worklist.{WorklistEvent, WorklistState}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext

class DispatcherOutboundSpec extends DispatcherSpecSupport
  with Matchers {

  override def loggerName: String = classOf[DispatcherOutboundSpec].getName()

  private def runnableOutbound(
    ctxt : DispatcherExecContext,
    testMsg : FlowEnvelope,
    send : Flow[FlowEnvelope, FlowEnvelope, NotUsed]
  ) : (Collector[WorklistEvent], Collector[FlowEnvelope], RunnableGraph[NotUsed]) = {

    implicit val system : ActorSystem = ctxt.system

    val outColl = Collector[WorklistEvent]("out")
    val errColl = Collector[FlowEnvelope]("err")

    val source = Source.single[FlowEnvelope](testMsg)

    val sinkGraph : Graph[SinkShape[FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val outStep = b.add(DispatcherBuilder(ctxt.idSvc, ctxt.cfg, send)(ctxt.bs).outbound())
        val out = b.add(outColl.sink)
        val err = b.add(errColl.sink)

        outStep.out0 ~> out
        outStep.out1 ~> err

        SinkShape(outStep.in)
      }
    }

    (outColl, errColl,  source.to(sinkGraph))
  }

  def testOutbound(expectedState: WorklistState, send: Flow[FlowEnvelope, FlowEnvelope, NotUsed]) : Unit = {
    withDispatcherConfig { ctxt =>

      implicit val system : ActorSystem = ctxt.system
      implicit val eCtxt : ExecutionContext = system.dispatcher
      implicit val materializer : Materializer = ActorMaterializer()

      val envelope = FlowEnvelope().withHeader(ctxt.bs.headerConfig.headerBranch, "outbound").get

      val (outColl, errColl, out) = runnableOutbound(ctxt, envelope, send)

      try {
        out.run()

        val result = for {
          err <- errColl.result
          evt <- outColl.result
        } yield (err, evt)

        result.map { case (error, events) =>
          error should be (empty)
          events should have size 1

          val event = events.head
          event.worklist.items should have size 1
          event.worklist.id should be (envelope.id)
          event.state should be (expectedState)
        }
      } finally {
        system.stop(outColl.actor)
        system.stop(errColl.actor)
      }
    }
  }

  "The outbound flow of the dispatcher should" - {

    "produce a worklist completed event for successfull completions of the outbound flow" in {
      val good = Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env => env}
      testOutbound(WorklistState.Completed, good)
    }

    "produce a worklist failed event after unsuccessfull completions of the outbound flow" in {
      val bad = Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env => env.withException(new Exception("Boom !")) }
      testOutbound(WorklistState.Failed, bad)
    }
  }
}
