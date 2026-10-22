#!/bin/bash
# prime-watcher.sh - remap the Xiaomi remote's Prime Video button to Jellyfin.
#
# Why this exists: on the Mi TV Stick FHD the Prime button is Linux
# KEY_YELLOW (scancode 405) and has NO entry in the vendor keylayout, so the
# key dies in the kernel before any Android app can see it. Button-remapper
# apps therefore cannot catch it. This watcher runs on a small always-on
# Linux box on the same LAN (a mini PC, a Raspberry Pi, ...), holds a
# persistent `adb shell -tt` session to the stick, watches the remote's
# input device with getevent, and launches Jellyfin when the button goes
# down.
#
# Notes from production use:
# - The `-tt` (PTY) is mandatory: without it getevent output is buffered
#   and key presses are lost.
# - The watcher re-resolves the stick's IP by MAC when DHCP moves it.
# - Runs happily as a systemd user service; enable linger so it survives
#   logouts:  loginctl enable-linger $USER
#
# Required environment:
#   STICK_MAC   MAC address of the stick (Settings > About, or your router)
# Optional:
#   STICK_IP    last known IP (re-resolved by MAC when it stops answering)
#   LAN_PREFIX  first three octets of your LAN for the fallback sweep
#   ADB         adb binary to use (default: adb from PATH)
#   ADB_VENDOR_KEYS  directory with the adb keys the stick has authorised

set -u
ADB="${ADB:-adb}"
MAC="${STICK_MAC:?set STICK_MAC to the stick's MAC address}"
IP="${STICK_IP:-}"
LAN_PREFIX="${LAN_PREFIX:-192.168.1}"

find_stick() {
  local found
  found=$(ip neigh | grep -i "$MAC" | awk '{print $1}' | head -1)
  if [ -z "$found" ]; then
    for i in $(seq 1 254); do (ping -c1 -W1 "$LAN_PREFIX.$i" >/dev/null 2>&1 &); done
    sleep 8
    found=$(ip neigh | grep -i "$MAC" | awk '{print $1}' | head -1)
  fi
  [ -n "$found" ] && IP="$found"
}

while true; do
  if [ -z "$IP" ] || ! timeout 10 $ADB connect "$IP:5555" 2>/dev/null | grep -q "connected"; then
    find_stick
    [ -n "$IP" ] && timeout 10 $ADB connect "$IP:5555" >/dev/null 2>&1
  fi
  if [ -n "$IP" ]; then
    echo "[prime-watcher] attaching to $IP $(date '+%F %T')"
    $ADB -s "$IP:5555" shell -tt 'getevent -lt /dev/input/event2 | while IFS= read -r l; do case "$l" in *KEY_YELLOW*DOWN*) am start -n org.jellyfin.androidtv/.ui.startup.StartupActivity >/dev/null 2>&1; sleep 1;; esac; done'
  fi
  echo "[prime-watcher] session ended, retrying in 5s"
  sleep 5
done
