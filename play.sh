#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
./gradlew --console=plain :app:installDist
exec app/build/install/app/bin/app "$@"
