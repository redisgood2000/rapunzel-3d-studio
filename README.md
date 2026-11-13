# Rapunzel 3D Studio

A native Android 3D character studio prototype using Kotlin and OpenGL ES 2.0.

## Current features
- Offline, procedurally rendered stylized princess character inspired by classic fairy-tale aesthetics.
- Drag to rotate, pinch to zoom, reset camera.
- Idle breathing/bobbing motion and arm sway.
- Smile, surprised, and wink expression presets.
- Native Android UI; no account, network permission, or external asset download required.

## Build
Open this repository in Android Studio or run:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main` and manual workflow dispatch triggers GitHub Actions. Download the APK from the workflow run's Artifacts section.

## Notes
This is an original stylized 3D approximation, not a ripped or copied Disney character model. The first build uses procedural geometry so it works offline. The renderer is a foundation for later upgrades such as a true skinned mesh, detailed hair strands, blend-shape facial animation, and glTF import.
