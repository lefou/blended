package blended.updater.tools.configbuilder

import java.io.{BufferedOutputStream, ByteArrayOutputStream, File, FileOutputStream, PrintStream}
import java.util.regex.{Matcher, Pattern}

import blended.updater.config._
import blended.updater.config.util.Unzipper
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import de.tototec.cmdoption.{CmdOption, CmdlineParser}

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.{Failure, Success, Try}

object RuntimeConfigBuilder {

  class CmdOptions {
    @CmdOption(names = Array("-h", "--help"), isHelp = true)
    var help: Boolean = false

    @CmdOption(names = Array("-d", "--download-missing"))
    var downloadMissing: Boolean = false

    @CmdOption(names = Array("-u", "--update-checksums"))
    var updateChecksums: Boolean = false

    @CmdOption(names = Array("-c", "--check"))
    var check: Boolean = false

    @CmdOption(names = Array("-f"), args = Array("configfile"), description = "Read the configuration from file {0}")
    var configFile: String = ""

    @CmdOption(names = Array("-o"), args = Array("outfile"), description = "Write the updated config file to {0}",
      conflictsWith = Array("-i"))
    var outFile: String = ""

    @CmdOption(
      names = Array("-i", "--in-place"),
      description = "Modifiy the input file (-o) instead of writing to the output file",
      requires = Array("-f"),
      conflictsWith = Array("-o")
    )
    var inPlace: Boolean = false

    @CmdOption(names = Array("-r", "--feature-repo"), args = Array("featurefile"),
      description = "Lookup additional feature configuration(s) from file {0}",
      maxCount = -1)
    def addFeatureRepo(repo: String): Unit = featureRepos ++= Seq(repo)

    var featureRepos: Seq[String] = Seq()

    @CmdOption(names = Array("-m", "--maven-url"), args = Array("url"), maxCount = -1)
    def addMavenUrl(mavenUrl: String) = this.mavenUrls ++= Seq(mavenUrl)

    var mavenUrls: Seq[String] = Seq()

    @CmdOption(names = Array("--debug"))
    var debug: Boolean = false

    @CmdOption(names = Array("--maven-artifact"), args = Array("GAV", "file"), maxCount = -1,
      description = "Gives explicit (already downloaded) file locations for Maven GAVs")
    def addMavenDir(gav: String, file: String) = this.mavenArtifacts ++= Seq(gav -> file)
    var mavenArtifacts: Seq[(String, String)] = Seq()

    @CmdOption(names = Array("--explode-resources"), description = "Explode resources (unpack and update touch-files)")
    var explodeResources: Boolean = false

    @CmdOption(names = Array("--add-overlay-file"), args = Array("file"), maxCount = -1,
      description = "Add the given overlay config file to the final profile")
    def addOverlayFile(file: String) = this.overlayFiles :+= file
    var overlayFiles: Seq[String] = Seq()

    @CmdOption(names = Array("--write-overlays-config"), description = "Write a specific overlays config (or base if no overlays were given)")
    var writeOverlaysConfig: Boolean = false

    @CmdOption(names = Array("--create-launch-config"), args = Array("file"),
      description = "Creates the given launcher config file, honoring the given overlays")
    var createLaunchConfigFile: String = _

    @CmdOption(names = Array("--profile-base-dir"), args = Array("dir"),
      description = "Set the profile base directory to be written via --update-launcher-conf")
    var profileBaseDir: String = "${BLENDED_HOME}/profiles"

    @CmdOption(names = Array("--env-var"), args = Array("key", "value"), maxCount = -1,
      description = "Add an additional environment variable as a fallback for resolving the config."
    )
    def addEnvVar(key: String, value: String) = this.envVars ++= Seq(key -> value)
    var envVars : Seq[(String, String)] = Seq()
  }

  def main(args: Array[String]): Unit = {
    try {
      run(args)
      sys.exit(0)
    } catch {
      case e: Throwable =>
        Console.err.println(s"An error occurred: ${e.getMessage()}")
        sys.exit(1)
    }
  }

  /**
    * Same as [[RuntimeConfigBuilder#main]], but does not call `sys.exit` but throws an exception in case of non-success.
    * @param args
    */
  def run(args: Array[String]): Unit = {
    run(args = args, debugLog = None)
  }

  def run(
    args: Array[String],
    debugLog: Option[String => Unit] = None,
    infoLog: String => Unit = Console.out.println,
    errorLog: String => Unit = Console.err.println
  ): Unit = {

    val options = new CmdOptions()

    val cp = new CmdlineParser(options)
    cp.parse(args: _*)
    if (options.help) {
      cp.usage()
      return
    }

    //    val debug = options.debug
    val debug: String => Unit = debugLog match {
      case Some(debugLog) => debugLog
      case None =>
        if (options.debug) msg => Console.err.println(msg)
        else msg => {}
    }

    debug(s"RuntimeConfigBuilder: ${args.mkString(" ")}")

    if (options.configFile.isEmpty()) sys.error("No config file given")

    val mvnGavs = options.mavenArtifacts.map {
      case (gav, file) => MvnGav.parse(gav) -> file
    }.collect {
      case (Success(gav), file) => gav -> file
    }

    // read feature repo files
    val features = options.featureRepos.map { fileName =>
      val featureConfig = ConfigFactory.parseFile(new File(fileName), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
      FeatureConfigCompanion.read(featureConfig).get
    }
    debug("features: " + features)

    val configFile = new File(options.configFile).getAbsoluteFile()
    val outFile = Option(options.outFile.trim())
      .filter(!_.isEmpty())
      .orElse(if (options.inPlace) Option(configFile.getPath()) else None)
      .map(new File(_).getAbsoluteFile())

    val dir = outFile.flatMap(f => Option(f.getParentFile())).getOrElse(configFile.getParentFile())
    val config = ConfigFactory
      .parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false))
      .withFallback(ConfigFactory.parseMap(options.envVars.toMap.asJava))
      .resolve()

    val unresolvedRuntimeConfig = RuntimeConfigCompanion.read(config).get
    //    val unresolvedLocalRuntimeConfig = LocalRuntimeConfig(unresolvedRuntimeConfig, dir)

    debug("unresolved runtime config: " + unresolvedRuntimeConfig)
    val resolved = FeatureResolver.resolve(unresolvedRuntimeConfig, features).get
    debug("runtime config with resolved features: " + resolved)

    val localRuntimeConfig = LocalRuntimeConfig(resolved, dir)

    if (options.check) {
      val issues = localRuntimeConfig.validate(
        includeResourceArchives = true,
        explodedResourceArchives = false
      )
      if (!issues.isEmpty) {
        sys.error(issues.mkString("\n"))
      }
    }

    lazy val mvnUrls = resolved.runtimeConfig.properties.get(RuntimeConfig.Properties.MVN_REPO).toSeq ++ options.mavenUrls
    debug(s"Maven URLs: $mvnUrls")

    def downloadUrls(b: Artifact): Seq[String] = {
      val directUrl = MvnGavSupport.downloadUrls(mvnGavs, b, options.debug)
      directUrl.map(Seq(_)).getOrElse(mvnUrls.flatMap(baseUrl => RuntimeConfig.resolveBundleUrl(b.url, Option(baseUrl)).toOption).to[Seq])
    }

    if (options.downloadMissing) {

      val files = resolved.allBundles.distinct.map { b =>
        RuntimeConfigCompanion.bundleLocation(b, dir) -> downloadUrls(b.artifact)
      } ++ resolved.runtimeConfig.resources.map(r =>
        RuntimeConfigCompanion.resourceArchiveLocation(r, dir) -> downloadUrls(r))

      val states = files.par.map {
        case (file, urls) =>
          if (!file.exists()) {
            infoLog(s"Fetching: ${file}")
            urls.find { url =>
              debug(s"Downloading ${file.getName()} from $url")
              RuntimeConfigCompanion.download(url, file).isSuccess
            }.map { url => file -> Try(file)
            }.getOrElse {
              val msg = s"Could not download ${file.getName()} from: $urls"
              errorLog(msg)
              sys.error(msg)
            }
          } else file -> Try(file)
      }.seq

      val issues = states.collect {
        case (file, Failure(e)) =>
          errorLog(s"Could not download: $file (${e.getClass.getSimpleName()}: ${e.getMessage()})")
          e
      }
      if (!issues.isEmpty) {
        sys.error(issues.mkString("\n"))
      }
    }

    val newRuntimeConfig = if (options.updateChecksums) {
      var checkedFiles: Map[File, String] = Map()

      def checkAndUpdate(file: File, r: Artifact): Artifact = {
        checkedFiles.get(file).orElse(RuntimeConfigCompanion.digestFile(file)).map { checksum =>
          checkedFiles += file -> checksum
          if (r.sha1Sum != Option(checksum)) {
            infoLog(s"${if (r.sha1Sum.isDefined) "Updating" else "Creating"} checksum for: ${r.fileName.getOrElse(RuntimeConfig.resolveFileName(r.url).get)}")
            r.copy(sha1Sum = Option(checksum))
          } else r
        }.getOrElse(r)
      }

      def checkAndUpdateResource(a: Artifact): Artifact =
        checkAndUpdate(localRuntimeConfig.resourceArchiveLocation(a), a)

      def checkAndUpdateBundle(b: BundleConfig): BundleConfig =
        b.copy(artifact = checkAndUpdate(localRuntimeConfig.bundleLocation(b), b.artifact))

      def checkAndUpdateFeatures(f: FeatureConfig): FeatureConfig =
        f.copy(bundles = f.bundles.map(checkAndUpdateBundle))

      ResolvedRuntimeConfig(
        resolved.runtimeConfig.copy(
          bundles = resolved.runtimeConfig.bundles.map(checkAndUpdateBundle),
          resolvedFeatures = resolved.allReferencedFeatures.map(checkAndUpdateFeatures),
          resources = resolved.runtimeConfig.resources.map(checkAndUpdateResource)
        )
      ).runtimeConfig
    } else resolved.runtimeConfig

    if (options.explodeResources) {
      newRuntimeConfig.resources.map { r =>
        val resourceFile = localRuntimeConfig.resourceArchiveLocation(r)
        if (!resourceFile.exists()) sys.error("Could not unpack missing resource file: " + resourceFile)
        val blacklist = List("profile.conf", "bundles", "resources", "overlays")
        Unzipper.unzip(resourceFile, localRuntimeConfig.baseDir, Nil,
          fileSelector = Some { fileName: String => !blacklist.exists(fileName == _) },
          placeholderReplacer = None) match {
            case Failure(e) => throw new RuntimeException("Could not update resource file: " + resourceFile, e)
            case _ =>
          }
        localRuntimeConfig.createResourceArchiveTouchFile(r, r.sha1Sum) match {
          case Failure(e) =>
            throw new RuntimeException("Could not create resource archive touch file for resource file: " + resourceFile, e)
          case _ =>
        }
      }
    }

    // read given overlays configs, e.g. A-1 and B-2
    val overlayConfigs = options.overlayFiles.map { f =>
      val config = ConfigFactory.parseFile(new File(f))
      OverlayConfigCompanion.read(config).get
    }.toSet

    if (options.writeOverlaysConfig) {

      // validate configs, e.g. no conflicts
      val localOverlays = LocalOverlays(overlayConfigs, localRuntimeConfig.baseDir)
      debug("About to validate overlays: " + localOverlays)
      val validationResult = localOverlays.validate()
      debug("overlay validation result: " + validationResult)
      validationResult match {
        case Nil => // ok
        case errors =>
          sys.error("Inconsistent overlays given:\n- " + errors.mkString("\n- "))
      }

      // materialize config generators of overlay configs, e.g. <profileDir>/A-1/B-2/container/application_overlay.conf
      localOverlays.materialize().get

      // write resulting overlay config, e.g. <profileDir>/overlays/A-1-B-2.conf (or base.conf)
      val overlayFile = LocalOverlays.preferredConfigFile(localOverlays.overlayRefs, localOverlays.profileDir)
      ConfigWriter.write(LocalOverlays.toConfig(localOverlays), overlayFile, None)
    }

    if (Option(options.createLaunchConfigFile).isDefined) {
      val profileBaseDir = options.profileBaseDir
      val profileLookup = ProfileLookup(
        profileName = localRuntimeConfig.runtimeConfig.name,
        profileVersion = localRuntimeConfig.runtimeConfig.version,
        profileBaseDir = new File("REPLACE_BASE_DIR"),
        overlays = overlayConfigs.map(_.overlayRef)
      )
      val file = new File(options.createLaunchConfigFile)
      debug("Writing launch config file: " + file)
      val os = new ByteArrayOutputStream()
      ConfigWriter.write(ProfileLookup.toConfig(profileLookup), os, None)
      // FIXME: the following is magic to support env variable in output, but it will work only if th file string has no spaces in it
      val confOutput = Pattern.
        compile("[\"]REPLACE_BASE_DIR[\"]").
        matcher(os.toString()).
        replaceAll(Matcher.quoteReplacement(profileBaseDir))
      val ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
      try ps.println(confOutput) finally ps.close()
    }

    outFile match {
      case None =>
        ConfigWriter.write(RuntimeConfigCompanion.toConfig(newRuntimeConfig), Console.out, None)
      case Some(f) =>
        debug("Writing config file: " + configFile)
        ConfigWriter.write(RuntimeConfigCompanion.toConfig(newRuntimeConfig), f, None)
    }

  }

}
