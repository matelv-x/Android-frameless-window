# Release signing

`stargate-release.jks` is the encrypted permanent signing key for the application.

Never store its passwords in this directory or in Git history. They are stored as GitHub Actions Secrets. Losing the key or its passwords will make it impossible to sign updates that are compatible with previously installed versions.
