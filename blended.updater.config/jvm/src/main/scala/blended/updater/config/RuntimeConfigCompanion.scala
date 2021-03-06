package blended.updater.config

import java.io._
import java.net.URL
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Formatter

import blended.updater.config.util.ConfigPropertyMapConverter
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigValue}

import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.util.Try
import scala.util.control.NonFatal

object RuntimeConfigCompanion {

  def read(config: Config): Try[RuntimeConfig] = Try {

    val getProperties : String => Map[String, String] =
      key => ConfigPropertyMapConverter.getKeyAsPropertyMap(config, key, Some(() => Map.empty))

    val optionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    val reference = ConfigFactory.parseResources(getClass(), "RuntimeConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    config.withFallback(optionals).checkValid(reference)

    RuntimeConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      bundles =
        if (config.hasPath("bundles"))
          config.getObjectList("bundles").asScala.map { bc => BundleConfigCompanion.read(bc.toConfig()).get }.toList
        else List.empty,
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      properties = getProperties("properties"),
      frameworkProperties = getProperties("frameworkProperties"),
      systemProperties = getProperties("systemProperties"),
      features =
        if (config.hasPath("features"))
          config.getObjectList("features").asScala.map { f =>
          FeatureRefCompanion.fromConfig(f.toConfig()).get
        }.toList
        else List.empty,
      resources =
        if (config.hasPath("resources"))
          config.getObjectList("resources").asScala.map(r => ArtifactCompanion.read(r.toConfig()).get).toList
        else List.empty,
      resolvedFeatures =
        if (config.hasPath("resolvedFeatures"))
          config.getObjectList("resolvedFeatures").asScala.map(r => FeatureConfigCompanion.read(r.toConfig()).get).toList
        else List.empty
    )
  }


  def toConfig(runtimeConfig: RuntimeConfig): Config = {

    val propCfg : Map[String, String] => ConfigValue = m => ConfigPropertyMapConverter.propertyMapToConfigValue(m)

    val config = Map(
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "bundles" -> runtimeConfig.bundles.map(BundleConfigCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "startLevel" -> runtimeConfig.startLevel,
      "defaultStartLevel" -> runtimeConfig.defaultStartLevel,
      "properties" -> propCfg(runtimeConfig.properties),
      "frameworkProperties" -> propCfg(runtimeConfig.frameworkProperties),
      "systemProperties" -> propCfg(runtimeConfig.systemProperties),
      "features" -> runtimeConfig.features.map(FeatureRefCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "resources" -> runtimeConfig.resources.map(ArtifactCompanion.toConfig).map(_.root().unwrapped()).asJava,
      "resolvedFeatures" -> runtimeConfig.resolvedFeatures.map(FeatureConfigCompanion.toConfig).map(_.root().unwrapped()).asJava
    ).asJava

    ConfigFactory.parseMap(config).resolve()
  }

  def bytesToString(digest: Array[Byte]): String = {
    import java.lang.StringBuilder
    val result = new StringBuilder(32);
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString
  }

  def digestFile(file: File): Option[String] = {
    if (!file.exists()) None else {
      val sha1Stream = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA"))
      try {
        while (sha1Stream.read != -1) {}
        Option(bytesToString(sha1Stream.getMessageDigest.digest))
      } catch {
        case NonFatal(e) => None
      } finally {
        sha1Stream.close()
      }
    }
  }

  def download(url: String, file: File): Try[File] =
    Try {
      val parentDir = file.getAbsoluteFile().getParentFile() match {
        case null =>
          new File(".")
        case parent =>
          if (!parent.exists()) {
            parent.mkdirs()
          }
          parent
      }

      val tmpFile = File.createTempFile(s".${file.getName()}", "", parentDir)
      try {
        val fileStream = new FileOutputStream(tmpFile)
        val outStream = new BufferedOutputStream(fileStream)
        try {

          val connection = new URL(url).openConnection
          connection.setRequestProperty("User-Agent", "Blended Updater")
          val inStream = new BufferedInputStream(connection.getInputStream())
          try {
            val bufferSize = 1024
            var buffer = new Array[Byte](bufferSize)

            while (inStream.read(buffer, 0, bufferSize) match {
              case count if count < 0 => false
              case count => {
                outStream.write(buffer, 0, count)
                true
              }
            }) {}
          } finally {
            inStream.close()
          }
        } finally {
          outStream.flush()
          outStream.close()
          fileStream.flush()
          fileStream.close()
        }

        Files.move(Paths.get(tmpFile.toURI()), Paths.get(file.toURI()),
          StandardCopyOption.ATOMIC_MOVE);

        file
      } catch {
        case NonFatal(e) =>
          if (tmpFile.exists()) {
            tmpFile.delete()
          }
          throw e
      }

    }

  def bundlesBaseDir(baseDir: File): File = new File(baseDir, "bundles")

  def bundleLocation(bundle: BundleConfig, baseDir: File): File =
    new File(RuntimeConfigCompanion.bundlesBaseDir(baseDir), bundle.jarName.getOrElse(RuntimeConfig.resolveFileName(bundle.url).get))

  def bundleLocation(artifact: Artifact, baseDir: File): File =
    new File(RuntimeConfigCompanion.bundlesBaseDir(baseDir), artifact.fileName.getOrElse(RuntimeConfig.resolveFileName(artifact.url).get))

  def resourceArchiveLocation(resourceArchive: Artifact, baseDir: File): File =
    new File(baseDir, s"resources/${resourceArchive.fileName.getOrElse(RuntimeConfig.resolveFileName(resourceArchive.url).get)}")

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact, baseDir: File, mvnBaseUrl: Option[String]): File = {
    val resFile = resourceArchiveLocation(resourceArchive, baseDir)
    new File(resFile.getParentFile(), s".${resFile.getName()}")
  }

}
