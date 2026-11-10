# SUFI AI — Android starter project

This is a native Android Studio project written in Kotlin + Jetpack Compose.

## Included in version 1
- Chat interface
- Gemini API integration (user supplies their own API key in Settings)
- Android speech recognition input (tap the microphone)
- Android text-to-speech output
- Basic local commands: open YouTube, open Chrome/browser, tell current time

## Build an APK
1. Install the latest stable Android Studio on a computer.
2. Open this `SUFI_AI_Android` folder in Android Studio.
3. Let Gradle sync finish. Use JDK 17.
4. Connect an Android phone or use an emulator and press Run.
5. To create an APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Android Studio will show the generated APK location, normally `app/build/outputs/apk/debug/app-debug.apk`.

## Gemini setup
1. Create a Gemini API key in Google AI Studio.
2. Open SUFI AI > Settings.
3. Paste the key and press Save.
4. Ask a question. The app calls `gemini-2.5-flash`.

A 400/401 response usually means the model/API request or key/project configuration needs checking. Never paste your API key into public chats, screenshots, GitHub, or shared code.

## Important limitations
- This is a working starter project, not a finished always-listening JARVIS replacement.
- Voice input starts only after tapping the microphone. Always-on wake-word listening needs a separate foreground-service implementation, ongoing microphone notification, battery handling, and Android-version-specific testing.
- Opening apps is implemented only for YouTube and Chrome/browser. Arbitrary screen tapping, reading other apps, sending messages, or changing phone settings requires additional Android APIs and explicit user permissions; it is not silently enabled.
- The app needs internet for Gemini answers. Basic local commands can work without Gemini.
- API keys embedded in a distributed APK can be extracted. For a public release, move Gemini calls behind a secure backend instead of shipping a personal key in the app.
