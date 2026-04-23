---
description: Architecture overview, module structure, key components, data flow, and dependencies
globs:
  - "app/**/*.kt"
  - "**/*.gradle.kts"
---

# Architecture

## Single-Module Structure

One Gradle module: `app/`. Source sets:

- `main` — shared code (Compose UI, services, monitor, bluetooth, AAP protocol, widgets)
- `foss` / `gplay` — flavor-specific code (e.g. upgrade/billing implementations)
- `debug` — debug-only code including screenshot content composables
- `test` / `testFoss` / `testGplay` — unit tests
- `screenshotTest` — Compose Preview Screenshot tests for Play Store assets

A previous `app-common/` module was merged into `app/` (commit `be8f4919`).

## Core Patterns

- **MVVM**: ViewModels with LiveData/StateFlow for UI state management
- **Dependency Injection**: Hilt/Dagger for dependency management
- **Coroutines**: Kotlin coroutines for async operations
- **Repository Pattern**: Data layer abstraction for monitoring and settings

## Key Components

### Device Monitoring

`monitor/core/` is split into two data-source siblings that `DeviceMonitor` merges:

- `monitor/core/ble/BlePodMonitor` — passive BLE scanning; reads Apple advertisement beacons (battery, case state, in-ear, etc.). Works for any pod in range; no pairing required
- `monitor/core/aap/` — AAP connection lifecycle layer on top of `AapConnectionManager`:
  - `AapLifecycleManager` — starts/stops the AAP subsystem
  - `AapAutoConnect` — auto-opens AAP sessions for bonded/known devices
  - `AapKeyPersister`, `AapLearnedSettingsPersister` — persist session keys and learned pod settings across app restarts
  - `StemConfigSender`, `StemPressReaction`, `AncGestureResolver` — push config and react to stem/HID events
- `monitor/core/cache/DeviceStateCache` — persisted last-known state so profiles still show data when a device is out of range
- `DeviceMonitor` — singleton that `combine`s `BlePodMonitor.devices + AapConnectionManager.allStates + DeviceStateCache + profiles` into unified `PodDevice` objects. ViewModels observe `DeviceMonitor.devices`; they do **not** reach into `BlePodMonitor` or the AAP layer directly
- `MonitorControl` / `MonitorService` — foreground service lifecycle holding the scan awake
- `BluetoothEventReceiver`, `BootCompletedReceiver` — system triggers that wake the service

**BLE vs AAP — what each path gives you:**

| | BLE (advertisements) | AAP (L2CAP session) |
|---|---|---|
| Direction | Read-only, passive | Bidirectional commands + events |
| Prerequisite | Bluetooth on | Bonded + `BLUETOOTH_CONNECT` + active L2CAP socket |
| Data | Battery, case open, in-ear, pod model | Settings, ANC mode control, press controls, stem events, device info |
| Availability | Any pod in range | Only your own paired pods |

### Reaction System

- `ReactionsCard`: Compose UI for reaction settings, embedded in the device settings screen
- `PopUpWindow`: Displays AirPods status when case is opened
- `PopUpContent`: Compose pod rendering — model-specific UI branches inline, no factory class

### Widget System (Glance)

- `BatteryGlanceWidget`, `AncGlanceWidget`: Jetpack Glance-based home-screen widgets
- `WidgetConfigurationActivity`: Configuration UI launched on widget placement
- Lives under `app/src/main/java/eu/darken/capod/main/ui/widget/`

### Upgrade / Pro Features

- `UpgradeRepo` interface with two flavor implementations:
  - `UpgradeRepoGplay` — billing-client backed, includes grace-period handling for interrupted purchases
  - `UpgradeControlFoss` — cache/sponsor-backed; users are `isPro = false` until they call `upgrade()`, after which the pro flag is persisted via DataStore
- FOSS is **not** "always pro" — it's opt-in via a local sponsor flow

### AAP (Apple Accessory Protocol) Stack

Three-layer structure under `pods/core/apple/aap/`:

- **`protocol/`** — pure data: `AapMessage`, `AapCommand`, `AapSetting`, `AapDeviceProfile`, `AapDeviceInfo`, `StemPressEvent`, `KeyExchangeResult`. Plus `DefaultAapDeviceProfile` and `Model.Features` capturing per-model capability
- **`engine/`** — session state machine for one connection:
  - `AapConnection` — the L2CAP socket wrapper
  - `AapSessionEngine` — drives the session lifecycle; tested in `AapSessionEngineTest`
  - `AapInboundInterpreter` / `AapOutboundController` — decode incoming messages, encode outgoing
  - `AapSettingsCoordinator`, `AapAncController`, `HidTracker`, `AapDeviceInfoDiagnostics` — feature-specific coordinators that sit on top of the session
- **`AapConnectionManager`** (singleton) — owns all open AAP sessions keyed by `BluetoothAddress`, uses `L2capSocketFactory` to create sockets. Consumers don't touch `AapConnection` directly — they call `sendCommand(...)` and observe `allStates`

The monitor-layer glue (`monitor/core/aap/`) described above wires this stack into the foreground service and persists its learned state.

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

1. `BluetoothEventReceiver` / `BootCompletedReceiver` wake `MonitorService` (foreground)
2. `MonitorService` keeps `BlePodMonitor` scanning (passive advertisements) and `AapLifecycleManager` running (active L2CAP sessions via `AapConnectionManager`)
3. `DeviceMonitor` merges BLE + AAP + cached state + profiles into `PodDevice` objects
4. ViewModels (`OverviewViewModel`, `DeviceSettingsViewModel`, `PressControlsViewModel`, widget view models) observe `DeviceMonitor.devices`; settings/command changes are sent back through `AapConnectionManager.sendCommand(...)`
5. Reaction triggers (case-open popup, auto-play, notifications) and widget state updates react to the merged flow

## Testing Strategy

- **Unit Tests**: `app/src/test/` (shared), `app/src/testFoss/`, `app/src/testGplay/` (flavor-specific — e.g. `UpgradeRepoGplayTest`, `FossUpgradeSerializationTest`)
- **Screenshot Tests**: `app/src/screenshotTest/` — Compose Preview Screenshot Testing, powers the Play Store screenshot pipeline

## Key Dependencies

- **Hilt**: Dependency injection framework
- **Navigation**: Navigation3 (`addNavigation3()`) drives current Compose screen routing. Some legacy `androidx.navigation` helpers still exist (`NavDirectionsExtensions`, `ViewModel3`) — don't assume SafeArgs is fully gone
- **kotlinx.serialization**: JSON serialization for configuration and caching
- **Material Design 3**: Compose Material3 UI components
