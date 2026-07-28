---
description: AAP protocol landmines and settled dead ends — what not to send, and what has already been proven impossible
paths:
  - "app/src/main/java/**/aap/**"
  - "app/src/main/java/**/monitor/core/aap/**"
  - "app/src/main/java/**/reaction/core/conversation/**"
---

# AAP Protocol — Landmines and Dead Ends

This file holds only what the code doesn't already say. The Conversational Awareness status
taxonomy is KDoc'd on `ConversationAwarenessEvent`, the `0x4B` frame shapes are documented at the
decode site in `DefaultAapDeviceProfile`, and known control IDs are catalogued in `AapControlId` —
read those, don't duplicate them here.

## Never send `0x0001` mid-session

`AapMessageType.CAPABILITIES_REQUEST` (`0x0001`) is a **handshake-phase opcode only**. Sending
`04 00 04 00 01 00` on an established session makes the device close the L2CAP stream
(`Stream closed by remote`), forcing a full reconnect. Verified experimentally on AirPods Pro 3
(fw `81.2675000075000000.6503`). The Stream State Info payload also differs between the original
connection (45B) and the forced reconnect (28B), suggesting device-side state loss.

The enum lists it with no warning, so it looks callable. It isn't.

This came up as a "cheap refresh settings" probe after writing `DYNAMIC_END_OF_CHARGE` (`0x3B`),
because the device doesn't echo that write in-session. **There is no read-setting primitive in AAP.**
Settings are push-only — on connect, on external change, or not at all. The supported pattern is
optimistic UI state + profile-learned persistence + let reconnects refresh.

## `0x37` does not use the Apple-bool encoding

Hearing Protection PPE (`0x37`, the EN 352 82 dBA media cap) is **Pro 3 only** and encodes as a
plain `01 = on` / `00 = off`. Every other AAP boolean uses the Apple-bool convention where false is
`0x02` — `encodeAppleBool` is wrong for this one. Companion `0x38` carries the cap level
(observed `0x52` = 82 dBA).

Hardware-confirmed reads on Pro 3 (A3064), 2026-06-10. A full settings flood on Pro 2 USB-C (A3048)
never contains `0x37` or `0x38`. Write-effect is **not** yet hardware-verified; it goes over the same
PSM `0x1001` channel CAPod already writes ANC and CA to, so the risk is low, but it is untested.

## Settled dead ends — do not re-investigate

**Real-time ambient dB level (#521) is not implementable.** AirPods send no dB or attenuation
telemetry over any AAP opcode or ATT characteristic. Apple's feature measures SPL with the Watch or
iPhone microphone and subtracts a *static per-model lookup table* held in the private
`HearingUtilities.framework`; AirPods contribute only their current listening mode. Established via
the iOS 26.1 decompile plus a sweep of librepods, apple-wireshark, and the tyalie AAP definitions.
`0x50` is PerfStats, `0x53` a PME config blob, `0x58` an Opus mic audio stream — none is a metric.

**Loud Sound Reduction (#520) has no non-root toggle.** LSR lives on a separate raw ATT channel, not
AAP: a second L2CAP socket to **PSM 31 (`0x001F`)**, handle `0x1B`, plain `0x01`/`0x00`. Connecting
and *reading* works without Apple vendor-ID spoofing. **Writes are silently ignored** — the pods
return a Write Response (`13`) and the immediate read-back is unchanged. Reproduced back-to-back on
Pixel 8 + Pro 2 USB-C, 2026-06-10. This matches librepods only exposing the toggle behind their
root/Xposed VID-spoofing hook. A functional toggle is root-only; a read-only status indicator is
feasible today.

Not to be confused with `0x37` above — different feature, different channel, and that one is a
normal writable AAP setting.

## Session exclusivity

The pods accept exactly **one AAP session**. Any debug activity that boots the app starts
`MonitorService`, which auto-connects and wins the socket — a proof-of-concept activity will connect
at the L2CAP layer and then receive nothing. Protocol experiments have to go through the monitor's
own session, i.e. the real feature write path.
