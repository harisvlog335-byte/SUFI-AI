# SUFI AI – Neon (Android starter source)

## Is version mein
- SUFI Neon dark purple/cyan Android interface
- Gemini AI chat (Hindi/Hinglish/English)
- Speech-to-text button (Android speech recognition)
- Text-to-speech replies (installed Android voice par depend)
- "YouTube kholo" aur "Chrome kholo" commands
- GitHub Actions se debug APK build workflow

## API key setup
**Important:** Client APK mein API key extract ki ja sakti hai. Real/public release ke liye API key ko secure backend/proxy ke peeche rakhein. Is starter ko personal testing ke liye hi use karein.

GitHub par repository Settings → Secrets and variables → Actions → New repository secret:
- Name: `GEMINI_API_KEY`
- Value: apni Gemini API key

Phir Actions tab → **Build SUFI AI APK** → Run workflow. Successful run ke Artifacts section se `SUFI-AI-debug-apk` download karein.

## Local build
Android Studio, JDK 17 aur Android SDK 35 chahiye. Project root mein `local.properties` file banayein aur add karein:

```properties
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

`local.properties` ko GitHub par commit na karein. Android Studio mein project open karke Gradle sync karein, phir `gradle assembleDebug` chalayein.

APK path: `app/build/outputs/apk/debug/app-debug.apk`

## Abhi included nahi hai
- Always-listening "Hey SUFI" wake word
- Screen reading / arbitrary touch automation
- Background phone control

In features ke liye alag Android permissions, foreground service, accessibility design, user consent aur testing zaroori hai. Female-sounding voice Android ke installed TTS voices par depend karti hai; device Settings → Text-to-speech output mein voice select karein.
