pipeline {
    agent { label 'android' }

    stages {
        stage('Environment/version diagnostics') {
            steps {
                sh './gradlew --version'
            }
        }

        stage('Formatting/static analysis') {
            steps {
                echo 'Spotless formatting gate. Detekt and coverage remain deferred.'
                sh '''
                    set -eu
                    # Jenkins PR-merge checkouts may not have origin/develop.
                    git fetch --no-tags origin +refs/heads/develop:refs/remotes/origin/develop
                    git rev-parse --verify 'refs/remotes/origin/develop^{commit}' >/dev/null
                    git merge-base HEAD origin/develop >/dev/null
                    ./gradlew spotlessCheck
                '''
            }
        }

        stage('Unit tests') {
            steps {
                sh './gradlew testDebugUnitTest'
            }
        }

        stage('Android lint') {
            steps {
                sh './gradlew lint'
            }
        }

        stage('assembleDebug') {
            steps {
                sh './gradlew assembleDebug'
            }
        }

        stage('Navidrome integration preflight') {
            when {
                expression {
                    !env.CHANGE_FORK
                }
            }
            steps {
                sh './integration/navidrome/lifecycle.sh preflight'
            }
        }

        stage('Synthetic Navidrome integration') {
            when {
                expression {
                    !env.CHANGE_FORK
                }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh './integration/navidrome/run-integration.sh'
                }
            }
        }

        stage('archive APK and test/lint reports') {
            steps {
                echo 'Artifacts are archived in the post block.'
            }
        }
    }

    post {
        always {
            junit(
                allowEmptyResults: true,
                testResults: 'app/build/test-results/testDebugUnitTest/**/*.xml'
            )
            junit(
                allowEmptyResults: true,
                testResults: 'app/build/test-results/navidromeIntegrationTest/**/*.xml'
            )
            archiveArtifacts(
                allowEmptyArchive: true,
                artifacts: 'app/build/outputs/apk/debug/*.apk,app/build/test-results/**/*.xml,app/build/reports/lint-results-*.xml,app/build/reports/lint-results-*.html,app/build/reports/lint-results-*.sarif'
            )
        }
    }
}
