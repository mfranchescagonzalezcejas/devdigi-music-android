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
                sh './gradlew --no-build-cache --rerun-tasks lint'
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

        stage('Signed release artifacts') {
            when {
                expression {
                    def branchName = env.BRANCH_NAME ?: ''

                    !env.CHANGE_ID &&
                        (
                            branchName == 'main' ||
                            branchName.startsWith('release/')
                        )
                }
            }
            steps {
                withCredentials([
                    file(
                        credentialsId: 'android-release-keystore',
                        variable: 'DEVDIGI_RELEASE_STORE_FILE'
                    ),
                    string(
                        credentialsId: 'android-release-store-password',
                        variable: 'DEVDIGI_RELEASE_STORE_PASSWORD'
                    ),
                    string(
                        credentialsId: 'android-release-key-alias',
                        variable: 'DEVDIGI_RELEASE_KEY_ALIAS'
                    ),
                    string(
                        credentialsId: 'android-release-key-password',
                        variable: 'DEVDIGI_RELEASE_KEY_PASSWORD'
                    )
                ]) {
                    sh '''#!/usr/bin/env bash
                        set -eu

                        ./gradlew assembleRelease bundleRelease

                        APK="$(
                            find app/build/outputs/apk/release                                 -maxdepth 1                                 -type f                                 -name '*-release.apk'                                 | sort                                 | head -n 1
                        )"

                        AAB="$(
                            find app/build/outputs/bundle/release                                 -maxdepth 1                                 -type f                                 -name '*-release.aab'                                 | sort                                 | head -n 1
                        )"

                        [ -n "$APK" ] || {
                            echo 'RELEASE_APK=FAIL_MISSING'
                            exit 1
                        }

                        [ -n "$AAB" ] || {
                            echo 'RELEASE_AAB=FAIL_MISSING'
                            exit 1
                        }

                        SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

                        [ -n "$SDK_ROOT" ] || {
                            echo 'RELEASE_SIGNATURE_VERIFY=FAIL_ANDROID_SDK'
                            exit 1
                        }

                        APKSIGNER="$(
                            find "$SDK_ROOT/build-tools"                                 -type f                                 -name apksigner                                 2>/dev/null                                 | sort -V                                 | tail -n 1
                        )"

                        [ -n "$APKSIGNER" ] || {
                            echo 'RELEASE_APK_SIGNATURE=FAIL_TOOL_MISSING'
                            exit 1
                        }

                        command -v jarsigner >/dev/null 2>&1 || {
                            echo 'RELEASE_AAB_SIGNATURE=FAIL_TOOL_MISSING'
                            exit 1
                        }

                        "$APKSIGNER" verify "$APK" >/dev/null

                        jarsigner                             -verify                             "$AAB"                             >/dev/null 2>&1

                        APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
                        AAB_SHA="$(sha256sum "$AAB" | awk '{print $1}')"

                        {
                            printf '%s  %s\n'                                 "$APK_SHA"                                 "$(basename "$APK")"
                            printf '%s  %s\n'                                 "$AAB_SHA"                                 "$(basename "$AAB")"
                        } > app/build/outputs/release-sha256.txt

                        echo 'RELEASE_APK_SIGNATURE=PASS'
                        echo 'RELEASE_AAB_SIGNATURE=PASS'
                        echo 'RELEASE_PROVENANCE_SHA256=PASS'
                        echo 'RELEASE_SIGNING=PASS'
                    '''
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
                artifacts: 'app/build/outputs/apk/debug/*.apk,app/build/outputs/apk/release/*.apk,app/build/outputs/bundle/release/*.aab,app/build/outputs/release-sha256.txt,app/build/test-results/**/*.xml,app/build/reports/lint-results-*.xml,app/build/reports/lint-results-*.html'
            )
        }
    }
}
