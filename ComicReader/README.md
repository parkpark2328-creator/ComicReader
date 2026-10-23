# Comic Reader (Android)

A simple Android image reader for direct image URLs and web pages that you own or are authorized to access.

## Features
- URL input for direct images and ordinary HTML pages
- Image list with vertical scrolling
- `og:image` cover preference, falling back to the first page image
- Recent history and saved last selected image index
- Favorites stored locally on device
- GitHub Actions workflow to build a debug APK

## Build
Open this folder in Android Studio, or run `gradle assembleDebug` with JDK 17 and Android SDK 35 installed.

GitHub Actions runs on pushes to `main`. Download `ComicReader-debug-apk` from the completed workflow run's Artifacts section.

## Limitations
Some sites block automated requests, require login, use scripts to load images, or disallow reuse. This app does not bypass access controls, paywalls, DRM, or copyright restrictions. Use only content you have permission to access and display.
