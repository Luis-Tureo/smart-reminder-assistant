#!/bin/sh

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild no esta disponible. Instala Xcode Command Line Tools."
  exit 1
fi

sh "$SCRIPT_DIR/generate_xcode_project.sh"

cd "$SCRIPT_DIR"
xcodebuild \
  -project iosApp.xcodeproj \
  -scheme iosApp \
  -destination "generic/platform=iOS Simulator" \
  build
