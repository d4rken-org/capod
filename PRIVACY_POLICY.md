# Privacy policy
This is the privacy policy for the Android app "CAPod - Companion for AirPods".

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
This is a privacy measure on Android's side because you could determine someones location by scanning for Bluetooth devices:
If you know the physical location of a Bluetooth device (e.g. AirTags) you could use Bluetooth data to calculate your position.

### Location access in the background

CAPod uses the "location access in the background" permission (`ACCESS_BACKGROUND_LOCATION`) on Android 11 and older to receive Bluetooth Low Energy data while the app is in the background. This permission enables the "Show popup" and "Autoconnect" features and allows CAPod to react to nearby devices whil the app is closed.

## Automatic crash reports

Anonymous device information may be collected in the event of an app crash or error.

To do this the app uses the service "Bugsnag":
https://www.bugsnag.com/

Bugsnag's privacy policy can be found here:
https://docs.bugsnag.com/legal/privacy-policy/

Crash reports may contain device and app related information, e.g. your phone model, Android version and app version.

You can disable automatic reports in the app's settings.
