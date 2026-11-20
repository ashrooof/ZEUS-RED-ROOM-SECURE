# ZEUS RED ROOM SECURE

Independent, consent-first continuation of ZEUS RED ROOM.

## Project principles

- The legacy `Contacts-Web-App` repository is not modified.
- Preserve the ZEUS RED ROOM Build 90 visual reference and Bluetooth pairing/PBAP flow.
- First-run consent is explicit, unselected by default, and describes each data category and the Telegram destination.
- Refusal keeps transfer and backup features locked.
- Before reading a remote phonebook, show a separate transfer notice and require the remote device owner to approve the PBAP access prompt on that device. Never bypass or suppress that system prompt.
- Telegram backup is opt-in per clearly described scope; queued backups are encrypted at rest, retry safely, and expose delivery status and deletion controls.
- No Telegram bot token, chat secret, or private key is stored in the APK, source code, build logs, or public repository. A backend secret store is required.
- No hidden collection, background phonebook reads, or silent uploads.

## Planned layout

- `android/` — Android application and ZEUS UI/Bluetooth integration.
- `backend/` — authenticated Telegram relay API; secrets configured only in the deployment provider.
- `docs/CONSENT_AND_DATA_FLOW.md` — consent wording, data map, retention and revocation.
- `docs/BUILD_AND_TEST.md` — reproducible build and device test plan.

## Status

Repository bootstrap only. No APK has been built or released yet. The original Build 90 source is being audited before porting so that Bluetooth behavior and visual assets are preserved rather than replaced with an unrelated implementation.
