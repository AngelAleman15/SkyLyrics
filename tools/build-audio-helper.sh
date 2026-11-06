#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dotnet build native/SkyLyrics.AudioBridge/SkyLyrics.AudioBridge.csproj -c Release
dotnet native/SkyLyrics.AudioBridge/bin/Release/net8.0/SkyLyrics.AudioBridge.dll --self-test
python3 tools/package-audio-helper.py
