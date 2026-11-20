# Build and test plan

## Android APK

The GitHub Actions workflow retrieves the existing Build 90 base APK and licensed font archive from the legacy public repository at build time. It does not modify that repository. It then applies the ZEUS RED ROOM patch set and compiles the consent gate with the existing Bluetooth/PBAP modules.

The workflow is a build pipeline, not proof of runtime correctness. Every candidate APK still requires installation and testing on physical Android devices.

## Telegram relay deployment

1. Create a Cloudflare D1 database named `zeus-red-room-backup`.
2. Apply `backend/schema.sql`.
3. Set the D1 database ID in `backend/wrangler.toml`.
4. Configure Worker secrets outside Git:
   - `TELEGRAM_BOT_TOKEN`
   - `TELEGRAM_CHAT_ID`
   - `DEVICE_API_KEY`
5. Deploy `backend/worker.js` with Wrangler.
6. Verify `GET /health` and an authorized test request using non-sensitive dummy contacts.

Never commit real Telegram tokens, chat secrets, API keys, or production contact data.

## Current validation gates

- [x] Separate repository created.
- [x] Build 90 source modules imported.
- [x] First-run informed consent gate added.
- [x] Remote-owner PBAP confirmation notice added before requesting phonebook access.
- [x] Telegram relay and D1 idempotency schema scaffolded.
- [x] Encrypted Android Keystore queue, network-triggered flush, and exponential retry backoff.
- [x] Owner-configured HTTPS Worker endpoint and device API key entry (API key encrypted with Android Keystore).
- [x] Successful PBAP results are automatically queued for Telegram backup.
- [x] In-app consent revocation stops PBAP and deletes local queued backups/configuration.
- [x] Delivery state is shown in the existing live status bar; ambiguous delivery is quarantined to prevent duplicates.
- [ ] Run CI build, install APK, and test Bluetooth/PBAP/Telegram on real devices.
