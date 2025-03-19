---
layout: plain
permalink: /privacy
title: "Privacy Policy"
---

# Privacy policy
This is the privacy policy for the Android app "CAPod - Companion for AirPods" by Matthias Urhahn (darken).

## Preamble
CAPod respects your privacy.

I do not collect, share or sell personal information.

Send a [quick mail](mailto:support@darken.eu) if you have questions.

My underlying privacy principle is the [Golden Rule](https://en.wikipedia.org/wiki/Golden_Rule).

## Location data

CAPod does not collect, share or sell location data.

Location permissions are required to receive Bluetooth Low Energy (BLE) data and ebale its core functionality.
The permission "access fine location" (`ACCESS_FINE_LOCATION`) and "access coarse location" (`ACCESS_COARSE_LOCATION`) are required to receive Bluetooth Low Energy data on Android 11 and lower.
On Android 12+ the newer and more fine grained `BLUETOOTH_SCAN` permission is used instead.

Bluetooth Low Energy is a technology that devices like AirPods use to communicate their status to nearby devices.
CAPod requests location permissions because these permissions are required to work with Bluetooth Low Energy data.
This is a privacy measure on Android's side because you could determine someones location by scanning for Bluetooth
devices:
If you know the physical location of a Bluetooth device (e.g. AirTags) you could use Bluetooth data to calculate your
position.

### Location access in the background

CAPod uses the "location access in the background" permission (`ACCESS_BACKGROUND_LOCATION`) on Android 11 and older to
receive Bluetooth Low Energy data while the app is in the background. This permission enables the "Show popup" and "
Autoconnect" features and allows CAPod to react to nearby devices whil the app is closed.

## Automatic error reports

*This was removed in v2.11.0+*

If an error occurs, an automated report may be sent to help me fix the issue.

This is optional and you can opt out of this in the settings.

Error reports are collected using "Bugsnag":
https://www.bugsnag.com/

Bugsnag's privacy policy can be found here:
https://docs.bugsnag.com/legal/privacy-policy/

Error reports contain device and app information related to the error that occured.
Additional information about the error context may also be included, e.g. what this app did shortly before the error.

Additional details about the type of data that is collected by the error tracking SDK can be found here:
https://docs.bugsnag.com/platforms/android/playstore-privacy/

Error reports are pseudonymous. Unless you tell me your install-ID, I don't know that an error report came from you.

Error reports are automatically deleted after 90 days.

## Debug logs

The app has a debug log feature that can be used to assist troubleshooting efforts. This feature creates a log file that contains verbose output of what the app is doing.

It is manually triggered by the user through an option in the app settings. The recorded log file can be shared through compatible apps (e.g. your email app) using the system's share dialog. As this log file may contain sensitive information (e.g. details about files or installed applications) it should only be shared with trusted parties.
