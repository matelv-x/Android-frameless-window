# Release signing

The permanent release keystore is not stored in this repository. It is encoded and stored as the protected GitHub Actions Secret `ANDROID_KEYSTORE_BASE64`.

The release workflow restores the keystore to `signing/stargate-release.jks` only for the duration of a GitHub Actions build. Its passwords and alias are stored in separate GitHub Actions Secrets. Never commit the generated keystore or any signing credentials to Git history.

Losing the key or its passwords will make it impossible to sign updates that are compatible with previously installed versions.
