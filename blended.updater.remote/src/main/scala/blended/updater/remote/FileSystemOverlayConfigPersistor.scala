package blended.updater.remote

import java.io.File

import blended.updater.config.{ConfigWriter, OverlayConfig, OverlayConfigCompanion}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.Try

class FileSystemOverlayConfigPersistor(storageDir: File) extends OverlayConfigPersistor {

  private[this] val log = LoggerFactory.getLogger(classOf[FileSystemOverlayConfigPersistor])

  private[this] var overlayConfigs: Map[File, OverlayConfig] = Map()
  private[this] var initalized: Boolean = false

  def overlayConfigFileName(oc: OverlayConfig): String = s"${oc.name}-${oc.version}.conf"

  def initialize(): Unit = {
    log.debug("About to initialize overlay config persistor for storageDir: {}", storageDir)
    overlayConfigs = if (!storageDir.exists()) {
      Map()
    } else {
      val files = Option(storageDir.listFiles()).getOrElse(Array())
      val ocs = files.flatMap { file =>
        val oc = Try {
          ConfigFactory.parseFile(file).resolve()
        }.flatMap { rawConfig =>
          OverlayConfigCompanion.read(rawConfig)
        }
        log.debug("Found file: {} with: {}", Array(file, oc))
        oc.toOption.map(oc => file -> oc)
      }
      ocs.filter { case (file, oc) => file.getName() == overlayConfigFileName(oc) }.toMap
    }
    initalized = true
  }

  override def persistOverlayConfig(overlayConfig: OverlayConfig): Unit = {
    if (!initalized) initialize()
    val configFile = new File(storageDir, overlayConfigFileName(overlayConfig))
    if (configFile.exists()) {
      // collision, what should we do?
      if (overlayConfigs.get(configFile) == Some(overlayConfig)) {
        // known and same, so silently ignore
        log.debug("OverlayConfig already persistent")
      } else {
        val msg = "Cannot persist overlay config. Storage location already taken for a different configuration."
        log.error("{} Found file {} with config: {}", msg, configFile, overlayConfigs.get(configFile))
        sys.error(msg)
      }
    }
    ConfigWriter.write(OverlayConfigCompanion.toConfig(overlayConfig), configFile, None)
    overlayConfigs += configFile -> overlayConfig
  }

  override def findOverlayConfigs(): List[OverlayConfig] = {
    if (!initalized) initialize()
    overlayConfigs.values.toList
  }

}
