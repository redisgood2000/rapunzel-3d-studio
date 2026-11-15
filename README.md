# Rapunzel 3D Studio

A native Android character viewer and studio prototype.

## GLB model viewer

The app now opens an interactive GLB viewer. Tap **IMPORT GLB MODEL(S)** and choose one or more `.glb` files from your device. Imported models appear as selectable character buttons; tap a button to switch between them.

- Drag to orbit the model.
- Pinch to zoom and use two fingers to pan.
- Reset the camera or toggle auto-rotation.
- Import multiple GLB models in one picker operation.
- Models are loaded locally from the selected document URI; the app does not upload them.

The viewer uses Google's open-source `model-viewer` web component from its CDN, so an internet connection is required to load the viewer engine. The GLB model data itself is read from the device and remains local.

## Add Rapunzel and Flynn

Download the GLB files from the ChatGPT conversation to your Android device, then open the app and import both files together:
- `rapunzel_stylized_3d.glb`
- `flynn_rider_stylized_3d.glb`

Use the character buttons under the toolbar to switch between them.

## Build

Open this repository in Android Studio or run:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main` and manual workflow dispatch triggers GitHub Actions. Download the APK from the workflow run's Artifacts section.
