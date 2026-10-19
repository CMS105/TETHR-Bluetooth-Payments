# BluetoothUPI

BluetoothUPI is a native Android demo app built with Kotlin and Jetpack Compose. It sends a demo payment request from a Merchant phone to a Customer phone over Bluetooth Classic RFCOMM. It works without an internet connection, cloud service, or backend.

> **Demo only — no real money is transferred.** Approval is only a message exchanged between the two phones. The ₹1,000.00 simulated balance is never deducted.

## Open and build

1. Install Android Studio with Android SDK Platform 36. Let Android Studio or Gradle install any required Build-Tools packages.
2. Open this `bluetooth-upi-android` folder in Android Studio.
3. Use Android Studio's Gradle sync. If asked, select a JDK 17 or newer for Gradle.
4. Select the `app` run configuration and run it on an Android phone with Bluetooth, or build a debug APK from **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

The project uses Gradle Kotlin DSL, application ID `com.example.bluetoothupi`, minimum SDK 26, and target/compile SDK 36. The first Gradle sync downloads the Android Gradle Plugin and AndroidX dependencies.

### Command-line build and install

With Android SDK Platform 36, Java 17+, and an Android device with USB debugging enabled:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For two physical phones connected to the same computer, install the same debug APK on both:

```bash
adb devices
adb -s PHONE_1_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE_2_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Replace each serial with the value shown by `adb devices`. Alternatively, transfer `app/build/outputs/apk/debug/app-debug.apk` to each phone and install it there.

## Two-phone test

1. On both phones, turn on Bluetooth and open Android **Settings → Bluetooth**.
2. Pair the phones using Android's normal system pairing flow. Accept any pairing confirmation shown by either phone.
3. Open BluetoothUPI on Phone 1, select **Customer**, and tap **Start Customer listener**. Grant the requested Nearby devices permissions. When Android asks whether the phone may be discoverable, allow it; visibility is requested for up to five minutes.
4. Keep BluetoothUPI open on Phone 1. The app must not assume that Bluetooth being enabled makes the phone discoverable.
5. On Phone 2, open BluetoothUPI, select **Merchant**, and tap **Refresh** under Paired phones. Select the paired Customer phone by tapping **Connect**. The connection changes to connected only after the RFCOMM socket succeeds.
6. Enter a positive INR amount with no more than two decimal places, such as `12.50`, then tap **Send demo request**.
7. On Phone 1, review the request and tap **Approve** or **Reject**. Phone 2 shows the decision. The app never deducts the simulated balance or sends a real payment.
8. Disconnect and repeat with the other decision, a second amount, or by restarting the Customer listener.

If the phones were already paired, the RFCOMM connection can be attempted while the Customer listener is running. If the connection does not succeed, check that the phones remain paired, Bluetooth is on, the Customer listener is active, and the Customer phone is visible if required by that device. The Merchant list contains paired devices only; this app does not run Bluetooth discovery.

## Permissions and privacy

- Android 12 and newer: `BLUETOOTH_CONNECT` is requested for RFCOMM and paired-device access. `BLUETOOTH_ADVERTISE` is requested only when asking Android to show the Customer phone as discoverable.
- Android 11 and older: the legacy Bluetooth permissions are declared. The app does not scan for nearby devices, so it does not request `BLUETOOTH_SCAN` or location permission.
- Bluetooth messages contain only a generated request ID, an amount in integer paise, and an approval/rejection decision. No bank, UPI, payment credential, or secret data is collected.

## Message protocol

Both endpoints use the same fixed RFCOMM service UUID:

```text
e7d2a6c0-3f41-4c10-9a28-b7f12c18d050
```

Messages are UTF-8 JSON objects terminated by a newline. Amounts are positive integer paise values; request IDs are canonical UUIDs.

```json
{"type":"payment_request","requestId":"c1a3a7c6-14b6-4896-bd02-94b4ac0ee507","amountPaise":1250}
{"type":"payment_response","requestId":"c1a3a7c6-14b6-4896-bd02-94b4ac0ee507","decision":"approved"}
```

The receiver rejects invalid JSON, unsupported message types, non-positive or non-integer amounts, malformed or duplicate request IDs, and replies that do not match the active request. Messages are capped at 4 KiB. An unanswered Merchant request times out after 30 seconds.

## Build and hardware verification

This source project is intended to sync and build in Android Studio with SDK Platform 36 and a JDK 17 or newer. An Android emulator is not a substitute for the RFCOMM acceptance test. Final Bluetooth pairing, visibility prompts, disconnection/reconnection, and two-phone transaction testing must be performed on physical Android devices.
