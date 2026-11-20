# ZEUS RED ROOM SECURE

Independent, consent-first continuation of ZEUS RED ROOM Build 90.

## Implemented

- Separate repository; the legacy `Contacts-Web-App` repository remains untouched.
- Existing Build 90 UI, Bluetooth discovery/pairing, PBAP, and TXT export source imported as the visual and functional baseline.
- Explicit first-run disclosure. Refusal closes the app and blocks its features.
- Separate local confirmation before requesting remote PBAP access; the remote device owner must still approve the Android PBAP/system prompt on their own phone.
- Automatic encrypted local queue after a successful PBAP transfer.
- Android Keystore AES-GCM encryption for queued contact payloads and the device API key.
- HTTPS relay to Telegram. Bot token and destination chat ID live only in Cloudflare Worker secrets, never in the APK or repository.
- D1 operation ledger with idempotency. Definite failures may retry; ambiguous delivery is quarantined rather than blindly resent.
- Network-triggered retry and exponential backoff.
- In-app consent revocation stops transfer, clears local queued backups, and removes the stored relay configuration.

## Setup

See [Build and test](docs/BUILD_AND_TEST.md) and [Consent and data flow](docs/CONSENT_AND_DATA_FLOW.md).

The app requires a deployed Cloudflare Worker, D1 database, and owner-configured relay URL plus `DEVICE_API_KEY`. Configure `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, and `DEVICE_API_KEY` as Worker secrets only.

## Release status

Implementation is in progress. The first clean CI APK build has succeeded. The APK is still a test build: Bluetooth/PBAP behavior has not yet been validated on physical devices, and Telegram delivery requires deploying the Worker/D1 backend and entering its URL plus DEVICE_API_KEY on first launch. Do not treat it as a production release until those device and live-backend tests pass.
