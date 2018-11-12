package blended.streams.processor

import blended.streams.FlowProcessor
import blended.streams.FlowProcessor.IntegrationStep

import scala.util.Try

case class AckProcessor(name : String) extends FlowProcessor {

  override val f: IntegrationStep = { env =>
    Try {
      if (env.exception.isEmpty && env.requiresAcknowledge) {
        env.acknowledge()
      }
      env
    }
  }
}
