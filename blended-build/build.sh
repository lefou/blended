#!/bin/sh
set -e
cd blended-parent
mvn clean install
cd ..
mvn clean install -P parent | grep -v "Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
mvn clean install -P build,assembly | grep -v "Download" | grep -v "longer than 100"; test ${PIPESTATUS[0]} -eq 0
