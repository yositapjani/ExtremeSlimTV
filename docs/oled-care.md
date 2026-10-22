# OLED Care — how it works

OLED burn-in is cumulative: the same pixels emitting the same bright content
for hours wear unevenly. On a TV home screen the worst offenders are not the
background (video moves) but the fixed furniture — dock tiles with saturated
brand artwork, a big white clock, status icons.

High-end OLED TVs fight this at panel level ("pixel shift"). Many panels,
monitors and older sets don't, or have it switched off. ExtremeSlimTV's
launcher adds the same idea at UI level, for any Android TV device it runs
on:

## Pixel-shift orbit

- The entire UI layer (clock block, status pills, dock, app grid) is one
  container; the video/gradient background is a sibling *under* it and never
  moves.
- Every **4 minutes** the container glides to the next point of an 8-step
  orbit: `(0,0) (2,1) (4,0) (2,-1) (0,-2) (-2,-1) (-4,0) (-2,1)` in dp —
  a ±8 px horizontal / ±4 px vertical envelope at the stick's density —
  animated over 0.8 s with a decelerate curve.
- Result: no UI element occupies the same pixels for longer than 4 minutes,
  and the motion is far too small and slow to notice while watching.

## Idle dim

- After **3 minutes** without a remote key press (tracked in
  `dispatchKeyEvent`), the UI layer fades to **65% alpha** over 0.6 s.
- Any key press restores 100% in 0.2 s. Returning to Home also resets it.

## Verification

A test build (`CARE_TEST = true` in `HomeActivity`) shortens the tick to
10 s, the idle limit to 20 s and multiplies the orbit by 5. Photographing
the home screen twice, 50 s apart, and template-matching the Netflix tile
between the two frames measured a clean **+20 px vertical step** — exactly
one amplified orbit step — with the dim state visible in the same frames.
Production builds ship with `CARE_TEST = false`.

## Limits

Launcher-level care only covers the launcher's own screen. In-app UI
(Netflix menus and the like) is each app's responsibility — leave your TV's
built-in pixel shift on as well if it has one.
