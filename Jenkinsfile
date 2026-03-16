#!groovy

/*
 * Copyright (C) 2026 by Sebastian Hasait (sebastian at hasait dot de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '', numToKeepStr: '20')),
        parameters([
                string(name: 'releaseVersion', defaultValue: '', description: 'Release version - if set a release will be build, otherwise normal CI'),
                string(name: 'developmentVersion', defaultValue: '', description: 'Next development version _without_ SNAPSHOT - if set POMs will be updated after build'),
                string(name: 'gitUserName', defaultValue: 'ciserver', description: 'Git user name'),
                string(name: 'gitUserEmail', defaultValue: 'ciserver@hasait.de', description: 'Git user email'),
                credentials(name: 'gitPushSshCredentialId', defaultValue: '', description: 'Git SSH Credentials for release pushing', credentialType: 'com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey', required: false),
                booleanParam(name: 'forceTag', defaultValue: false, description: 'Replace an existing tag with the given name (add -f to git tag)'),
                booleanParam(name: 'mvnDebug', defaultValue: false, description: 'Produce execution debug output (add -X to mvn)'),
                booleanParam(name: 'clearMvnRepo', defaultValue: false, description: 'Clear repo before build')
        ]),
        pipelineTriggers([pollSCM('H/10 * * * *')])
])

echo """
    params.releaseVersion = ${params.releaseVersion}
    params.developmentVersion = ${params.developmentVersion}
    params.gitUserName = ${params.gitUserName}
    params.gitUserEmail = ${params.gitUserEmail}
    params.gitPushSshCredentialId = ${params.gitPushSshCredentialId}
    params.forceTag = ${params.forceTag}
    params.mvnDebug = ${params.mvnDebug}
    params.clearMvnRepo = ${params.clearMvnRepo}
""".stripIndent()

def sshAgentIfParamSet(Closure<?> block) {
    if (params.gitPushSshCredentialId) {
        sshagent([params.gitPushSshCredentialId]) {
            block.call()
        }
    }
    else {
        block.call()
    }
}

node('linux') {
    def wsHome
    def mvnRepo

    stage('Prepare') {
        wsHome = pwd()
        echo "wsHome = ${wsHome}"

        mvnRepo = "${wsHome}/.m2repo"
        echo "mvnRepo = ${mvnRepo}"
        if (params.clearMvnRepo) {
            sh "rm -rf '${mvnRepo}'"
        }
        sh "mkdir -p '${mvnRepo}'"

        sh "rm -rf 'co'"
    }

    configFileProvider([configFile(fileId: 'ciserver-settings.xml', targetLocation: 'maven-settings.xml', variable: 'mvnSettings')]) {
        dir('co') {
            def mvnOptions = "-B -U -e -s ${mvnSettings} -Dmaven.repo.local=${mvnRepo}"
            if (params.mvnDebug) {
                mvnOptions = "-X ${mvnOptions}"
            }
            mvnOptions = "${mvnOptions} -Pproduction"
            def mvnCommand = "./sdkman-mvn.sh ${mvnOptions}"
            def currentBranch
            def releaseTag

            stage('Checkout') {
                checkout scm

                sh "printenv | sort"
                sh "git config --list"
                if (env.BRANCH_NAME) {
                    currentBranch = "HEAD:${env.BRANCH_NAME}"
                } else {
                    currentBranch = sh(returnStdout: true, script: "git branch | grep \\* | cut -d ' ' -f2").trim()
                }
                echo "\u27A1 On branch ${currentBranch}..."

                if (params.releaseVersion || params.developmentVersion) {
                    echo "\u27A1 Configuring git for pushing..."
                    sh "git config user.name '${params.gitUserName}'"
                    sh "git config user.email '${params.gitUserEmail}'"
                    sh "git config --local credential.username '${params.gitUserName}'"
                }
            }

            stage('Create Release Tag') {
                if (params.releaseVersion) {
                    releaseTag = "${params.releaseVersion}"
                    def msg = "Changing POM versions to release version ${params.releaseVersion}"
                    echo "\u27A1 ${msg}..."
                    sh "${mvnCommand} release:update-versions -DautoVersionSubmodules=true -DdevelopmentVersion=${params.releaseVersion}-REMOVEME-SNAPSHOT"
                    sh "find . -type f -name pom.xml -exec sed -i -e 's/${params.releaseVersion}-REMOVEME-SNAPSHOT/${params.releaseVersion}/' \\{\\} \\;"
                    sh "find . -type f -name pom.xml -exec git add \\{\\} \\;"
                    sh "git commit -m '[Jenkinsfile] ${msg}'"
                    def gitTagOptions = params.forceTag ? '-f' : ''
                    sh "git tag ${gitTagOptions} ${releaseTag}"
                    addBadge text: "Release ${params.releaseVersion}", cssClass: 'badge-text--background'
                } else {
                    echo "\u27A1 No Release Tag requested"
                }
            }

            stage('Package') {
                echo "\u27A1 Packaging project..."
                sh "${mvnCommand} package -Dmaven.test.failure.ignore=true"
            }

            stage('Collect Test Results') {
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
            }

            stage('Archive Artifacts') {
                archiveArtifacts artifacts: '**/target/*.jar', onlyIfSuccessful: true, allowEmptyArchive: true
            }

            stage('Deploy') {
                if (params.releaseVersion) {
                    echo "\u27A1 Pushing release tag ${releaseTag}..."
                    sshAgentIfParamSet {
                        sh "git push -f origin tag ${releaseTag}"
                    }
                }
                echo "\u27A1 Deploying project..."
                sh "${mvnCommand} deploy -DskipTests=true"
            }

            stage('Change Development Version') {
                if (params.developmentVersion) {
                    def msg = "Changing POM versions to development version ${params.developmentVersion}"
                    echo "\u27A1 ${msg}..."

                    sh "${mvnCommand} release:update-versions -DautoVersionSubmodules=true -DdevelopmentVersion=${params.developmentVersion}-SNAPSHOT"
                    sh "find . -type f -name pom.xml -exec git add \\{\\} \\;"
                    sh "git commit -m '[Jenkinsfile] ${msg}'"
                    sshAgentIfParamSet {
                        sh "git push origin ${currentBranch}"
                    }
                } else {
                    echo "\u27A1 No Development Version requested"
                }
            }
        }
    }
}
