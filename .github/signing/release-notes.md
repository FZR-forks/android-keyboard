This custom build preserves the fork's multilingual layout grouping, tablet toolbar behavior, and configurable spacebar swipe sensitivity.

## Upstream changes

Includes all upstream `master` changes through `7d61ab4132cfcbb7aa713190d934f0125dc38b03` (November 2, 2026), with the fork's customizations preserved.

- Updated the swipe context language model.
- Fixed apostrophe handling in swipe context and phantom spacing after a colon.
- Improved English “i” autocorrection with dictionaries missing that shortcut.
- Fixed fuzzy Pinyin rules overriding the configured spelling rules.
- Added upstream ATL compatibility and developer diagnostics, and handled missing screenshot volumes.
- Kept multilingual layout grouping, hardware-keyboard toolbar behavior, configurable spacebar swipe sensitivity (32 dp default), and private release signing.

## Private release signing

- Release APKs now use a private RSA-4096 key stored in GitHub Actions Secrets.
- The publicly shared keystore has been removed from the current source tree. It was already public and remains compromised; deleting it cannot revoke copies or Git history.
- The APK carries a verified signing-certificate rotation proof. Android 9+ can update the previous shared-key release in place and retain its app data. Rollback, shared UID, signature permissions, and authenticator access are disabled for the old key.
- The keyboard's private broadcast permission has a new name, avoiding the old certificate's permission ownership conflict without restoring its privileges.
- The release pipeline checks migration on Android 9 and Android 15, including data retention and rejection of an update signed with the exposed key.
- **These custom stable releases require Android 9 or newer.** We do not keep signing older Android builds with the compromised key.

## Updating and settings

First export a backup in the currently installed keyboard: **Settings → Miscellaneous → Export configuration**. Save the `.backup` file outside the app's own storage.

Then install this APK over the previous custom release. **Do not uninstall first.** If the installed version is the previous fork release signed with the shared key, Android 9+ should preserve your settings, dictionaries, themes, and app data automatically. Obtainium can keep tracking this same repository.

If Android reports a signature conflict (for example, your installed APK used a different signing key), enable another keyboard, export the backup, uninstall the old keyboard, install this release, and use **Settings → Miscellaneous → Import configuration**. Re-enable the keyboard in Android settings afterward. Android 7/8 cannot install this release.

The release includes `SHA256SUMS`, the public release certificate, and verification output. These public files contain no private signing key.
