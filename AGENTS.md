# AGENTS.md - Developer Guide for Vesti Android App

## Project Overview

This is a web feed reader for Android.

## Build Commands

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Release APK
```bash
./gradlew assembleRelease
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Run a Single Unit Test
```bash
./gradlew testDebugUnitTest --tests "org.vestifeed.feeds.FeedsModelTest"
```

### Run All Tests (unit + instrumented)
```bash
./gradlew test
```

### Run Instrumented Tests (on device/emulator)
```bash
./gradlew connectedDebugAndroidTest
```

### Clean Build
```bash
./gradlew clean
```

### Build with Info
```bash
./gradlew assembleDebug --info
```

## Code Style Guidelines

### Language & Version
- Kotlin 2.3.10
- Java 21 (JVM target)
- Android SDK 36 (compileSdk)

### Project Structure
```
org.vestifeed.app/src/main/kotlin/<package>/    # App code 
org.vestifeed.app/src/test/kotlin/<package>/    # Unit tests
org.vestifeed.app/src/androidTest/kotlin/       # Instrumented tests
```

### Package Organization
- Group by feature/domain (e.g., `org.vestifeed.feeds`, `org.vestifeed.entries`, `org.vestifeed.auth`, `org.vestifeed.sync`)
- Use lowercase with camelCase for file names

### Imports
- Fully qualified imports (no wildcard imports)
- Order: standard library -> Android -> external libraries -> project
- Example:
  ```kotlin
  package org.vestifeed.feeds

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import co.appreactor.feedk.AtomLinkRel
  import org.vestifeed.conf.ConfRepo
  import kotlinx.coroutines.Dispatchers
  ```

### Naming Conventions
- **Classes**: PascalCase (e.g., `FeedsModel`, `EntriesAdapter`)
- **Functions**: camelCase (e.g., `addFeed`, `importOpml`)
- **Variables/Properties**: camelCase (e.g., `hasActionInProgress`, `error`)
- **Sealed class states**: PascalCase with object/data class (e.g., `State.Loading`, `State.ShowingFeeds`)
- **Test classes**: `<ClassName>Test` suffix (e.g., `FeedsModelTest`)
- **Test methods**: descriptive names, no prefix (e.g., `fun init()`)

### Types & Null Safety
- Use nullable types (`?`) when values can be null
- Avoid `!!` operator; prefer safe calls (`?.`) and elvis operator (`?:`)
- Use `runCatching` for exception handling instead of try-catch when appropriate

### Database
- Raw SQL and built-in helpers only, no external deps or ORMs
- Check schema.sql to see the full picture

### Networking
- Retrofit + OkHttp for API calls
- MockWebServer for testing API integrations

### Testing
- JUnit 4
  ```

### Android-Specific
- ViewBinding enabled
- Use `Fragment` with `FragmentKtx` extensions
- Use `Dispatchers.setMain` / `resetMain` in `@Before` / `@After`

### Formatting
- 4 spaces for indentation (Kotlin default)
- No explicit line length limit (follow Android Studio defaults)
- Trailing commas for readability
- Single blank line between top-level declarations

### What NOT to Do
- Don't use `var` unless necessary (prefer `val`)
- Don't commit secrets or keys to the repository
