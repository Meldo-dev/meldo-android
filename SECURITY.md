# Security

Found a security problem in Meldo? Please don't open a public issue. Email **contact@meldo.dev** with what you found and how to reproduce it. You'll get an answer within a week.

Meldo has no server, so the interesting surfaces are on the device: the encrypted Google Drive sync (`integration/drive/…/data/DriveSync.kt`), the OAuth flows, the Keystore-backed `SecureStore`, and what other apps can reach (exported components, widgets, deep links, the Quick Settings tile).

Please test only against your own accounts and devices. Don't probe Microsoft or Google servers.
