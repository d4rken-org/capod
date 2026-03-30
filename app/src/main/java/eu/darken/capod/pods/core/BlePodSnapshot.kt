package eu.darken.capod.pods.core

/**
 * Type alias for the BLE-sourced device snapshot.
 *
 * The [PodDevice] interface represents BLE scan data (battery, ear detection, case state)
 * extracted from Apple Continuity Protocol advertisements.
 *
 * New code should use [BlePodSnapshot] to clarify that this is the BLE data source,
 * not the unified facade ([eu.darken.capod.monitor.core.MonitoredDevice]).
 *
 * Full rename of [PodDevice] → [BlePodSnapshot] across the codebase is deferred to
 * an IDE refactoring pass (103 files, 470 occurrences).
 */
typealias BlePodSnapshot = PodDevice
