# Consent and data flow

## First-run app-user consent

The first launch must show a blocking disclosure with no preselected checkbox or implicit acceptance. The user must actively choose **أوافق وأتابع**. Choosing **أرفض وأغلق التطبيق** closes the app and leaves transfer features unavailable.

The disclosure identifies:
- Phonebook contacts returned by the remote device's Bluetooth PBAP service.
- Remote device name, contact count, operation timestamp and delivery status.
- Telegram as the destination, controlled by the app owner.
- Encrypted offline queueing and automatic retry after connectivity returns.
- The fact that the remote phone owner must separately approve the PBAP/system request on their own device.

## Remote-device owner approval

The app user's consent does not authorize access to another person's address book. Before PBAP starts, ZEUS must tell the operator to obtain the remote owner's approval. The app must not suppress, bypass, automate, or misrepresent the remote Android PBAP permission prompt. If the remote owner declines, the transfer stops and nothing from that phonebook is queued or uploaded.

## Telegram backup rules

- Send only contacts successfully returned after the remote owner approved PBAP.
- The operator must be shown which device and how many contacts are about to be backed up.
- The owner can cancel before queueing.
- Keep a delivery state per operation; do not silently discard failed items.
- Queue payloads encrypted at rest with a key protected by Android Keystore.
- Use an idempotency key on the backend to avoid duplicate Telegram messages.
- Keep Telegram bot credentials exclusively in the backend secret store. Never put them in the APK, Git history, workflow logs, or client configuration.

## Revocation and deletion

Revocation must stop new reads and uploads, cancel pending retries, and offer deletion of queued local payloads. Already delivered Telegram messages cannot be recalled by the app; the user must be told this before revocation.

## Implementation status

The repository currently contains the first-run disclosure gate. Remote-owner confirmation, encrypted offline queue, authenticated backend relay, Telegram delivery, revocation UI, and device-level tests are not yet complete. Do not ship an APK until all are implemented and tested.
