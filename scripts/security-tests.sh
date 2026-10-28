#!/bin/zsh
# Runs the security regression tests as JVM unit tests on this computer (no phone needed).
# Usage: scripts/security-tests.sh
set -u
cd "$(dirname "$0")/.."
JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/jdk-21.0.13+9/Contents/Home}" \
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" \
  ./gradlew --console=plain \
    :app:testDebugUnitTest --tests 'app.meldeplanner.android.SecurityTest' \
    :integration:drive:testDebugUnitTest --tests 'app.meldeplanner.android.data.DriveSyncTest'
code=$?
echo "Reports: app/build/reports/tests/testDebugUnitTest/index.html"
echo "         integration/drive/build/reports/tests/testDebugUnitTest/index.html"
exit $code
