# ZEUS RED ROOM SECURE

Independent, consent-first continuation of ZEUS RED ROOM Build 90.

## Implemented in the current working branch

- Separate repository; the legacy `Contacts-Web-App` repository remains untouched.
- Existing Build 90 interface, Bluetooth discovery/pairing, PBAP transfer and TXT export are retained as the baseline.
- First-run disclosure and a separate remote-device owner confirmation before PBAP is requested. The remote owner must still approve the Android PBAP/system prompt on their own phone.
- **No automatic contact upload:** after PBAP completes, the operator selects specific contacts and taps the Telegram action. Android then displays a second confirmation naming the device and selected-contact count. Only after that confirmation are those selected contacts queued.
- Encrypted local queue using Android Keystore AES-GCM; if the phone is offline before the request is sent, the encrypted item remains queued for retry.
- Authenticated HTTPS relay to Telegram. Telegram bot token, destination chat ID and device API key remain Cloudflare Worker secrets; none belong in the APK or repository.
- **No D1 database:** the Worker sends directly to Telegram. If a request may have reached Telegram but the response is lost, the operation is quarantined rather than automatically resent, avoiding accidental duplicate contact files.
- In-app consent revocation stops PBAP, clears queued local backups and removes the stored relay configuration.

## Setup

See [Build and test](docs/BUILD_AND_TEST.md) and [Consent and data flow](docs/CONSENT_AND_DATA_FLOW.md).

Deploy `backend/worker.js` as a Cloudflare Worker. Configure `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, and `DEVICE_API_KEY` as Worker secrets only. On first launch, enter the Worker HTTPS URL and the matching `DEVICE_API_KEY`.

## Release status

The current branch is a candidate for CI compilation, not a production release. A successful build proves packaging and signing only; it does not prove Bluetooth/PBAP compatibility or live Telegram delivery. Those require installation and tests on physical Android devices using a test device and dummy contacts. The APK signing key in the existing workflow is test-only.
