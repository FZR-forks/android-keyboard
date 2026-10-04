# Release signing

Repository Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.

The build job has no signing Secrets. It produces an unsigned APK. A separate signing job decodes the keystore to runner temporary storage, signs the APK, verifies its certificate against `release-cert.pem`, and removes the temporary keystore. Only the publish job receives `contents: write`. All third-party actions are pinned to commit SHAs, and releases only run from `my-version`.

`release.lineage` is a public Android APK signing-certificate rotation proof, not a private key. Its first certificate is the exposed former shared debug signer; its last certificate is the private release signer. The old signer has only the installed-data capability. Rollback, shared UID, signature permission, and authenticator capabilities are disabled. The new key signs all supported platform versions; stable APKs require API 28 and use rotation starting at API 28.

The release uses APK Signature Scheme v3 only. The v1/v2 compatibility signatures require the original signer when a lineage is present, so they are disabled rather than reusing the exposed key. APK v3 is supported by every supported platform (Android 9+).

The manifest removes AndroidX's old `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and defines `PRIVATE_SIGNER_RECEIVER_PERMISSION` instead. Private receiver registration supplies this new signature permission explicitly. This avoids the duplicate-permission install failure during rotation without granting the compromised signer permission capabilities. Emulator checks also start Settings and the keyboard service to catch receiver-registration regressions.

GitHub Secrets are write-only. Keep an encrypted offline backup of the private keystore and password before migrating or replacing the repository. Reuse the same private key and lineage for subsequent releases; generating a new key on each run breaks updates.

Local development uses Gradle's standard machine-specific debug keystore. Local release builds are unsigned by default. `keystore.properties` remains supported for explicit local private-key signing, but only the GitHub release pipeline adds the checked-in migration lineage. Do not distribute a local signed build as a release without that lineage.

Do not restore `java/shared.keystore` when merging future upstream changes. The old key was publicly available, so removing it from this branch cannot erase copies, upstream history, or old source archives. Security comes from rotating away from it and rejecting it after migration.
