pipeline {
    agent { label 'android' }

    parameters {
        booleanParam(name: 'RUN_NAVIDROME_INTEGRATION', defaultValue: false, description: 'Run trusted Navidrome integration checks.')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Environment/version diagnostics') {
            steps {
                sh './gradlew --version'
            }
        }

        stage('Formatting/static analysis') {
            steps {
                sh './gradlew check'
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

        stage('Optional trusted Navidrome integration') {
            when {
                allOf {
                    branch 'main'
                    expression { !env.CHANGE_ID && params.RUN_NAVIDROME_INTEGRATION }
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'navidrome-url', variable: 'NAVIDROME_URL'),
                    usernamePassword(credentialsId: 'navidrome-test-account', usernameVariable: 'NAVIDROME_USERNAME', passwordVariable: 'NAVIDROME_PASSWORD')
                ]) {
                    sh './gradlew testDebugUnitTest -PnavidromeIntegration=true'
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
            junit allowEmptyResults: true, testResults: 'app/build/test-results/**/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'app/build/outputs/apk/debug/*.apk,app/build/test-results/**/*.xml,app/build/reports/lint-results-*.xml,app/build/reports/lint-results-*.html,app/build/reports/lint-results-*.sarif'
        }
    }
}
