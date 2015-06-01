#!/bin/bash

set -e

blendedVersion="1.1.4-SNAPSHOT"

projects="blended-activemq-brokerstarter \
blended-akka \
blended-akka-itest \
blended-camel-utils \
blended-container-context \
blended-container-id \
blended-container-registry \
blended-itestsupport \
blended-jaxrs \
blended-jmx \
blended-jolokia \
blended-karaf-branding \
blended-karaf-central \
blended-karaf-demo \
blended-karaf-features \
blended-karaf-installer \
blended-karaf-parent \
blended-launcher \
blended-mgmt-agent \
blended-mgmt-rest \
blended-modules \
blended-neo4j-api \
blended-parent \
blended-persistence \
blended-samples \
blended-scalatest-bundle \
blended-spray \
blended-spray-api \
blended-testing \
blended-testsupport \
blended-updater \
blended-util"

for project in $projects; do

  echo ""
  echo "*******************************************************"
  echo "* Project: $project"
  echo "* "

  cd "$project"

  cat > .project << EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
        <name>$project</name>
        <comment></comment>
        <projects>
        </projects>
        <buildSpec>
                <buildCommand>
                        <name>org.scala-ide.sdt.core.scalabuilder</name>
                        <arguments>
                        </arguments>
                </buildCommand>
        </buildSpec>
        <natures>
                <nature>org.scala-ide.sdt.core.scalanature</nature>
                <nature>org.eclipse.jdt.core.javanature</nature>
        </natures>
</projectDescription>
EOF

  cat > .classpath << EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
EOF

  for path in src/main/scala src/main/java src/main/binaryResources src/main/resources; do
    if [ -e "$path" ]; then
      cat >> .classpath << EOF
        <classpathentry kind="src" path="$path"/>
EOF
    fi
  done

  for path in src/test/scala src/test/java src/test/binaryResources src/test/resources; do
    if [ -e "$path" ]; then
      cat >> .classpath << EOF
        <classpathentry kind="src" output="target/test-classes" path="$path"/>
EOF
    fi
  done

  cat >> .classpath << EOF
        <classpathentry kind="output" path="target/classes"/>
        <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.7"/>
EOF

  mkdir -p .settings

  cat > .settings/org.eclipse.core.resources.prefs << EOF
eclipse.preferences.version=1
encoding/<project>=UTF-8
EOF

  cat > .settings/org.eclipse.core.runtime.prefs << EOF
eclipse.preferences.version=1
line.separator=\n
EOF

  cat > .settings/org.eclipse.jdt.core.prefs << EOF
eclipse.preferences.version=1
org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled
org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.7
org.eclipse.jdt.core.compiler.compliance=1.7
org.eclipse.jdt.core.compiler.problem.assertIdentifier=error
org.eclipse.jdt.core.compiler.problem.enumIdentifier=error
org.eclipse.jdt.core.compiler.problem.forbiddenReference=warning
org.eclipse.jdt.core.compiler.source=1.7
EOF

  cat > .settings/org.scala-ide.sdt.core.prefs << EOF

P=
Xcheckinit=false
Xdisable-assertions=false
Xelide-below=-2147483648
Xexperimental=false
Xfatal-warnings=false
Xfuture=false
Xlog-implicits=false
Xno-uescape=false
Xplugin=
Xplugin-disable=
Xplugin-require=
Xpluginsdir=misc/scala-devel/plugins
Ypresentation-debug=false
Ypresentation-delay=0
Ypresentation-log=
Ypresentation-replay=
Ypresentation-verbose=false
apiDiff=false
compileorder=Mixed
deprecation=false
eclipse.preferences.version=1
explaintypes=false
feature=false
g=vars
nameHashing=false
no-specialization=false
nowarn=false
optimise=false
recompileOnMacroDef=true
relationsDebug=false
scala.compiler.additionalParams=\ -Xsource\:2.10 -Ymacro-expand\:none
scala.compiler.installation=-510426087
scala.compiler.sourceLevel=2.10
scala.compiler.useProjectSettings=true
stopBuildOnError=true
target=jvm-1.6
unchecked=false
verbose=false
withVersionClasspathValidator=false
EOF

  rm -rf libs
  mvn dependency:copy-dependencies -DoutputDirectory=libs || true

  for lib in $(ls libs/*); do

    foundInReactor=""
    for p in $projects; do
      if [ "libs/${p}-${blendedVersion}.jar" = "$lib" ]; then
        foundInReactor="$p"
        break
      fi
    done

    if [ -n "$foundInReactor" ]; then
      echo "Found in reactor: $foundInReactor"
      cat >> .classpath << EOF
        <classpathentry combineaccessrules="false" kind="src" path="/${foundInReactor}" optional="true"/>
EOF
    else
      echo "Not found in reactor: $lib"
    fi

    cat >> .classpath << EOF
      <classpathentry kind="lib" path="$lib" sourcepath="libs/$(basename -s .jar $lib)-sources.jar"/>
EOF

  done

    cat >> .classpath << EOF
</classpath>
EOF

  mvn dependency:copy-dependencies -DoutputDirectory=libs -Dclassifier=sources || true

  cd ..

done