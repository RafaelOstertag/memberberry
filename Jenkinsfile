pipeline {
    agent {
        label 'amd64&&kotlin&&docker'
    }

    triggers {
        cron '@daily'
    }

    tools {
        maven 'Latest Maven'
    }

    options {
        ansiColor('xterm')
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '15')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Build and Test') {
            steps {
                configFileProvider([configFile(fileId: 'b958fc4b-b1bd-4233-8692-c4a26a51c0f4', variable: 'MAVEN_SETTINGS_XML')]) {
                    sh 'mvn -B -s "$MAVEN_SETTINGS_XML" install'
                }
            }

            post {
                always {
                    junit '**/failsafe-reports/*.xml,**/surefire-reports/*.xml'
                    jacoco()
                }
            }
        }

        stage('Sonarcloud') {
            when {
                not {
                    triggeredBy "TimerTrigger"
                }
            }
            steps {
                configFileProvider([configFile(fileId: 'b958fc4b-b1bd-4233-8692-c4a26a51c0f4', variable: 'MAVEN_SETTINGS_XML')]) {
                    withSonarQubeEnv(installationName: 'Sonarcloud', credentialsId: 'e8795d01-550a-4c05-a4be-41b48b22403f') {
                        sh label: 'sonarcloud', script: "mvn -B -s \"$MAVEN_SETTINGS_XML\" -Dsonar.branch.name=${env.BRANCH_NAME} $SONAR_MAVEN_GOAL"
                    }
                }
            }
        }

        stage("Quality Gate") {
            when {
                not {
                    triggeredBy "TimerTrigger"
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage("Check Dependencies") {
            steps {
                configFileProvider([configFile(fileId: 'b958fc4b-b1bd-4233-8692-c4a26a51c0f4', variable: 'MAVEN_SETTINGS_XML')]) {
                    sh 'mvn -B -s "$MAVEN_SETTINGS_XML" -Psecurity-scan dependency-check:check'
                }
                dependencyCheckPublisher failedTotalCritical: 1, failedTotalHigh: 5, failedTotalLow: 8, failedTotalMedium: 8, pattern: 'target/dependency-check-report.xml', unstableTotalCritical: 0, unstableTotalHigh: 4, unstableTotalLow: 8, unstableTotalMedium: 8
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
                    sh 'mvn -B -s "$MAVEN_SETTINGS_XML" -DskipTests -Dquarkus.package.type=uber-jar clean deploy'
                }
            }
        }

        stage('Build Development Docker Image') {
            when {
                branch 'develop'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            parallel {
                stage('ARM64') {
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
                        buildDockerImage("latest-arm64")
                    }
                }

                stage('AMD64') {
                    when {
                        branch 'develop'
                        not {
                            triggeredBy "TimerTrigger"
                        }
                    }

                    steps {
                        buildDockerImage("latest-amd64")
                    }
                }
            }
        }

        stage('Build Development Multi Arch Docker Manifest') {
            when {
                branch 'develop'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                buildMultiArchManifest("latest")
            }
        }

        stage('Build Release Docker Image') {
            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            parallel {
                stage('AMD64') {
                    environment {
                        VERSION = sh returnStdout: true, script: "mvn -B help:evaluate -q -DforceStdout -Dexpression=project.version"
                    }

                    steps {
                        buildDockerImage(env.VERSION + "-amd64")
                    }
                }

                stage('ARM64') {
                    agent {
                        label "arm64&&docker&&kotlin"
                    }

                    environment {
                        VERSION = sh returnStdout: true, script: "mvn -B help:evaluate -q -DforceStdout -Dexpression=project.version"
                    }


                    steps {
                        buildDockerImage(env.VERSION + "-arm64")
                    }
                }
            }

        }

        stage('Build Production Multi Arch Docker Manifest') {
            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            environment {
                VERSION = sh returnStdout: true, script: "mvn -B help:evaluate -q -DforceStdout -Dexpression=project.version"
            }

            steps {
                buildMultiArchManifest(env.VERSION)
            }
        }

        stage('Trigger k8s deployment') {
            environment {
                VERSION = sh returnStdout: true, script: "mvn -B help:evaluate -q -DforceStdout -Dexpression=project.version"
            }

            when {
                branch 'master'
                not {
                    triggeredBy "TimerTrigger"
                }
            }

            steps {
                build wait: false, job: '../../memberberry-helm', parameters: [string(name: 'VERSION', value: env.VERSION)]
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

def buildDockerImage(String tag) {
    withEnv(['IMAGE_TAG=' + tag, 'GRAALVM_HOME=/opt/graalvm', 'JAVA_HOME=/opt/graalvm']) {
        withCredentials([usernamePassword(credentialsId: '750504ce-6f4f-4252-9b2b-5814bd561430', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            configFileProvider([configFile(fileId: 'b958fc4b-b1bd-4233-8692-c4a26a51c0f4', variable: 'MAVEN_SETTINGS_XML')]) {
                sh '''mvn -B \
                    -s "${MAVEN_SETTINGS_XML}" \\
                    clean \\
                    package \\
                    -DskipTests \\
                    -Dnative \\
                    -Dquarkus.docker.dockerfile-native-path=src/main/docker/Dockerfile.native-ubi \\
                    -Dquarkus.container-image.build=true \\
                    -Dquarkus.container-image.name=memberberry \\
                    -Dquarkus.container-image.tag="${IMAGE_TAG}" \\
                    -Dquarkus.container-image.group=rafaelostertag \\
                    -Dquarkus.container-image.push=true \\
                    -Dquarkus.container-image.username="${USERNAME}" \\
                    -Dquarkus.container-image.password="${PASSWORD}"
                    '''
            }
        }
    }
}

def buildMultiArchManifest(String tag) {
    withEnv(['IMAGE_TAG=' + tag]) {
        withCredentials([usernamePassword(credentialsId: '750504ce-6f4f-4252-9b2b-5814bd561430', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            sh 'docker login --username "$USERNAME" --password "$PASSWORD"'
            sh 'docker manifest create "rafaelostertag/memberberry:${IMAGE_TAG}" --amend "rafaelostertag/memberberry:${IMAGE_TAG}-amd64" --amend "rafaelostertag/memberberry:${IMAGE_TAG}-arm64"'
            sh 'docker manifest push --purge "rafaelostertag/memberberry:${IMAGE_TAG}"'
        }
    }
}
