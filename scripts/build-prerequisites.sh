#!/bin/bash -e

git clone --branch master --depth 1 "https://github.com/woq-blended/sbt-testlogconfig.git" "${TRAVIS_BUILD_DIR}/sbt-testlogconfig"
cd "${TRAVIS_BUILD_DIR}/sbt-testlogconfig"
sbt publishLocal
cd "${TRAVIS_BUILD_DIR}"
