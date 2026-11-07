# Installation

There is no store release yet. You build the app from source and install the APK on your device.

## What you need

* JDK 17. Gradle 8.14 does not run on JDK 25. Check with `java -version`; the output should contain `17`.
* Android SDK 35. Android Studio installs it for you.
* An Android 8.0 (API 26) or newer phone or tablet, or an emulator.

## Steps

### 1. Get the code

```bash
git clone https://github.com/anand34577/pos-print.git
cd pos-print
```

### 2. Tell Gradle where the SDK is

Create a file called `local.properties` in the project folder. Skip this if Android Studio already made it.

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

Use the path to your own SDK folder.

### 3. Run the tests and build

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

You should see `BUILD SUCCESSFUL`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Install it

With a phone connected over USB and USB debugging turned on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio, set *Gradle JDK* to 17 and press Run.

## Release build

1. Copy `keystore.properties.example` to `keystore.properties`. Git ignores this file.
2. Fill in your keystore path, passwords and alias.
3. Run `./gradlew :app:assembleRelease`.

Without `keystore.properties` the release APK is unsigned. Next: [Getting Started](Getting-Started).
