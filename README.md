# SaveFromIfunny

Android share-target app for saving iFunny posts to your device gallery.

## What it does

- Adds **Save from iFunny** to the Android share sheet
- Saves shared iFunny videos to your gallery
- Saves shared iFunny images to your gallery
- Crops the bottom 20px from saved images

## Install

Build the debug APK in Android Studio with **Build -> Build APK(s)**, or run:

`./gradlew :app:assembleDebug`

The APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

Install that APK on your Android device and allow app installation from your chosen source if Android prompts for it.

## Use

1. Open a post in iFunny.
2. Tap **Share**.
3. Choose **Save from iFunny**.
4. Wait for the confirmation message.
5. Check your gallery or Photos app for the saved file.

## Notes

- Video posts are saved as videos.
- Image posts are saved as images with the bottom 20px removed.
- If a post cannot be resolved from the share action, the app will show an error message instead of saving anything.
