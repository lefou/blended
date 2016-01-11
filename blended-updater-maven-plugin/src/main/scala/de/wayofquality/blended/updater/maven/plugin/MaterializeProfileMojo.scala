package de.wayofquality.blended.updater.maven.plugin

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope
import java.io.File
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.Parameter
import blended.updater.tools.configbuilder._
import scala.collection.JavaConverters._

@Mojo(name = "materialize-profile", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
class MaterializeProfileMojo extends AbstractMojo {

  @Component
  var project: MavenProject = _

  @Parameter(required = true, property = "srcProfile")
  var srcProfile: File = _

  @Parameter(defaultValue = "${project.build.directory}/profile", property = "destDir", required = true)
  var destDir: File = _

  //  @Parameter(defaultValue = "false")
  //  var makeProfileAndVersionDir: Boolean = _

  @Parameter(property = "localRepositoryUrl")
  var localRepositoryUrl: String = _

  @Parameter(defaultValue = "compile", property = "dependencyScope")
  var dependencyScope: String = _

  @Parameter(defaultValue = "conf", property = "dependencyType")
  var dependencyType: String = _

  // TODO add filter for conf dependencies

  /**
   * Resolve all artifacts with mvn URLs only from the dependencies of the project.
   */
  @Parameter(property = "resolveFromDependencies", defaultValue = "false")
  var resolveFromDependencies: Boolean = _

  override def execute() = {
    getLog.debug("Running Mojo materialize-profile")

    val targetProfile = new File(destDir, "profile.conf")

    val confArtifacts = project.getDependencyArtifacts.asScala.
      filter(a => a.getScope() == dependencyScope && a.getType() == dependencyType)
    getLog.info(s"Feature artifacts: ${confArtifacts.mkString(", ")}")
    val featureFiles = confArtifacts.map(_.getFile())

    getLog.debug("feature args: " + featureFiles.toArray.flatMap { f =>
      Array("-r", f.getAbsolutePath)
    })

    val localRepoUrl = Option(localRepositoryUrl).getOrElse(project.getProjectBuildingRequest.getLocalRepository.getUrl)
    val remoteRepoUrls = project.getRepositories.asScala.map(r => r.getUrl)

    val repoArgs = if (resolveFromDependencies) {
      project.getArtifacts.asScala.toArray.flatMap { a =>
        Array("--maven-artifact",
          s"${a.getGroupId}:${a.getArtifactId}:${Option(a.getClassifier).filter(_ != "jar").getOrElse("")}:${a.getVersion}:${Option(a.getType).getOrElse("")}",
          a.getFile.getAbsolutePath)
      }
    } else {
      Array("--maven-url", localRepoUrl) ++ remoteRepoUrls.toArray.flatMap(u => Array("--maven-url", u))
    }

    val profileArgs = Array(
      "--debug",
      "-f", srcProfile.getAbsolutePath,
      "-o", targetProfile.getAbsolutePath,
      "--download-missing",
      "--update-checksums"
    ) ++ featureFiles.toArray.flatMap { f =>
        Array("-r", f.getAbsolutePath)
      } ++ repoArgs
    RuntimeConfigBuilder.run(profileArgs)
  }

}