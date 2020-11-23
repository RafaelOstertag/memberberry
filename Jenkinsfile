pipeline {
    agent {
        label 'amd64&&kotlin&&docker&&kotlin'
    }

    triggers {
        pollSCM ''
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
               label "arm64&&docker&&java"
            }
            when {
                not {
                    anyOf {
                        branch 'master'
                        triggeredBy "TimerTrigger"
                    }
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
    }
}