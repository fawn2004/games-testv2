# Block Builder

A small voxel building sandbox for Android, written in plain Java with OpenGL ES 2.0 (no libraries).

**Download:** [`apk/BlockBuilder.apk`](apk/BlockBuilder.apk). Open it on an Android 5.0+ phone and allow
"install unknown apps" when asked.

## Features
- Title screen to create, play and delete multiple worlds (normal or flat terrain, optional seed)
- 128 × 128 × 64 world with procedural hills, mountains with snow, beaches, lakes and trees
- 64 placeable blocks in five tabs: Natural, Wood, Building, Minerals and Colors (16 colors),
  including ores, three kinds of wood, bookshelves, crates, roof tiles, metal and a glow lamp
- Worlds generate ores underground, gravel and clay lake beds, and four kinds of trees
- Ambient-occlusion lighting, distance fog, translucent water and an underwater tint
- Walking with auto-jump, swimming, and a fly mode
- Worlds are saved automatically when you go back to the menu or leave the app

## Controls
| Action | Touch |
| --- | --- |
| Move | Drag on the left side of the screen (a joystick appears) |
| Look | Drag on the right side |
| Build / break | Tap on the right side (the BUILD/BREAK button switches modes) |
| Break quickly | Hold a finger still on the right side |
| Jump / swim up | JUMP button |
| Fly | FLY button (then UP / DOWN) |
| Pick blocks | Tap a hotbar slot, or tap **+** to choose any block (by tab) for the selected slot |
| Back to the world list | MENU button (top-left) or the phone's Back button |

## Building from source
Requires JDK 17+ and the Android SDK (platform 34, build-tools 34).

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the local debug key so it can be sideloaded directly.
