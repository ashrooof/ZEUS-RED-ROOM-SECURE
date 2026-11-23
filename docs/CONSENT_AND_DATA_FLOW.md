# Consent and data flow

## First-run app-user consent

The first launch shows a blocking disclosure. The user must actively choose **أوافق وأتابع**; choosing **أرفض وأغلق التطبيق** closes the app and leaves transfer features unavailable.

The disclosure explains that:
- The app requests contacts only through the remote device's Bluetooth PBAP service.
- The remote phone owner must separately approve the Android PBAP/system request on their own device.
- No contacts are uploaded automatically after PBAP.
- The operator must select contacts and activate the Telegram action, then approve a native confirmation naming the source device and selected-contact count.
- Only selected contacts are sent directly to the Telegram chat configured in this test build.
- If the phone is offline before a request is sent, the selected payload is encrypted at rest and retained for retry.

## Remote-device owner approval

The app user's consent does not authorize access to another person's address book. Before PBAP starts, ZEUS displays a reminder to obtain the remote owner's approval. The app must not suppress, bypass, automate, or misrepresent the remote Android PBAP permission prompt. If the remote owner declines, the transfer stops and nothing from that phonebook is queued or uploaded.

## Telegram export rules

- PBAP transfer only reads the phonebook after the separate remote-owner confirmation flow and system-level PBAP access.
- No automatic upload occurs when PBAP completes.
- Only contacts explicitly selected in the UI are eligible for Telegram export.
- A second Android confirmation shows the source device and selected-contact count; canceling it creates no queue item.
- Queue payloads are encrypted at rest with a key protected by Android Keystore.
- The test app sends directly to Telegram Bot API. No relay server or database is used.
- A failure before the request body is sent can retry from the encrypted queue. An ambiguous result after sending is quarantined rather than blindly retried.

## Credential handling

The test workflow reads `TELEGRAM_BOT_TOKEN` from GitHub Actions Secrets and injects it into the APK in encrypted/obfuscated form. The passphrase is also part of the APK, so this is not strong credential protection. Use only a disposable test token and rotate it after testing. The chat ID is `8552540563`.

## Revocation and deletion

Revocation cancels PBAP, deletes locally queued payloads and clears the local encryption key. Already delivered Telegram messages cannot be recalled by the app; the user is told this before revocation.

## Validation status

CI packaging/signing is not a substitute for physical-device tests. Bluetooth discovery, PBAP owner prompts, selected-only export, offline retry, Telegram delivery, and revocation must be tested on physical Android devices with dummy contacts before release.
