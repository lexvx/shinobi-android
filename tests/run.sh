#!/usr/bin/env bash
# Pure JVM contract tests. API test binds an ephemeral localhost HTTP port.
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
jdk_dir=${SHINOBI_TEST_JDK:-${JAVA_HOME:-/tmp/shinobi-android-tools/jdk}}
json_jar=${SHINOBI_TEST_JSON_JAR:-/tmp/shinobi-json-test.jar}
android_jar=${SHINOBI_TEST_ANDROID_JAR:-${ANDROID_HOME:-/tmp/shinobi-android-tools/sdk}/platforms/android-36/android.jar}
for dependency in "$jdk_dir/bin/javac" "$jdk_dir/bin/java" "$json_jar" "$android_jar"; do
    if [[ ! -f "$dependency" ]]; then
        echo "Missing test dependency: $dependency" >&2
        echo 'Set SHINOBI_TEST_JDK, SHINOBI_TEST_JSON_JAR and SHINOBI_TEST_ANDROID_JAR as needed.' >&2
        exit 1
    fi
done
classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/shinobi-tests.XXXXXX")
trap 'rm -rf -- "$classes_dir"' EXIT
sources_dir="$project_dir/app/src/main/java/ru/lex42/shinobiviewer"
classpath="$json_jar:$android_jar"
"$jdk_dir/bin/javac" -cp "$classpath" -d "$classes_dir" \
    "$sources_dir/ApiClient.java" "$sources_dir/ArchiveVariants.java" \
    "$sources_dir/ArchiveTimeline.java" "$sources_dir/MediaFiles.java" \
    "$sources_dir/CredentialsStore.java" \
    "$project_dir/tests/ApiClientTest.java" "$project_dir/tests/ArchiveTimelineTest.java" \
    "$project_dir/tests/java/ru/lex42/shinobiviewer/MediaServicesTest.java"
for test in ArchiveTimelineTest MediaServicesTest ApiClientTest; do
    "$jdk_dir/bin/java" -cp "$classes_dir:$classpath" "ru.lex42.shinobiviewer.$test"
done
