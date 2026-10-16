#!/bin/sh
# Builds the example and opens it in the booted iOS simulator.
# Usage: ./run-simulator.sh <widget-key> [server]   e.g. ./run-simulator.sh wk_... https://helpium.test
# For a local Herd server, trust its certificate once:
#   xcrun simctl keychain booted add-root-cert "$HOME/Library/Application Support/Herd/config/valet/CA/LaravelValetCASelfSigned.pem"
set -e
cd "$(dirname "$0")"
xcodebuild -project HelpiumExample.xcodeproj -scheme HelpiumExample -destination 'generic/platform=iOS Simulator' -derivedDataPath .build build -quiet
xcrun simctl install booted .build/Build/Products/Debug-iphonesimulator/HelpiumExample.app
xcrun simctl terminate booted io.helpium.example 2>/dev/null || true
SIMCTL_CHILD_HELPIUM_WIDGET_KEY="$1" SIMCTL_CHILD_HELPIUM_BASE_URL="${2:-https://helpium.io}" xcrun simctl launch booted io.helpium.example
