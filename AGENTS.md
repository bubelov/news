# AGENTS.md - Developer Guide for News App

## Project Overview

This is an Android RSS feed reader and podcast player. It supports Miniflux, Nextcloud, and standalone modes.

## Build Commands

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Release APK (self-signed)
```bash
./gradlew assembleSelfSignedRelease
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Run a Single Unit Test
```bash
./gradlew testDebugUnitTest --tests "feeds.FeedsModelTest"
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
- Java 11 (JVM target)
- Android SDK 36 (compileSdk)

### Project Structure
```
app/src/main/kotlin/<package>/
app/src/test/kotlin/<package>/      # Unit tests
app/src/androidTest/kotlin/        # Instrumented tests
```

### Package Organization
- Group by feature/domain (e.g., `feeds`, `entries`, `auth`, `sync`)
- Use lowercase with camelCase for file names

### Imports
- Fully qualified imports (no wildcard imports)
- Order: standard library -> Android -> external libraries -> project
- Example:
  ```kotlin
  package feeds

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import co.appreactor.feedk.AtomLinkRel
  import conf.ConfRepo
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

### State Management
- Use `MutableStateFlow` for mutable state with `asStateFlow()` for exposure
- Use sealed classes for UI states:
  ```kotlin
  sealed class State {
      object Loading : State()
      data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
      data class ShowingError(val error: Throwable) : State()
  }
  ```

### Error Handling
- Use `runCatching` with `.onSuccess` and `.onFailure`:
  ```kotlin
  runCatching {
      // operation
  }.onSuccess {
      // handle success
  }.onFailure { e ->
      error.update { e }
  }
  ```
- Store errors in `MutableStateFlow<Throwable?>` for UI observation
- Avoid exposing raw exceptions to users; log and show meaningful messages

### Dependency Injection
- Use Koin with `@KoinViewModel` for ViewModels
- Use `@Single` for repositories and other singletons
- Example:
  ```kotlin
  @KoinViewModel
  class FeedsModel(
      private val confRepo: ConfRepo,
      private val feedsRepo: FeedsRepo,
  ) : ViewModel()
  ```

### Coroutines
- Use `viewModelScope.launch` for ViewModel coroutine launching
- Use `withContext(Dispatchers.IO)` for blocking operations
- Use `Flow` for reactive data streams
- Use `flowOf()` for simple flow creation

### Database
- Raw SQL and built-in helpers only, no external deps or ORMs

### Networking
- Retrofit + OkHttp for API calls
- MockWebServer for testing API integrations

### Testing
- JUnit 4 with MockK for mocking
- Use `runBlocking` for suspend test functions
- Use `newSingleThreadContext` for main dispatcher in tests
- Use `testDb()` helper for in-memory test databases:
  ```kotlin
  val db = testDb()
  ```

### Android-Specific
- ViewBinding enabled
- Use `Fragment` with `FragmentKtx` extensions
- Navigation Component with SafeArgs
- Use `Dispatchers.setMain` / `resetMain` in `@Before` / `@After`

### Formatting
- 4 spaces for indentation (Kotlin default)
- No explicit line length limit (follow Android Studio defaults)
- Trailing commas for readability
- Single blank line between top-level declarations

### What NOT to Do
- Don't use `var` unless necessary (prefer `val`)
- Don't use `lateinit` for simple nullable types
- Don't expose mutable collections
- Don't commit secrets or keys to the repository
