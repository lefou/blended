import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedHawtioLogin,
  packaging = "bundle",
  description = "Adding required imports to the hawtio war bundle.",
  build = Build(
    plugins = Seq(
      mavenBundlePlugin
    )
  )
)