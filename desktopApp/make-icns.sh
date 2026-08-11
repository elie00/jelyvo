#!/bin/bash
# Builds a macOS .icns from Jelyvo's app icon.
#
# The icon is kept in one place — fastlane's Android launcher icon — and converted here rather than
# committed twice. sips and iconutil ship with macOS, which this module already targets exclusively.
#
# Usage: make-icns.sh <source.png> <output-dir>
set -euo pipefail

SOURCE="$1"
OUT_DIR="$2"
ICONSET="$OUT_DIR/Jelyvo.iconset"

rm -rf "$ICONSET"
mkdir -p "$ICONSET"

# name:pixels — macOS wants each size at 1x and 2x. The source is 512, so 512@2x is upscaled.
for spec in \
  icon_16x16:16 icon_16x16@2x:32 \
  icon_32x32:32 icon_32x32@2x:64 \
  icon_128x128:128 icon_128x128@2x:256 \
  icon_256x256:256 icon_256x256@2x:512 \
  icon_512x512:512 icon_512x512@2x:1024
do
  name="${spec%%:*}"
  size="${spec##*:}"
  sips -z "$size" "$size" "$SOURCE" --out "$ICONSET/$name.png" >/dev/null
done

iconutil --convert icns "$ICONSET" --output "$OUT_DIR/Jelyvo.icns"
