pipeline {
  agent any
  stages {
    stage('Prepare Build') {
      steps {
        sh 'cd blended.docker/blended.docker.build; docker build -t blended-build .'
      }
    }
  }
}
