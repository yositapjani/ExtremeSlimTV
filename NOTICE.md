# Third-party notices

## Launcher lineage

The launcher in `launcher/` descends from the Android TV makeover project by
Kulwant Nagi:

- Repository: https://github.com/kulwantnagi/android-tv-makeover
- Licence: MIT, Copyright (c) 2026 Kulwant Nagi

ExtremeSlimTV's launcher keeps that foundation and adds, among other changes:
the five-app centred mini dock, completion-based aerial video rotation, the
enlarged shadowed clock type, the Wi-Fi/Bluetooth status pills with capsule
shadows, and OLED Care (pixel-shift orbit + idle dim). The original MIT
copyright above is retained for the derived parts.

## Method

The debloat workflow (back up the package list, disable with
`pm disable-user`, install a clean launcher, keep a restore path) follows the
approach published by Gathin23/cleanyourtv
(https://github.com/Gathin23/cleanyourtv) and the wider October 2026 Android
TV debloat community. No code from that repository is included here; the
debloat profile in `profiles/` is original work verified on the author's own
device, and was also contributed upstream in that project's list format.
