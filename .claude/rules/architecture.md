---
description: Architecture overview, module structure, key components, data flow, and dependencies
globs:
  - "app/**/*.kt"
  - "app-common/**/*.kt"
  - "**/*.gradle.kts"
---

# Architecture

## Multi-Module Structure

- **app/**: Main Android application with FOSS and Google Play flavors
- **app-common/**: Shared code between main app and Wear OS app

## Core Patterns

- **MVVM**: ViewModels with LiveData/StateFlow for UI state management
- **Dependency Injection**: Hilt/Dagger for dependency management
- **Coroutines**: Kotlin coroutines for async operations
- **Repository Pattern**: Data layer abstraction for monitoring and settings

## Key Components

### PodMonitor System

- `PodMonitor`: Core service that detects and tracks AirPods via Bluetooth LE
- `MonitorControl`: Manages MonitorService lifecycle
- `MonitorService`: Foreground service that continuously scans for AirPods
- `BluetoothEventReceiver`: Handles system Bluetooth events

### Reaction System

- `ReactionSettingsFragment`: Configuration for popup notifications
- `PopUpWindow`: Displays AirPods status when case is opened
- `PopUpPodViewFactory`: Creates UI components for different pod models

### Common Utilities

- `EdgeToEdgeHelper`: Handles Android edge-to-edge display insets

## Build Configuration

### Flavors

- **FOSS**: Open-source version without Google Play dependencies
- **Google Play (gplay)**: Version with billing client for in-app purchases

### Build Types

- **debug**: Unobfuscated, full logging, no minification
- **beta**: Obfuscated, production-ready with strict lint checks
- **release**: Fully optimized for production distribution

## Data Flow

The app follows a unidirectional data flow:

1. `BluetoothEventReceiver` detects Bluetooth events
2. `MonitorService` scans for AirPods beacon data
3. `PodMonitor` processes and stores device information
4. ViewModels observe monitor data via repositories
5. UI components react to ViewModel state changes
6. `ReactionSystem` triggers popups and notifications

## Bluetooth LE Implementation

The app uses Android's Bluetooth LE APIs to scan for Apple device advertisements. The core scanning logic is in `MonitorService` which runs as a foreground service.

## Multi-Platform Considerations

Code shared between phone and Wear OS apps is placed in `app-common`. When modifying shared functionality, ensure compatibility across both platforms.

## Testing Strategy

- **Unit Tests**: Located in `app-common/src/test/` for shared logic
- **Test Flavors**: Separate test configurations for FOSS and Google Play variants

## Key Dependencies

- **Hilt**: Dependency injection framework
- **AndroidX Navigation**: Fragment navigation with SafeArgs
- **Moshi**: JSON serialization for configuration and debugging
- **Material Design**: UI components following Material Design guidelines
