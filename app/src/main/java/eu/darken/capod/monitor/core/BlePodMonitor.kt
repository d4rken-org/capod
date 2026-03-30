package eu.darken.capod.monitor.core

/**
 * Type alias for the BLE scan pipeline.
 *
 * The [PodMonitor] class handles BLE advertisement scanning and device detection.
 * New code should use [BlePodMonitor] to clarify that this is the BLE-only monitor,
 * not the unified merge point ([DeviceMonitor]).
 *
 * Full rename deferred to IDE refactoring pass.
 */
typealias BlePodMonitor = PodMonitor
