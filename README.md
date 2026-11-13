# Rapunzel 3D Studio

A native Android 3D character studio prototype using Kotlin and OpenGL ES 2.0.

## App and resource update design

- The APK contains the renderer, studio UI, and a starter Rapunzel character appearance resource.
- Character appearance data is stored separately at `resources/rapunzel-character.json`.
- On launch, the app checks the hosted JSON resource over HTTPS.
- The **UPDATE** button lets the user manually check for a newer resource pack.
- A downloaded pack is schema-validated and version-checked before it replaces the local copy.
- Installed resources remain available offline; the app does not need to download them on every launch.
- Resource-only changes do not require a new APK. Renderer or app-code changes still require a new APK.

## Current features
- Original stylized princess character inspired by classic fairy-tale aesthetics.
- Drag to rotate, pinch to zoom, reset camera.
- Idle breathing/bobbing motion and arm sway.
- Smile, surprised, and wink expression presets.
- Updateable hair, skin, and dress material colors.
- Native Android UI.

## Build
Open this repository in Android Studio or run:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main` and manual workflow dispatch triggers GitHub Actions. Download the APK from the workflow run's Artifacts section.

## Current resource-pack scope
The first independently updateable pack contains versioned appearance/material settings in JSON. The 3D geometry and animation logic are still implemented in the APK renderer; this is not yet a downloadable rigged GLB/glTF model pack. The next model-resource milestone can move the character mesh, textures, skeleton, and animation clips into a separately downloadable asset bundle.
