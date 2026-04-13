#!/bin/sh

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen no esta instalado. Instala XcodeGen antes de continuar."
  exit 1
fi

cd "$SCRIPT_DIR"
xcodegen generate

echo "Proyecto generado: $SCRIPT_DIR/iosApp.xcodeproj"
