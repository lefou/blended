#!/usr/bin/env groovy
pipeline {
  agent any
  stages {
    stage('Prepare') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
            sbt -J-Xmx4096m -J-XX:MaxMetaspaceSize=1536m clean update
          '''
        }
      }
    }
    stage('Unit-Test and reports') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
            sbt -J-Xmx4096m -J-XX:MaxMetaspaceSize=1536m siteComplete
            git checkout --orphan gh-pages
            git reset
            git add -f target/site
            git commit -m "Deploy Site, build number ${BUILD_NUMBER}"
            git filter-branch -f --prune-empty --subdirectory-filter target/site
            git clean -f -f -d
            git push -f origin gh-pages
          '''
        }
      }
    }
    stage('Compile and publish') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
             sbt -J-Xmx4096m -J-XX:MaxMetaspaceSize=1536m clean publishLocal
          '''
        }
      }
    }
  }
}
