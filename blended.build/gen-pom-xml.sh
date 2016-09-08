#!/usr/bin/env bash

dirs=$(find . -name pom.scala -exec dirname {} \; | grep -v "target/scalamodel")

for dir in $dirs; do

  # As we cannot check the included files, we always generate all
  cont="y"

#  if [ "${dir}/pom.xml" -ot "${dir}/pom.scala" ]; then
#    cont="y"
#
#    echo -n "${dir}/pom.xml is newer than ${dir}/pom.scala! Continue anyway [y/N]?"
#    read cont
#
#  fi

  if [ "$cont" == "y" ]; then

    echo "Converting ${dir}/pom.scala ->  ${dir}/pom.xml"
    (cd ${dir} && mvn -N io.takari.polyglot:polyglot-translate-plugin:0.1.15:translate -Dinput=pom.scala -Doutput=pom.xml)

  fi

done

unset cont