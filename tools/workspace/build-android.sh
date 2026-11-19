#!/usr/bin/env bash
# Pinned Gradle launcher for the upstream repository, which omitted the wrapper JAR.
set -euo pipefail
project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../Source/android" && pwd)
toolchain_dir="${STORMVR_TOOLCHAIN:-$HOME/Android/stormvr}"
if [[ -f "$toolchain_dir/environment.sh" ]]; then
    source "$toolchain_dir/environment.sh"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$toolchain_dir/gradle-cache}"
gradle_bin="$toolchain_dir/gradle-8.7/bin/gradle"
if [[ ! -x "$gradle_bin" ]]; then
    printf 'Gradle 8.7 não está instalado. Execute: python tools/workspace/bootstrap-android.py --accept-licenses\n' >&2
    exit 2
fi
exec "$gradle_bin" -p "$project_dir" --max-workers=2 "$@"
