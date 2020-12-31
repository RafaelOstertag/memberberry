pipeline {
    agent {
        label 'amd64&&kotlin&&docker'
    }

    triggers {
        pollSCM '@hourly'
        cron '@daily'
    }

    tools {
        maven 'Latest Maven'
    }

    options {
        ansiColor('xterm')
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')
        timestamps()
    }

    stages {
        stage('Clean') {
            steps {
                sh 'mvn -B clean'
            }
        }

        stage('Build and Test') {
            steps {
                sh 'mvn -B install'
            }
        }

        stage('Sonarcloud') {
            steps {
                withCredentials([string(credentialsId: 'e8795d01-550a-4c05-a4be-41b48b22403f', variable: 'accessToken')]) {
                    sh label: 'sonarcloud', script: "mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.login=$accessToken"
                }
            }
        }

        stage('Publish test results') {
            steps {
                junit '**/failsafe-reports/*.xml,**/surefire-reports/*.xml'
            }
        }

        stage('Deploy to Nexus') {
            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                configFileProvider([configFile(fileId: 'b958fc4b-b1bd-4233-8692-c4a26a51c0f4', variable: 'MAVEN_SETTINGS_XML')]) {
                    sh 'mvn -B -s "$MAVEN_SETTINGS_XML" -DskipTests deploy'
                }
            }
        }

        stage('Build & Push Development Docker Image') {
            agent {
               label "arm64&&docker&&kotlin"
            }
            when {
                branch 'develop'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                sh "mvn clean package -DskipTests -Dquarkus.package.type=fast-jar"
                sh "docker build -t rafaelostertag/memberberry:latest -f src/main/docker/Dockerfile.fast-jar ."
                withCredentials([usernamePassword(credentialsId: '750504ce-6f4f-4252-9b2b-5814bd561430', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    sh 'docker login --username "$USERNAME" --password "$PASSWORD"'
                    sh "docker push rafaelostertag/memberberry:latest"
                }
            }
        }

        stage('Build & Push Release Docker Image') {
            agent {
                label "arm64&&docker&&kotlin"
            }

            environment {
                VERSION = sh returnStdout: true, script: "mvn -B help:evaluate '-Dexpression=project.version' | grep -v '\\[' | tr -d '\\n'"
            }

            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                sh "mvn clean package -DskipTests -Dquarkus.package.type=fast-jar"
                sh "docker build -t rafaelostertag/memberberry:${env.VERSION} -f src/main/docker/Dockerfile.fast-jar ."
                withCredentials([usernamePassword(credentialsId: '750504ce-6f4f-4252-9b2b-5814bd561430', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    sh 'docker login --username "$USERNAME" --password "$PASSWORD"'
                    sh "docker push rafaelostertag/memberberry:${env.VERSION}"
                }
            }
        }

        stage('Deploy to k8s') {
            agent {
                label "helm"
            }

            environment {
                VERSION = sh returnStdout: true, script: "mvn -B help:evaluate '-Dexpression=project.version' | grep -v '\\[' | tr -d '\\n'"
            }

            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                withKubeConfig(credentialsId: 'a9fe556b-01b0-4354-9a65-616baccf9cac') {
                    sh "helm upgrade -n memberberry -i --set image.tag=${env.VERSION} memberberry helm/memberberry"
                }
            }
        }
    }

    post {
        unsuccessful {
            mail to: "rafi@guengel.ch",
                    subject: "${JOB_NAME} (${BRANCH_NAME};${env.BUILD_DISPLAY_NAME}) -- ${currentBuild.currentResult}",
                    body: "Refer to ${currentBuild.absoluteUrl}"
        }
    }
}