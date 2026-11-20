# Block Builder

A small voxel building sandbox for Android, written in plain Java with OpenGL ES 2.0 (no libraries).

**Download:** [`apk/BlockBuilder.apk`](apk/BlockBuilder.apk). Open it on an Android 5.0+ phone and allow
"install unknown apps" when asked.

## Features
- 128 × 128 × 64 world with procedural hills, mountains with snow, beaches, lakes and trees
- 20 placeable blocks, including grass, stone, wood, glass, brick, water and 8 colored blocks
- Ambient-occlusion lighting, distance fog, translucent water and an underwater tint
- Walking with auto-jump, swimming, and a fly mode
- Your world is saved automatically when you leave the app

## Controls
| Action | Touch |
| --- | --- |
| Move | Drag on the left side of the screen (a joystick appears) |
| Look | Drag on the right side |
| Build / break | Tap on the right side (the BUILD/BREAK button switches modes) |
| Break quickly | Hold a finger still on the right side |
| Jump / swim up | JUMP button |
| Fly | FLY button (then UP / DOWN) |
| Pick blocks | Tap a hotbar slot, or tap **+** to choose any block for the selected slot |
| New world | **+** → New World (tap twice to confirm) |

## Building from source
Requires JDK 17+ and the Android SDK (platform 34, build-tools 34).

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the local debug key so it can be sideloaded directly.
