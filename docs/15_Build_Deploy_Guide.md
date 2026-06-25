# 15 — Build & Deploy Guide

## Module Overview

This document specifies the complete build system configuration, dependency management, CI/CD pipeline, APK signing, release process, and deployment checklist for Babymomo. The project uses Gradle 8.4 with a version catalog (`libs.versions.toml`), Hilt for dependency injection, and GitHub Actions for continuous integration.

**Key Principle:** The build should be reproducible, the CI should be green on every push, and the release process should be a single tag push.

---

## 1. Project Structure

```
babymomo/
├── app/
│   ├── build.gradle.kts              # App module build config
│   ├── proguard-rules.pro            # R8/Proguard rules
│   └── src/
│       ├── main/                     # Production source
│       ├── test/                     # Unit tests
│       └── androidTest/              # Instrumented tests
│
├── gradle/
│   ├── libs.versions.toml            # Single version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties # Gradle 8.4
│
├── .github/
│   └── workflows/
│       └── android.yml               # CI pipeline
│
├── docs/                             # Architecture docs (this directory)
├── build.gradle.kts                  # Root build config
├── settings.gradle.kts               # Module includes
├── gradle.properties                 # JVM args, AndroidX flags
├── gradlew                           # Gradle wrapper (Unix)
├── gradlew.bat                       # Gradle wrapper (Windows)
├── CHANGELOG.md
├── LICENSE                           # MIT
└── README.md
```

---

## 2. Root build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
}
```

---

## 3. App build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.babymomo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.babymomo.app"
        minSdk = 26              // Android 8.0
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_LOGGING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.sqlcipher)

    // Networking (SSE streaming)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // On-device AI
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    // Embeddings (v1.1)
    implementation(libs.onnxruntime.android)

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Serialization + Coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Security
    implementation(libs.security.crypto)

    // Image loading
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
```

---

## 4. libs.versions.toml — Complete Version Catalog

```toml
[versions]
kotlin = "1.9.22"
agp = "8.3.0"
compose-bom = "2024.02.00"
hilt = "2.50"
room = "2.6.1"
sqlcipher = "4.5.4"
ktor = "2.3.8"
litert = "1.0.1"
onnxruntime = "1.17.0"
workmanager = "2.9.0"
kotlinx-serialization = "1.6.3"
kotlinx-coroutines = "1.8.0"
security-crypto = "1.1.0-alpha06"
accompanist = "0.34.0"
coil = "2.6.0"
junit = "4.13.2"
mockk = "1.13.10"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version = "2.7.7" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room + SQLCipher
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
sqlcipher = { group = "net.zetetic", name = "android-database-sqlcipher", version.ref = "sqlcipher" }

# Networking
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# On-device AI
litert = { group = "com.google.ai.edge.litert", name = "litert", version.ref = "litert" }
litert-gpu = { group = "com.google.ai.edge.litert", name = "litert-gpu", version.ref = "litert" }

# Embeddings
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime" }

# WorkManager
workmanager-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }

# Serialization + Coroutines
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }

# Security
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }

# Image loading
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

---

## 5. gradle.properties

```properties
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true

# AndroidX
android.useAndroidX=true

# Kotlin
kotlin.code.style=official

# Non-transitive R classes
android.nonTransitiveRClass=true

# Compose
android.defaults.buildfeatures.buildconfig=true
```

---

## 6. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Babymomo"
include(":app")
```

---

## 7. GitHub Actions CI

### Workflow: `.github/workflows/android.yml`

```yaml
name: Build APK

on:
  push:
    branches: [main, dev]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: babymomo-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk

      - name: Build release APK
        run: ./gradlew assembleRelease
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}

      - name: Upload release APK
        if: startsWith(github.ref, 'refs/tags/v')
        uses: actions/upload-artifact@v4
        with:
          name: babymomo-release-apk
          path: app/build/outputs/apk/release/app-release.apk
```

---

## 8. APK Signing

### Signing Configuration

```kotlin
// In app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePwd = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasVal = System.getenv("KEY_ALIAS")
            val keyPwd = System.getenv("KEY_PASSWORD")

            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePwd
                keyAlias = keyAliasVal
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Keystore Generation

```bash
keytool -genkeypair \
  -alias babymomo \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore babymomo-release.keystore \
  -storetype PKCS12

# Convert to base64 for GitHub Secrets
base64 -w 0 babymomo-release.keystore > keystore_base64.txt
```

### GitHub Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g., "babymomo") |
| `KEY_PASSWORD` | Key password |

---

## 9. Release Process

### Tag-Based Release

```bash
# 1. Update version in build.gradle.kts
#    versionCode = 2
#    versionName = "1.1.0"

# 2. Update CHANGELOG.md

# 3. Commit and tag
git commit -am "release: v1.1.0"
git tag v1.1.0
git push origin main --tags

# 4. GitHub Actions builds release APK
# 5. Create GitHub Release manually or via action
# 6. Attach APK to release
```

### GitHub Release Workflow (v1.1 enhancement)

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: ./gradlew assembleRelease
      - uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/app-release.apk
          generate_release_notes: true
```

---

## 10. Build Variants

| Variant | Application ID | Minify | Debug | Signature |
|---------|---------------|--------|-------|-----------|
| debug | com.babymomo.app.debug | No | Yes | Debug key |
| release | com.babymomo.app | Yes (R8) | No | Release key |

### Debug Variant Features

- `BuildConfig.ENABLE_LOGGING = true`
- Application ID suffix: `.debug` (can install alongside release)
- Version name suffix: `-debug`
- No code shrinking or obfuscation

### Release Variant Features

- `BuildConfig.ENABLE_LOGGING = false`
- R8 full mode: minification + shrinking + obfuscation
- Resources shrinking enabled
- Proguard rules applied

---

## 11. Dependency Audit

### Direct Dependencies (23)

| Category | Count | Libraries |
|----------|-------|-----------|
| Compose | 3 | ui, material3, navigation |
| Hilt | 3 | android, compiler, navigation-compose |
| Room | 4 | runtime, ktx, compiler, sqlcipher |
| Networking | 4 | ktor-core, ktor-okhttp, ktor-negotiation, ktor-json |
| AI | 2 | litert, litert-gpu |
| WorkManager | 2 | runtime-ktx, hilt-work |
| Serialization | 2 | kotlinx-json, kotlinx-coroutines |
| Security | 1 | security-crypto |
| Images | 1 | coil-compose |
| Testing | 3 | junit, mockk, coroutines-test |
| Embeddings | 1 | onnxruntime-android (v1.1) |

### No Analytics, No Ads, No Tracking

The dependency list contains **zero** analytics, advertising, or tracking SDKs:
- No Firebase Analytics
- No Google Analytics
- No Facebook SDK
- No Crashlytics
- No AdMob
- No any other third-party tracker

---

## 12. AndroidManifest

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Optional — only used when user enables internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".BabymomoApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@style/Theme.Babymomo"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 13. Deployment Checklist

### Pre-Release

- [ ] All unit tests pass (`./gradlew testDebugUnitTest`)
- [ ] Lint checks pass (`./gradlew lintDebug`)
- [ ] Debug APK tested on physical device (minSdk 26)
- [ ] Debug APK tested on physical device (targetSdk 35)
- [ ] No hardcoded secrets in source code
- [ ] Proguard rules validated (release build runs correctly)
- [ ] SQLCipher encryption verified
- [ ] EncryptedSharedPreferences working
- [ ] All permissions justified and minimal
- [ ] Network security config correct
- [ ] Version code and name updated
- [ ] CHANGELOG.md updated

### Release

- [ ] Tag created: `v1.x.x`
- [ ] GitHub Actions CI green
- [ ] Release APK built and signed
- [ ] APK installed on test device, all features work
- [ ] GitHub Release created with APK attachment
- [ ] Release notes match CHANGELOG.md

### Post-Release

- [ ] Merge release tag back to `main` if needed
- [ ] Update `dev` branch with latest
- [ ] Close related issues
- [ ] Update README with new version info

---

## 14. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `build_debug` | `./gradlew assembleDebug` | APK produced, no errors |
| `build_release` | `./gradlew assembleRelease` | Signed APK produced |
| `unitTests_pass` | `./gradlew testDebugUnitTest` | All tests pass |
| `lint_pass` | `./gradlew lintDebug` | No critical lint errors |
| `versionCatalog_valid` | Parse libs.versions.toml | All dependencies resolve |
| `ci_green` | Push to dev branch | GitHub Actions green |
| `release_tag` | Push v1.0.0 tag | Release APK built and uploaded |
| `proguard_releaseWorks` | Install release APK | App runs correctly |
| `proguard_noCrashes` | Release build with R8 | Room, Hilt, serialization work |
| `keystore_signing` | Verify release APK signature | Valid signature |
| `manifest_permissions` | Check declared permissions | Only 7 permissions, all justified |
| `networkConfig_noCleartext` | Release build HTTP test | Cleartext blocked |
