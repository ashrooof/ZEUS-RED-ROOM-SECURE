# ZEUS RED ROOM SECURE

Independent, consent-first test continuation of ZEUS RED ROOM Build 90.

## Test branch behavior

- The legacy `Contacts-Web-App` repository and the `main` branch are not changed by this work.
- Existing Build 90 interface, Bluetooth discovery/pairing, PBAP transfer and TXT export remain the baseline.
- The remote phone owner must approve the Android PBAP/system request on their own phone.
- **No automatic contact upload:** after PBAP completes, the operator selects contacts and taps Telegram. Android then shows a second confirmation naming the device and selected-contact count. Only the selected contacts are sent after confirmation.
- Selected data is encrypted at rest in a local queue using Android Keystore AES-GCM.
- The test APK sends a TXT document directly to Telegram Bot API. There is no Cloudflare Worker, server, or database.
- The Telegram bot token is read from the GitHub Actions secret `TELEGRAM_BOT_TOKEN` during the build and injected as encrypted/obfuscated data into the test APK. The decryption passphrase is also inside the APK, so this is not strong protection against reverse engineering. Use a disposable test bot token and rotate it after testing.
- Destination chat ID: `8552540563`.

## Build

GitHub Actions builds the APK when this branch changes. The build requires the repository Actions secret `TELEGRAM_BOT_TOKEN`. No URL or `DEVICE_API_KEY` is requested in the app.

## Test procedure

1. Use a disposable test bot token and ensure the bot can message chat `8552540563`.
2. Install the test APK on an Android device.
3. Test Bluetooth/PBAP only with a device whose owner has explicitly agreed and approves the Android PBAP prompt.
4. Use dummy contacts, select only those test entries, and confirm the native Telegram send dialog.
5. Confirm the resulting TXT document in the Telegram chat.

A successful CI build verifies compilation, APK packaging and signature only. It does not prove real Bluetooth/PBAP compatibility or live Telegram delivery; those need physical-device testing. The APK signing key in the workflow is test-only.
