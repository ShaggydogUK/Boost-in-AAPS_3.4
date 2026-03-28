# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AndroidAPS Boost V2 — an open-source Android app for automated insulin delivery (artificial pancreas). This is a fork featuring the Boost plugin with DynISF V2 formula, based on AAPS 3.4.0.0. It includes a Wear OS companion app.

## Build Commands

```bash
./gradlew assembleDebug                  # Build debug APK
./gradlew assembleRelease                # Build release APK
./gradlew test                           # Run all unit tests
./gradlew :plugins:aps:test              # Run tests for a specific module
./gradlew :plugins:aps:testDebugUnitTest # Run debug unit tests for a module
./gradlew connectedAndroidTest           # Run instrumentation tests (device required)
./gradlew ktlintFormat                   # Auto-format Kotlin code
./gradlew ktlintCheck                    # Check formatting without fixing
./gradlew clean                          # Clean build
```

## Build Configuration

- **Gradle 9.0.0**, AGP 8.13.2, Kotlin 2.2.21, Java 21
- compileSdk 36, minSdk 31 (30 for Wear), targetSdk 32
- Version catalog at `gradle/libs.versions.toml`
- Shared version constants in `buildSrc/src/main/kotlin/Versions.kt`
- ktlint applied globally via root `build.gradle.kts`; wildcard imports disabled (`.editorconfig`)

## Architecture

**~50 Gradle modules** organized as:

- **`app`** — Main application entry point, DI wiring (`app/src/main/kotlin/app/aaps/di/AppComponent.kt`)
- **`core/*`** — Shared foundation: `interfaces` (plugin contracts, critical abstraction layer), `data`, `keys` (preference keys), `objects`, `utils`, `ui`, `nssdk` (Nightscout SDK), `graph`/`graphview`, `validators`
- **`plugins/*`** — Feature plugins: `aps` (algorithm — Boost, BoostV2, SMB, AMA), `automation`, `configuration`, `constraints`, `insulin`, `main`, `sensitivity`, `smoothing`, `source` (BG sources), `sync` (Nightscout, Tidepool)
- **`pump/*`** — Pump drivers: Dana variants, Omnipod (Dash/Eros), Medtronic, Medtrum, Insight, ComboV2, Equil, Diaconn, EOPatch, virtual. Shared code in `pump:common`, BLE via `pump:rileylink`
- **`database/*`** — Room database (`impl` has entities/DAOs/migrations, `persistence` has the repository layer)
- **`implementation`** — Concrete implementations of core interfaces
- **`shared/*`** — `impl` (shared implementations), `tests` (shared test utilities)
- **`ui`** — Shared UI components, dialogs, tabs
- **`wear`** — Wear OS companion app
- **`workflow`** — Background work orchestration

**Key patterns:**
- **Dagger 2** for dependency injection (android-dagger pattern with `AndroidInjector`). Each module contributes a Dagger module wired through `AppComponent`.
- **RxJava 3** is the primary reactive framework; Kotlin coroutines used in newer code
- **Room** for persistence with 31+ migrations in `database/impl`
- **Plugin system** — features register as plugins via interfaces in `core:interfaces`
- Networking: OkHttp + Retrofit + Gson

## Testing

- **JUnit 5** (Jupiter) with `useJUnitPlatform()`
- **Mockito** (mockito-kotlin) for mocking, **Google Truth** for assertions
- Tests configured with `returnDefaultValues = true` and `includeAndroidResources = true`
- Shared test utilities in `:shared:tests`
- Custom instrumentation test runner: `app.aaps.runners.InjectedTestRunner`

## Code Style

- 4-space indentation, no tabs
- Auto-format with ktlint before committing (`./gradlew ktlintFormat`)
- Use Android string resources (`strings.xml` + `@string/id`) — English only, other languages via Crowdin
- Kotlin compiler flags: `-Xjvm-default=all`, experimental unsigned types opt-in
