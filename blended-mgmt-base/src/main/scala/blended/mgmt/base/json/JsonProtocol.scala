package blended.mgmt.base.json

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

import blended.mgmt.base.ActivateProfile
import blended.mgmt.base.AddOverlayConfig
import blended.mgmt.base.AddRuntimeConfig
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ContainerRegistryResponseOK
import blended.mgmt.base.NameVersion
import blended.mgmt.base.OverlayState
import blended.mgmt.base.RemoteContainerState
import blended.mgmt.base.ServiceInfo
import blended.mgmt.base.StageProfile
import blended.mgmt.base.UpdateAction
import blended.updater.config.Artifact
import blended.updater.config.BundleConfig
import blended.updater.config.FeatureConfig
import blended.updater.config.FeatureRef
import blended.updater.config.GeneratedConfig
import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayRef
import blended.updater.config.RuntimeConfig
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.JsonParser
import spray.json.ParserInput
import spray.json.RootJsonFormat
import blended.mgmt.base.Profile
import blended.mgmt.base.OverlaySet

/**
 * Defines type-classes to de-/serialization of protocol classes.
 */
trait JsonProtocol extends DefaultJsonProtocol {

  implicit val nameVersionFormat: RootJsonFormat[NameVersion] = jsonFormat2(NameVersion)
  implicit val overlayStateFormat: RootJsonFormat[OverlayState] = new RootJsonFormat[OverlayState] {
    import spray.json._
    override def write(obj: OverlayState): JsValue = obj match {
      case OverlayState.Active => "active".toJson
      case OverlayState.Valid => "valid".toJson
      case OverlayState.Invalid => "invalid".toJson
      case OverlayState.Pending => "pending".toJson
      case _ => serializationError(s"Could not write object ${obj}")
    }
    override def read(json: JsValue): OverlayState = {
      json match {
        case JsString("active") => OverlayState.Active
        case JsString("valid") => OverlayState.Valid
        case JsString("invalid") => OverlayState.Invalid
        case JsString("pending") => OverlayState.Pending
        case _ => deserializationError("UpdateAction expected")
      }
    }
  }
  implicit val overlaySetFormat: RootJsonFormat[OverlaySet] = jsonFormat3(OverlaySet)
  implicit val profileFormat: RootJsonFormat[Profile] = jsonFormat3(Profile)
  implicit val serviceInfoFormat: RootJsonFormat[ServiceInfo] = jsonFormat4(ServiceInfo)
  implicit val containerInfoFormat: RootJsonFormat[ContainerInfo] = jsonFormat4(ContainerInfo)
  implicit val artifactFormat: RootJsonFormat[Artifact] = jsonFormat3(Artifact)
  implicit val bundleConfigFormat: RootJsonFormat[BundleConfig] = jsonFormat3(BundleConfig)
  implicit val featureRefFormat: RootJsonFormat[FeatureRef] = jsonFormat3(FeatureRef)
  implicit val featureConfigFormat: RootJsonFormat[FeatureConfig] = jsonFormat5(FeatureConfig)
  implicit val runtimeConfigFormat: RootJsonFormat[RuntimeConfig] =
    // RuntimeConfig has an additional derived val confuses automatic field extraction
    jsonFormat(RuntimeConfig,
      "name", "version", "bundles",
      "startLevel", "defaultStartLevel",
      "properties", "frameworkProperties", "systemProperties",
      "features", "resources", "resolvedFeatures")
  implicit val overlayRefFormat: RootJsonFormat[OverlayRef] = jsonFormat2(OverlayRef)
  // we use Config's own JSON renderer and parser
  implicit val configFormat: RootJsonFormat[Config] = new RootJsonFormat[Config] {
    override def write(obj: Config): JsValue = {
      val json = obj.root().render(ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setFormatted(true).setJson(true))
      JsonParser.apply(ParserInput(json))
    }

    override def read(json: JsValue): Config = {
      ConfigFactory.parseString(json.toString())
    }
  }
  implicit val generatedConfigFormat: RootJsonFormat[GeneratedConfig] = jsonFormat2(GeneratedConfig)

  implicit val overlayConfigFormat: RootJsonFormat[OverlayConfig] = jsonFormat4(OverlayConfig)

  implicit val stageProfileFormat: RootJsonFormat[StageProfile] = jsonFormat4(StageProfile)
  implicit val activateProfileFormat: RootJsonFormat[ActivateProfile] = jsonFormat4(ActivateProfile)
  implicit val addRuntimeConfigFormat: RootJsonFormat[AddRuntimeConfig] = jsonFormat2(AddRuntimeConfig)
  implicit val addOverlayConfigFormat: RootJsonFormat[AddOverlayConfig] = jsonFormat2(AddOverlayConfig)

  implicit val updateActionFormat: RootJsonFormat[UpdateAction] = new RootJsonFormat[UpdateAction] {

    import spray.json._

    override def write(obj: UpdateAction): JsValue = obj match {
      case a: StageProfile => a.toJson
      case a: ActivateProfile => a.toJson
      case a: AddRuntimeConfig => a.toJson
      case a: AddOverlayConfig => a.toJson
      case _ => serializationError(s"Could not write object ${obj}")
    }

    override def read(json: JsValue): UpdateAction = {
      val stageProfile = classOf[StageProfile].getSimpleName()
      val activateProfile = classOf[ActivateProfile].getSimpleName()
      val addRuntimeConfig = classOf[AddRuntimeConfig].getSimpleName()
      val addOverlayConfig = classOf[AddOverlayConfig].getSimpleName()

      json.asJsObject.fields.get("kind") match {
        case Some(JsString(`stageProfile`)) => stageProfileFormat.read(json)
        case Some(JsString(`activateProfile`)) => activateProfileFormat.read(json)
        case Some(JsString(`addRuntimeConfig`)) => addRuntimeConfigFormat.read(json)
        case Some(JsString(`addOverlayConfig`)) => addOverlayConfigFormat.read(json)
        case _ => deserializationError("UpdateAction expected")
      }

    }
  }

  implicit val responseFormat: RootJsonFormat[ContainerRegistryResponseOK] = jsonFormat2(ContainerRegistryResponseOK)

  implicit val remoteContainerState: RootJsonFormat[RemoteContainerState] = jsonFormat2(RemoteContainerState)
}

object JsonProtocol extends JsonProtocol
