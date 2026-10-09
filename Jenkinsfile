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

        stage('assembleDebugAndroidTest') {
            steps {
                // PR and branch compilation; no release credentials.
                sh './gradlew :app:assembleDebugAndroidTest'
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

                        rm -rf \
                            app/build/outputs/apk/release \
                            app/build/outputs/bundle/release \
                            app/build/outputs/release-sha256.txt

                        ./gradlew assembleRelease bundleRelease

                        APK="$(
                            find app/build/outputs/apk/release \
                                -maxdepth 1 \
                                -type f \
                                -name '*-release.apk' \
                                | sort \
                                | head -n 1
                        )"

                        AAB="$(
                            find app/build/outputs/bundle/release \
                                -maxdepth 1 \
                                -type f \
                                -name '*-release.aab' \
                                | sort \
                                | head -n 1
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
                            find "$SDK_ROOT/build-tools" \
                                -type f \
                                -name apksigner \
                                2>/dev/null \
                                | sort -V \
                                | tail -n 1
                        )"

                        [ -n "$APKSIGNER" ] || {
                            echo 'RELEASE_APK_SIGNATURE=FAIL_TOOL_MISSING'
                            exit 1
                        }

                        command -v jarsigner >/dev/null 2>&1 || {
                            echo 'RELEASE_AAB_SIGNATURE=FAIL_TOOL_MISSING'
                            exit 1
                        }

                        if ! "$APKSIGNER" verify "$APK" >/dev/null; then
                            echo 'RELEASE_APK_SIGNATURE=FAIL_VERIFY'
                            exit 1
                        fi

                        AAB_VERIFY="$(
                            LC_ALL=C jarsigner -verify "$AAB" 2>&1
                        )" || {
                            echo 'RELEASE_AAB_SIGNATURE=FAIL_VERIFY'
                            exit 1
                        }

                        printf '%s\n' "$AAB_VERIFY" \
                            | grep -Fq 'jar verified.' || {
                                echo 'RELEASE_AAB_SIGNATURE=FAIL_UNSIGNED'
                                exit 1
                            }

                        unset AAB_VERIFY

                        APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
                        AAB_SHA="$(sha256sum "$AAB" | awk '{print $1}')"

                        {
                            printf '%s  %s\n' \
                                "$APK_SHA" \
                                "$(basename "$APK")"
                            printf '%s  %s\n' \
                                "$AAB_SHA" \
                                "$(basename "$AAB")"
                        } > app/build/outputs/release-sha256.txt

                        echo 'RELEASE_APK_SIGNATURE=PASS'
                        echo 'RELEASE_AAB_SIGNATURE=PASS'
                        echo 'RELEASE_PROVENANCE_SHA256=PASS'
                        echo 'RELEASE_SIGNING=PASS'
                    '''
                }

                archiveArtifacts(
                    artifacts: 'app/build/outputs/apk/release/*.apk,app/build/outputs/bundle/release/*.aab,app/build/outputs/release-sha256.txt',
                    fingerprint: true
                )
            }
        }

        stage('RC smoke signed instrumentation (trusted opt-in)') {
            // Only a separately provisioned signing worker may use this label.
            // A Jenkins label alone does not provide security isolation.
            agent { label 'android-signing' }

            when {
                beforeAgent true
                expression {
                    !env.CHANGE_ID &&
                        env.BRANCH_NAME == 'main' &&
                        env.DEVDIGI_RC_SMOKE_SIGNING_ENABLED == 'true'
                }
            }
            steps {
                // Rebuild on the trusted signing worker before credentials.
                // Never sign APKs transferred from shared PR workspaces.
                sh './gradlew :app:assembleDebugAndroidTest'

                // Clear generated test output, never the approved RC.
                sh 'rm -rf -- app/build/outputs/rc-smoke'

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
                    ),
                    file(
                        credentialsId: 'android-rc1-approved-apk',
                        variable: 'DEVDIGI_RC_REFERENCE_APK'
                    ),
                    string(
                        credentialsId: 'android-rc1-approved-sha256',
                        variable: 'DEVDIGI_RC_APPROVED_SHA256'
                    ),
                    string(
                        credentialsId: 'android-rc1-test-approved-commit',
                        variable: 'DEVDIGI_RC_APPROVED_TEST_SOURCE_SHA'
                    )
                ]) {
                    sh './tools/sign-rc-test-apk.sh'
                }

                // Only non-secret checksum/provenance receipts may leave the worker.
                archiveArtifacts(
                    fingerprint: true,
                    artifacts: 'app/build/outputs/rc-smoke/*.txt'
                )
            }
            post {
                always {
                    // Instrumentation shares the release signer: no public artifact.
                    sh 'rm -f -- app/build/outputs/rc-smoke/*.apk'
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
                artifacts: 'app/build/outputs/apk/debug/*.apk,app/build/test-results/**/*.xml,app/build/reports/lint-results-*.xml,app/build/reports/lint-results-*.html'
            )
        }
    }
}
