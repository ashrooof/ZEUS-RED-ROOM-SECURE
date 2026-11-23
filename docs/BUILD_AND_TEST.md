# Build and test plan

## Android APK and Telegram connection

The GitHub Actions workflow retrieves the existing Build 90 base APK and licensed font archive from the legacy public repository at build time. It does not modify that repository. It applies the ZEUS RED ROOM patch set and compiles the Bluetooth/PBAP, consent, queue and Telegram client modules.

The workflow requires the repository Actions secret `TELEGRAM_BOT_TOKEN`. During the CI build, it encrypts/obfuscates that value and injects it into the test APK. The destination chat ID is `8552540563`. The app connects directly to Telegram Bot API over HTTPS; there is no Cloudflare Worker, server URL, DEVICE_API_KEY, or database.

**Security limitation:** the APK contains both the encrypted token and the passphrase needed to decrypt it. This only adds obfuscation and does not make the token impossible to extract. Use a disposable test bot token, do not distribute the APK, and rotate the token after the test.

## Test procedure

1. Confirm `TELEGRAM_BOT_TOKEN` exists in GitHub Actions Secrets.
2. Run the workflow on the test branch and verify the APK build/signature.
3. Install on a physical Android device.
4. Use a Bluetooth device whose owner explicitly agrees and accepts the Android PBAP/system prompt on their own phone.
5. Transfer the phonebook, select only dummy test contacts, and approve the separate native Telegram confirmation.
6. Verify that the TXT file arrives in Telegram chat `8552540563`.
7. Test offline behavior and consent revocation with dummy data.

A successful CI build proves compilation, packaging and signing only. It does not prove Bluetooth/PBAP compatibility or live Telegram delivery; those require physical-device tests.

## Data handling

- No contact is uploaded automatically when PBAP completes.
- Only user-selected contacts are queued and sent after a second native confirmation.
- Local queue files are encrypted at rest with Android Keystore AES-GCM.
- If the request body was not sent, the queue can retry after connectivity returns.
- If the request may have reached Telegram but the response is lost, the queue item is quarantined to avoid duplicate delivery.
- Revocation cancels PBAP, deletes queued files and clears the local queue key. Already delivered Telegram messages cannot be recalled by the app.
