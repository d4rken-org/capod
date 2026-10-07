# Experimental event-driven AirPods reconnect

Opening the case can establish a classic Bluetooth link without connecting audio profiles. On Android 17+, CAPod can respond to `ACTION_ACL_CONNECTED` by requesting `BluetoothDevice.connect()` for a saved, companion-associated device. The option is per profile and off by default. Companion approval is requested when the user enables it.

The public method is invoked through reflection because this project currently compiles against API 36. Older Android versions keep the existing headset-profile connection implementation and scan-based Auto connect option.

## AirPods connection preference

On the first ready AAP session for an eligible profile, CAPod sends Automatic and saves the last requested preference. Users can choose Last connected instead. A successful socket write is not a device acknowledgment; initialization failures remain unset and retry on a later ready connection.

| Preference | Control `0x36` | Control `0x20` | `0x44` routing payload | `0x2D` query |
|---|---:|---:|---|---|
| Automatic | `1` | `1` | `04 00 02 00 03 06` | Sent |
| Last connected | `1` | `2` | `04 00 02 00 03 08` | Not sent |

Both preferences allow accessory-initiated links using control `0x36=1`. The separate Android connection request connects audio profiles after that link appears. This worked in device testing with AirPods Pro 3 and AirPods Gen 2; other AirPods models remain experimental.

The `0x2D` message is `04 00 04 00 2D 00`: a connected-device-list query with no payload. It contains no host address and is not itself an audio connection request. The `0x44` payload is retained as observed; its individual field meanings are not fully established.

## Background behavior

With the new option enabled for all configured profiles and scan-based Auto connect off, CAPod scans only while its UI is foreground or a configured device is connected. Backgrounding while disconnected cancels the scan and its stale-observation timer. At boot, it starts monitoring only if a configured device is already connected; otherwise it waits for Bluetooth broadcasts. Profile teardown/intermediate broadcasts do not restart monitoring.

Legacy or mixed-profile configurations preserve background scanning and boot monitoring. Connected monitoring and foreground scanning still consume power; no numerical battery reduction has been measured. This change does not implement Apple FastConnect, charging-case BLE pairing, or charging-case GATT control.
