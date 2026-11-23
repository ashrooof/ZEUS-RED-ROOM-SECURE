# Build and test plan

## Android APK

The GitHub Actions workflow retrieves the existing Build 90 base APK and licensed font archive from the legacy public repository at build time. It does not modify that repository. It applies the ZEUS RED ROOM patch set and compiles the Bluetooth/PBAP, consent, backup and WebView bridge modules.

The workflow is a packaging pipeline, not proof of runtime correctness. Every candidate APK still requires installation and testing on physical Android devices.

## Direct Telegram relay deployment (no database)

1. Deploy `backend/worker.js` as a Cloudflare Worker.
2. Configure these Worker secrets outside Git:
   - `TELEGRAM_BOT_TOKEN`
   - `TELEGRAM_CHAT_ID`
   - `DEVICE_API_KEY`
3. Deploy the Worker and verify `GET /health`.
4. On first app launch, enter the Worker HTTPS URL and the same `DEVICE_API_KEY`.
5. Use only a test device and dummy contacts for the first authorized send.

No D1 database or SQL migration is required. If Telegram explicitly rejects a request with a retryable response, the encrypted client queue can retry. If the request may have been accepted but its response is lost, the item is quarantined to avoid sending duplicate contact files; inspect Telegram before deciding what to do.

Never commit real Telegram tokens, chat secrets, API keys, or production contact data. If a token was ever pasted into chat, logs, or a public repository, rotate it with BotFather.

## Current validation gates

- [x] Separate repository created; legacy repository untouched.
- [x] Existing Build 90 UI and Bluetooth/PBAP modules retained.
- [x] First-run informed consent gate.
- [x] Remote-device owner reminder and native PBAP/system approval flow.
- [x] Automatic PBAP-to-Telegram enqueue removed.
- [x] Explicit contact selection and a second native confirmation before Telegram enqueue.
- [x] Encrypted Android Keystore queue.
- [x] Direct authenticated Worker-to-Telegram relay without D1.
- [x] Ambiguous sends quarantined to avoid duplicate delivery.
- [x] Consent revocation stops PBAP and deletes local queued backups/configuration.
- [ ] CI build and APK signature/package verification on this branch.
- [ ] Install and test Bluetooth discovery/PBAP on physical Android devices.
- [ ] Verify selected-only Telegram delivery using dummy contacts.
- [ ] Verify offline queue retry and revocation on a physical device.
