#!/usr/bin/env bash
# Eclipsia dev test server — Linux/macOS launcher
# Скачайте paper-1.20.4-XXX.jar с https://papermc.io/downloads/paper и положите в эту папку как paper.jar.

set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f paper.jar ]]; then
    echo "ОШИБКА: paper.jar не найден в $(pwd)" >&2
    echo "Скачайте Paper 1.20.4: https://papermc.io/downloads/paper" >&2
    exit 1
fi

# Aikar's flags — оптимизация G1GC для Minecraft серверов.
exec java \
    -Xms4G -Xmx4G \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 \
    -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -Dusing.aikars.flags=https://mcflags.emc.gs \
    -Daikars.new.flags=true \
    -jar paper.jar --nogui
