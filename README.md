# Nova Tuner

Per-app performance tuner for **non-rooted** Android phones. Uses [Shizuku](https://shizuku.rikka.app/) for shell-level access. Native Kotlin, no WebView.

Only apps you add are touched; every tweak is stored per package and can be restored.

## Per-app tweaks
- Game Mode (standard / performance / battery) via `cmd game`
- Resolution scale and FPS cap via `cmd game set`
- ANGLE renderer selection for that package only
- Standby bucket, Doze whitelist, optional background kill
- AOT compile (speed-profile / speed)
- Built-in shell (Shizuku shell user)

## Use
1. Install and start Shizuku (wireless debugging or adb).
2. Open Nova Tuner, grant the Shizuku permission.
3. Add a game (e.g. CODM), set options, tap **Apply & Launch**.

## Build

The APK is signed with the committed `app/nova.jks` so updates install over each other (hobby key, not for store release).

## Optional art slots
Drop PNGs with these exact names into `app/src/main/res/drawable-nodpi/` and the app picks them up automatically:
`bg_main`, `bg_layer`, `hero_art`, `hero_overlay`, `overlay_orb`, `ic_launcher`.
