package blended.streams.processor

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.testkit.TestKit
import blended.container.context.api.ContainerIdentifierService
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class HeaderProcessorSpec extends TestKit(ActorSystem("header"))
  with LoggingFreeSpecLike
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  implicit val materializer = ActorMaterializer()
  private val log = Logger[HeaderProcessorSpec]

  System.setProperty("Country", "cc")

  val msg = FlowMessage("Hallo Andreas", FlowMessage.noProps)
  val src = Source.single(FlowEnvelope(msg))
  val sink = Sink.seq[FlowEnvelope]

  val flow : (List[(String, String)], Option[ContainerIdentifierService]) => RunnableGraph[Future[Seq[FlowEnvelope]]] = (rules, idSvc) =>
    src.via(HeaderTransformProcessor(name = "t", rules = rules, overwrite = true, idSvc = idSvc).flow).toMat(sink)(Keep.right)

  val result : (List[(String, String)], Option[ContainerIdentifierService]) => Seq[FlowEnvelope] = { (rules, idSvc) =>
    Await.result(flow(rules, idSvc).run(), 3.seconds)
  }

  "The HeaderProcessor should" - {

    "set plain headers correctly" in {

      val parser = new SpelExpressionParser()
      val exp = parser.parseExpression("foo")
      val ctxt = new StandardEvaluationContext()

      val r = result(List(
        "foo" -> "bar"
      ), None)

      r should have size (1)
      r.head.flowMessage.header[String]("foo") should be (Some("bar"))
    }

    "perform the normal resolution of container context properties" in {

      val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

      withSimpleBlendedContainer(baseDir) { sr =>

        implicit val timeout = 3.seconds
        waitOnService[ContainerIdentifierService](sr)(None) match {
          case None => fail()
          case Some(idSvc) =>

            idSvc.resolvePropertyString("$[[Country]]").get should be ("cc")

            val r = result(List(
              "foo" -> """"$[[Country]]"""",
              "foo2" -> """#foo""",
              "test" -> "42"
            ), Some(idSvc))

            log.info(r.toString())
            r.head.flowMessage.header should have size (3)
            r.head.flowMessage.header[String]("foo") should be (Some("cc"))
            r.head.flowMessage.header[String]("foo2") should be (Some("cc"))
            r.head.flowMessage.header[Int]("test") should be (Some(42))
        }
      }
    }
  }

}
