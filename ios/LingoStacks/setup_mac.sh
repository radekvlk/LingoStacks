#!/usr/bin/env bash
#
# setup_mac.sh - Generates LingoStacks Xcode project on macOS
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 [LingoStacks iOS] Pripravuji Xcode projekt..."

# Check if XcodeGen is installed, if not, install via Homebrew
if ! command -v xcodegen &> /dev/null; then
    echo "📦 XcodeGen nebyl nalezen. Instaluji pres Homebrew..."
    if command -v brew &> /dev/null; then
        brew install xcodegen
    else
        echo "❌ Homebrew neni nainstalovan. Prosim nainstalujte XcodeGen: brew install xcodegen"
        exit 1
    fi
fi

# Generate Xcode project
echo "🔨 Generuji LingoStacks.xcodeproj..."
xcodegen generate

echo "✅ Projekt LingoStacks.xcodeproj byl uspesne vygenerovan!"
echo "📱 Oteviram v Xcode..."
open LingoStacks.xcodeproj
