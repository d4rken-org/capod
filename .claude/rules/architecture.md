---
description: Load-bearing architectural invariants that are not obvious from reading the code
---

# Architecture

Invariants worth knowing before you touch device state, the AAP stack, or the upgrade flow. Class
inventories and source-set layout are omitted deliberately — read the tree for those.

## BLE vs AAP

Two independent data paths. Which one a feature can use decides whether it is even possible.

| | BLE (advertisements) | AAP (L2CAP session) |
|---|---|---|
| Direction | Read-only, passive | Bidirectional commands + events |
| Prerequisite | `BLUETOOTH_SCAN` on Android 12+, Bluetooth/location permissions below | Bonded + `BLUETOOTH_CONNECT` + active L2CAP socket |
| Data | Battery, case open, in-ear, pod model | Settings, ANC control, press controls, stem events, device info |
| Availability | Any pod in range | Only your own paired pods |

A figure BLE never advertises cannot be obtained without a bonded AAP session, and anything
requiring a write is AAP-only.

## `DeviceMonitor` is the state merge boundary

`DeviceMonitor` (singleton) `combine`s four live sources — `BlePodMonitor.devices`,
`AapConnectionManager.allStates`, `BluetoothManager2.connectedDevices` (supplies `isSystemConnected`),
and `DeviceProfilesRepo.profiles` — then merges `DeviceStateCache` on top, deliberately after the
combine so cache writes don't feed back into it.

The invariant is about **state**, not about the whole AAP layer:

- Unified device state comes from `DeviceMonitor.devices` — don't assemble your own from `BlePodMonitor`
- Commands go **through** `AapConnectionManager.sendCommand(...)`. ViewModels legitimately inject it
  (`OverviewViewModel`, `DeviceSettingsViewModel`, `PressControlsViewModel` all do)
- Nothing outside the AAP engine touches `AapConnection` (the L2CAP socket wrapper) directly
- `TroubleShooterViewModel` reaching into `BlePodMonitor` for raw diagnostic scans is an intentional
  exception, not a pattern to copy

Because the cache is merged in, a `PodDevice` may carry data while the device is out of range —
presence in the flow does not imply a live connection.

## `AapConnectionManager` owns sessions

It holds every open AAP session keyed by `BluetoothAddress`. Consumers call `sendCommand(...)` and
observe `allStates`.

The stack under `pods/core/apple/aap/` splits into `protocol/` (pure data) and `engine/` (per-connection
state machine). The glue in `monitor/core/aap/` wires it into the foreground service and persists
learned settings and session keys across restarts.

## FOSS is not "always pro"

`UpgradeRepo` has two flavor implementations. `UpgradeControlFoss` starts users at `isPro = false`
and only persists the pro flag after `upgrade()` is called via the local sponsor flow. Do not assume
the FOSS flavor bypasses pro gating.

## Navigation is mid-migration

Navigation3 (`addNavigation3()`) drives current Compose routing, but legacy `androidx.navigation`
helpers still exist (`NavDirectionsExtensions`, `ViewModel3`). Don't assume SafeArgs is fully gone.
