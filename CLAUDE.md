# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Build Commands

```bash
# Build debug version
./gradlew assembleDebug

# Build all variants (FOSS and Google Play flavors)
./gradlew assemble

# Build specific flavor and type
./gradlew assembleFossDebug
./gradlew assembleGplayRelease

# Build app bundles for Play Store
./gradlew bundleGplayRelease
```

### Testing Commands

```bash
# Run all unit tests
./gradlew test

# Run unit tests for specific variant
./gradlew testFossDebugUnitTest

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
./gradlew connectedFossDebugAndroidTest

# Run all checks (lint + tests)
./gradlew check
```

### Code Quality Commands

```bash
# Run lint for all variants
./gradlew lint

# Run lint for specific variant
./gradlew lintFossDebug

# Auto-fix lint issues where possible
./gradlew lintFix

# Update lint baseline
./gradlew updateLintBaseline
```

### Release Commands

```bash
./gradlew assembleFossRelease assembleGplayRelease
```

## Architecture Overview

### Multi-Module Structure

- **app/**: Main Android application with FOSS and Google Play flavors
- **app-common/**: Shared code between main app

### Core Architecture Patterns

- **MVVM**: ViewModels with LiveData/StateFlow for UI state management
- **Dependency Injection**: Hilt/Dagger for dependency management
- **Coroutines**: Extensive use of Kotlin coroutines for async operations
- **Repository Pattern**: Data layer abstraction for monitoring and settings

### Key Components

#### PodMonitor System

- `PodMonitor`: Core service that detects and tracks AirPods via Bluetooth LE
- `MonitorControl`: Manages background monitoring worker lifecycle
- `MonitorWorker`: Background worker that continuously scans for AirPods
- `BluetoothEventReceiver`: Handles system Bluetooth events

#### Reaction System

- `ReactionSettingsFragment`: Configuration for popup notifications
- `PopUpWindow`: Displays AirPods status when case is opened
- `PopUpPodViewFactory`: Creates UI components for different pod models

#### Common Utilities

- `EdgeToEdgeHelper`: Handles Android edge-to-edge display insets

### Build Configuration

#### Flavors

- **FOSS**: Open-source version without Google Play dependencies
- **Google Play**: Version with billing client for in-app purchases

#### Build Types

- **debug**: Unobfuscated, full logging, no minification
- **beta**: Obfuscated, production-ready with strict lint checks
- **release**: Fully optimized for production distribution

### Data Flow Architecture

The app follows a unidirectional data flow:

1. `BluetoothEventReceiver` detects Bluetooth events
2. `MonitorWorker` scans for AirPods beacon data
3. `PodMonitor` processes and stores device information
4. ViewModels observe monitor data via repositories
5. UI components react to ViewModel state changes
6. `ReactionSystem` triggers popups and notifications

### Testing Strategy

- **Unit Tests**: Located in `app-common/src/test/` for shared logic
- **Test Flavors**: Separate test configurations for FOSS and Google Play variants

### Key Dependencies

- **Hilt**: Dependency injection framework
- **AndroidX Navigation**: Fragment navigation with SafeArgs
- **WorkManager**: Background task scheduling for monitoring
- **Moshi**: JSON serialization for configuration and debugging
- **Material Design**: UI components following Material Design guidelines

## Development Notes

### Bluetooth LE Implementation

The app uses Android's Bluetooth LE APIs to scan for Apple device advertisements. The core scanning logic is in
`MonitorWorker` which runs as a long-lived background task.

### Multi-Platform Considerations

Code shared between phone and Wear OS apps is placed in `app-common`. When modifying shared functionality, ensure
compatibility across both platforms.

### Localization Guidelines

When adding new user-facing strings:

- **Always use string resources**: Never hardcode user-facing text in layouts or code
- **Follow naming conventions**: Use descriptive, hierarchical naming (e.g., `profiles_name_default`, `settings_bluetooth_enabled`)
- **Provide context**: String names should indicate usage and location
- **Consider pluralization**: Use Android plural resources (`<plurals>`) when quantities vary

Examples of correct string naming:
- `profiles_create_title` (screen title)
- `profiles_name_label` (form field label)  
- `profiles_delete_confirmation` (dialog message)
- `error_network_unavailable` (error message)