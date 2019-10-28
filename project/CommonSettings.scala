import com.typesafe.sbt.SbtScalariform.autoImport._
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype.SonatypeKeys._
import xsbti.api.{AnalyzedClass, Projection}

object CommonSettings {

  val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

  def apply() : Seq[Def.Setting[_]] = Seq(
    organization := "de.wayofquality.blended",
    homepage := Some(url("https://github.com/woq-blended/blended")),

    publishMavenStyle := true,

    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Spring IO Releases" at "https://repo.spring.io/libs-release",
      "Maven2 Local" at m2Repo
    ),

    licenses += ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),

    scmInfo := Some(
      ScmInfo(
        url("https://github.com/woq-blended/blended"),
        "scm:git@github.com:woq-blended/blended.git"
      )
    ),

    developers := List(
      Developer(id = "atooni", name = "Andreas Gies", email = "andreas@wayofquality.de", url = url("https://github.com/atooni")),
      Developer(id = "lefou", name = "Tobias Roeser", email = "tobias.roser@tototec.de", url = url("https://github.com/lefou"))
    ),

    sonatypeProfileName := "de.wayofquality",

    javacOptions in Compile ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),

    scalaVersion := blended.sbt.Dependencies.scalaVersion,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),

    scalariformAutoformat := false,
    scalariformWithBaseDirectory := true
  )
}
