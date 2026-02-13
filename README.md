# SaveFromIfunny

Minimal Android share-target app that tries to save a video shared from iFunny.

## How it works

- Appears in the Android share sheet as **Save from iFunny**
- Receives `ACTION_SEND` for `video/*` (and `text/*` for diagnostics)
- If it gets a `content://` stream (`EXTRA_STREAM` or `ClipData`), it copies it to:
	- Android 10+ (API 29+): `Movies/iFunny/` via `MediaStore` (no storage permission)
	- Android 9 and below: public `Movies/iFunny/` (requires storage permission)

## Build

Open in Android Studio and use **Build → Build APK(s)**, or run:

`./gradlew :app:assembleDebug`

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Notes

This only works if iFunny shares real media bytes/URIs to normal share targets. If iFunny is Snapchat-only, you may only receive text/links (see Logcat tag `IFunnySaver`).
