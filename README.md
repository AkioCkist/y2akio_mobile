# Y2Akio Mobile

Y2Akio Mobile is an Android media downloader built with Kotlin and Jetpack Compose. It uses an embedded `yt-dlp` runtime and FFmpeg to analyze links, download video or audio, process playlists, and save supported social-media galleries.

> Use Y2Akio only for media that you own or are authorized to download. You are responsible for complying with copyright law, the source website's terms, and applicable local law.

## Features

- Analyze media links and choose video or audio output.
- Select resolution, container, codec, audio format, and audio bitrate.
- Download selected playlist entries in a batch.
- Select media from supported photo/gallery posts and save them as a folder or ZIP archive.
- Download subtitles, including manual and automatically generated tracks when the source provides them.
- Embed metadata and thumbnails, or save thumbnails as separate files.
- Customize filenames with date/time and channel-name prefixes.
- Run downloads in a foreground service with progress and completion notifications.
- Start a download from Android's share sheet without opening the main UI.
- Keep a local download history with open, share, copy-path, and delete actions.
- Import an encrypted Netscape-format `cookies.txt` file for content that requires an authenticated session.
- Switch between English and Vietnamese, and use light, dark, or system theme.
- Update the bundled `yt-dlp` component from the Settings screen.

The normal `yt-dlp` path supports websites handled by the installed `yt-dlp` version. Y2Akio also contains dedicated gallery handling for TikTok photo posts, X photo posts, Facebook photo posts and stories, and Instagram posts and stories. Website changes can temporarily break individual extractors.

## Requirements

### To run the app

- Android 14 or newer (API 34+).
- An internet connection.
- Free storage for the embedded runtime, temporary download files, and final media.

### To build the app

- JDK 17.
- Android SDK with API 37 installed.
- A recent Android Studio version compatible with Android Gradle Plugin 9.4.1, or the included Gradle wrapper.

## Build and install

Clone the repository, open it in Android Studio, allow Gradle synchronization to finish, then run the `app` configuration on an API 34+ device.

From PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

From macOS or Linux:

```bash
./gradlew assembleDebug
```

The project creates a separate APK for each supported ABI:

- `arm64-v8a` for most modern physical Android devices.
- `armeabi-v7a` for older 32-bit ARM devices.
- `x86_64` for compatible emulators and devices.

Debug APKs are written to `app/build/outputs/apk/debug/`. For example, most current phones use:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Install it with Android Debug Bridge:

```powershell
adb install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

Build optimized APKs with:

```powershell
.\gradlew.bat assembleRelease
```

Release APKs are written to `app/build/outputs/apk/release/`.

> The current `release` build is intentionally signed with the debug key so it can be sideloaded during development. Configure a private production signing key before publishing or distributing production builds.

## First launch

On first launch, the app initializes and extracts the embedded Python, `yt-dlp`, and FFmpeg runtime. This is approximately 30 MB and can take a few seconds depending on the device. The splash screen remains visible while the runtime is prepared, with a timeout fallback so startup cannot be blocked indefinitely.

Android may also request notification permission. Granting it is recommended because active background downloads and completion status are reported through notifications.

For complete usage instructions, see the [User Guide](docs/USER_GUIDE.md).

## Project structure

```text
app/src/main/java/com/example/y2akio_mobile/
├── download/   Download queue, foreground service, and notifications
├── model/      Media, playlist, gallery, request, and history models
├── service/    yt-dlp runtime plus site and gallery resolvers
├── settings/   Preferences, localization, and encrypted cookies
├── storage/    MediaStore/SAF output and local download history
├── ui/         Compose screens and reusable UI components
├── MainActivity.kt
├── ShareReceiverActivity.kt
└── Y2AkioApp.kt
```

The application initializes shared services in `Y2AkioApp`. `MainActivity` hosts the Compose UI and waits for the media runtime during the splash screen. `DownloadManager` owns the queue, while `DownloadService` keeps active work alive as a foreground data-sync service.

## Storage and privacy

- The default output location is `Download/Y2Akio`.
- Custom video and audio folders are selected through Android's Storage Access Framework; the app does not request broad storage access.
- Imported cookies are encrypted with AES-256-GCM using a key stored in Android Keystore.
- A plaintext cookie file is created only in private app storage for an active analyze/download operation and is deleted afterward. Leftovers from an interrupted process are cleaned on the next startup.
- Download history and preferences are stored locally on the device.

Cookies are sensitive account credentials. Import only a file you created yourself, never share it, and remove it from Settings when it is no longer needed.

## Tests

Run local unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Live gallery resolver tests are disabled by default because they contact real websites. Enable them explicitly when needed:

```powershell
$env:Y2AKIO_LIVE = "1"
.\gradlew.bat testDebugUnitTest --tests "com.example.y2akio_mobile.service.GalleryResolverLiveTest"
```

Live tests can fail when sample posts disappear, authentication expires, rate limits apply, or a website changes its markup or API.

## Main technologies

- Kotlin and Kotlin Coroutines
- Jetpack Compose and Material 3
- AndroidX Lifecycle, SplashScreen, DocumentFile, and ProfileInstaller
- `youtubedl-android` with embedded `yt-dlp` and FFmpeg
- OkHttp and Coil

## Known constraints

- There is no universal APK; install the APK matching the device ABI.
- A source website may change without notice and require a `yt-dlp` update or an app code change.
- Private, age-restricted, regional, or login-only media may require valid imported cookies and may still be unavailable.
- Selecting a non-automatic video codec can require re-encoding, which is slower and uses considerably more battery.
- YouTube downloads intentionally do not receive an imported whole-browser cookie jar because it can trigger bot protection in the current extraction flow.

## License

This repository does not currently include a license file. Add an appropriate license before redistributing the source or publishing derivative builds.
