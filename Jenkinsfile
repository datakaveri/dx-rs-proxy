pipeline {

  environment {
    devRegistry = 'ghcr.io/datakaveri/rs-proxy-dev'
    deplRegistry = 'ghcr.io/datakaveri/rs-proxy-depl'
    registryUri = 'https://ghcr.io'
    registryCredential = 'datakaveri-ghcr'
    GIT_HASH = GIT_COMMIT.take(7)
  }

  agent { 
    node {
      label 'slave1' 
    }
  }

  stages {

    stage('Trivy Code Scan (Dependencies)') {
      steps {
        script {
          sh '''
            trivy fs --scanners vuln,secret,misconfig --output trivy-fs-report.txt .
          '''
        }
      }
    }
    
    stage('Build images') {
      steps{
        script {
          echo 'Pulled - ' + env.GIT_BRANCH
          devImage = docker.build( devRegistry, "-f ./docker/dev.dockerfile .")
          deplImage = docker.build( deplRegistry, "-f ./docker/depl.dockerfile .")
        }
      }
    }

    stage('Trivy Docker Image Scan') {
      steps {
        script {
          sh "trivy image --output trivy-dev-image-report.txt ${devImage.imageName()}"
          sh "trivy image --output trivy-depl-image-report.txt ${deplImage.imageName()}"
        }
      }
    }
    stage('Archive Trivy Reports') {
      steps {
        archiveArtifacts artifacts: 'trivy-*.txt', allowEmptyArchive: true
        publishHTML(target: [
          allowMissing: true,
          keepAll: true,
          reportDir: '.',
          reportFiles: 'trivy-fs-report.txt, trivy-dev-image-report.txt, trivy-depl-image-report.txt',
          reportName: 'Trivy Reports'
        ])
      }
    }

    stage('Unit Tests and Code Coverage Test'){
      steps{
        script{
          sh 'cp /home/ubuntu/configs/5.6.0/rs-proxy-config-test.json ./secrets/all-verticles-configs/config-test.json'
          catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
              sh "mvn clean test checkstyle:checkstyle pmd:pmd"
            }  
        }
        }
      post{
      always {
        xunit (
          thresholds: [ skipped(failureThreshold: '0'), failed(failureThreshold: '0') ],
          tools: [ JUnit(pattern: 'target/surefire-reports/*.xml') ]
        )
        jacoco classPattern: 'target/classes', exclusionPattern: 'iudx/rs/proxy/apiserver/ApiServerVerticle.class,iudx/rs/proxy/database/example/postgres/PostgresServiceImpl.class,**/*VertxEBProxy.class,**/Constants.class,**/*Verticle.class,iudx/rs/proxy/apiserver/*.class,iudx/rs/proxy/deploy/*.class,**/*AuthenticationService.class,**/*MeteringService.class,**/*CacheService.class,**/*Method.class,**/*DatabaseService.class,**/*ApiServerConstants.class,**/*CacheServiceVertxProxyHandler.class,**/*MeteringServiceVertxProxyHandler.class,**/*AuthenticationServiceVertxProxyHandler.class,**/ConsentLoggingServiceVertxProxyHandler.class,**/database/**,**/databroker/**,**/JwtDataConverter.class,**/PayloadSigningManager.class', execPattern: 'target/jacoco.exec', sourcePattern: 'src/main/java'
      
        recordIssues(
          enabledForFailure: true,
          skipBlames: true,
          qualityGates: [[threshold:0, type: 'TOTAL', unstable: false]],
          tool: checkStyle(pattern: 'target/checkstyle-result.xml')
        )
        recordIssues(
          enabledForFailure: true,
          skipBlames: true,
          qualityGates: [[threshold:100, type: 'TOTAL', unstable: false]],
          tool: pmdParser(pattern: 'target/pmd.xml')
        )
      }
        failure{
          error "Test failure. Stopping pipeline execution!"
        }
        cleanup{
          script{
            sh 'sudo rm -rf target/'
          }
        }        
      }
    }

    stage('Start RS-Proxy for Integration Testing'){
      steps{
        script{
            sh 'scp src/test/resources/ADEX-Resource-Proxy-Server-Consumer-APIs.postman_collection.json jenkins@jenkins-master:/var/lib/jenkins/iudx/rs-proxy/Newman/'
            sh 'docker compose -f docker-compose.test.yml up -d integTest'
            sh 'sleep 30'
        }
      }
      post{
        failure{
          script{
            sh 'docker compose -f docker-compose.test.yml down --remove-orphans'
          }
        }
      }
    }
    

    stage('Integration Tests and OWASP ZAP pen test'){
      steps{
        node('built-in') {
          script{
            startZap ([host: '0.0.0.0', port: 8090, zapHome: '/var/lib/jenkins/tools/com.cloudbees.jenkins.plugins.customtools.CustomTool/OWASP_ZAP/ZAP_2.11.0'])
            sh 'curl http://0.0.0.0:8090/JSON/pscan/action/disableScanners/?ids=10096'
            sh 'HTTP_PROXY=\'127.0.0.1:8090\' newman run /var/lib/jenkins/iudx/rs-proxy/Newman/ADEX-Resource-Proxy-Server-Consumer-APIs.postman_collection.json -e /home/ubuntu/configs/5.6.0/rs-proxy-postman-env.json -n 2 --insecure -r htmlextra --reporter-htmlextra-export /var/lib/jenkins/iudx/rs-proxy/Newman/report/report.html --reporter-htmlextra-skipSensitiveData'
            runZapAttack()
            }
        }
      }
      post{
        always{
          node('built-in') {
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: '/var/lib/jenkins/iudx/rs-proxy/Newman/report/', reportFiles: 'report.html', reportTitles: '', reportName: 'Integration Test Report'])
            script{
              archiveZap failHighAlerts: 1, failMediumAlerts: 1, failLowAlerts: 1
            } 
          }
        }
        failure{
          error "Test failure. Stopping pipeline execution!"
        }
        cleanup{
          script{
            sh 'docker compose -f docker-compose.test.yml down --remove-orphans'
          } 
        }
      }
    }

    stage('Push Images') {
      when {
        allOf {
          anyOf {
            changeset "docker/**"
            changeset "docs/**"
            changeset "pom.xml"
            changeset "src/main/**"
            triggeredBy cause: 'UserIdCause'
          }
          expression {
            return env.GIT_BRANCH == 'origin/5.6.0';
          }
        }
      }
  	steps {
    	  script {
          docker.withRegistry( registryUri, registryCredential ) {
        	devImage.push("5.6.0-${env.GIT_HASH}")
        	deplImage.push("5.6.0-${env.GIT_HASH}")
          }
    	  }
  	}
    }

  }
  post{
    failure{
      script{
        if (env.GIT_BRANCH == 'origin/5.6.0')
        emailext recipientProviders: [buildUser(), developers()], to: '$RS_PROXY_RECIPIENTS, $DEFAULT_RECIPIENTS', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!', body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.'''
      }
    }
  }
}
