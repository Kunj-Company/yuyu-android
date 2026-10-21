# Releasing the Android app

Everything is built by `.github/workflows/android.yml` ("Android CI") on GitHub
Actions. Nobody needs to build or sign a release on a laptop.

## What each build produces

| Trigger | Tests + lint | Artifacts | Published to Releases |
| --- | --- | --- | --- |
| Pull request, push to `feat/**` or `fix/**` | yes | workflow artifact `yuyu-<version>` | no |
| Push to `main` | yes | workflow artifact | the rolling **`latest`** pre-release |
| Run workflow with a version, or push a `v*` tag | yes | workflow artifact | release **`vX.Y.Z`** |

Each build has a debug APK, a release APK (R8-minified, the one to install), a
release bundle (AAB, for the Play Store) and `SHA256SUMS.txt`. The test and lint
reports are uploaded as a separate `reports-*` artifact on every run.

## How builds are versioned

- **`versionCode`** is the number of commits on the built commit
  (`git rev-list --count HEAD`; CI checks out the full history). Every merge to
  `main` adds commits, so builds of `main` and releases cut from `main` always
  get a higher code than the ones before, whichever trigger produced them. Two
  builds of the same commit share a code. Android refuses to install a lower
  `versionCode` over a higher one, so only release from `main` (or from a tag
  on a commit that is on `main`); a tag pushed on an old commit would get an
  older code than the current `latest`.
- **`versionName`**:
  - releases: the semver from the tag or input, `v1.2.3` → `1.2.3`;
  - `main`: `<last release or 0.1.0>-main.<commits>+<short sha>`, for example
    `0.2.0-main.61+1a2b3c4`;
  - branches and pull requests: the same with the branch name or `pr<number>`,
    for example `0.2.0-feat-offline.58+9f8e7d6`.

  File names use `-` instead of `+` (`yuyu-0.2.0-main.61-1a2b3c4-release.apk`).

Locally the defaults are `versionCode 1` and `0.1.0`; pass
`-Pyuyu.versionCode=… -Pyuyu.versionName=…` to override.

## Where to download

Open the repository's **Releases** page
(`https://github.com/twilighty-abhi/yuyu-android/releases`):

- **`latest`** (marked pre-release): the newest build of `main`, replaced on
  every push to `main`. Its title shows the commit, and its notes say whether it
  is signed with the release key.
- **`vX.Y.Z`**: a versioned release, always signed with the release key.

Download `yuyu-…-release.apk`. Workflow artifacts from any run (including
branches) are under **Actions → the run → Artifacts** and expire after 30 days.

## Cutting a release

1. Make sure `main` is green and contains what you want to ship.
2. Start the release, either way:
   - GitHub: **Actions → Android CI → Run workflow**, enter the version (for
     example `0.2.0`, no `v`) and run it. The branch picker doesn't matter;
     releases always build `main` HEAD.
   - CLI: `gh workflow run android.yml -f version=0.2.0`
3. The run tests, lints and builds `main` with `versionName 0.2.0`, creates the
   tag `v0.2.0` on the built commit and publishes the release with generated
   notes and the APKs, AAB and checksums.

The run fails, without tagging or publishing anything, if the version isn't
semver (`1.2.3` or `1.2.3-rc.1`), if `v<version>` already exists, or if the
signing secrets are missing. A version with a suffix such as `-rc.1` is
published as a pre-release.

Pushing a tag yourself still works the same way:
`git tag v0.2.0 && git push origin v0.2.0` builds that commit and publishes
the release (the tag must point at a commit on `main`, see versioning above).

## Signing secrets

Release builds are signed with the Yuyu release key from four repository
secrets. Without them, branch and `main` builds (including `latest`) are
debug-signed and say so; versioned releases refuse to publish.

| Secret | Value |
| --- | --- |
| `YUYU_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `YUYU_KEYSTORE_PASSWORD` | Keystore password |
| `YUYU_KEY_ALIAS` | Key alias (`yuyu`) |
| `YUYU_KEY_PASSWORD` | Key password |

Create the keystore once, if you don't have one:

```sh
keytool -genkeypair -v -keystore yuyu-release.keystore -alias yuyu \
  -keyalg RSA -keysize 4096 -validity 10000
```

Set the secrets from the repository clone (`gh` prompts for the passwords so
they stay out of your shell history):

```sh
base64 -i yuyu-release.keystore | gh secret set YUYU_KEYSTORE_BASE64
gh secret set YUYU_KEYSTORE_PASSWORD
gh secret set YUYU_KEY_ALIAS --body yuyu
gh secret set YUYU_KEY_PASSWORD
```

(On Linux, use `base64 -w0 yuyu-release.keystore`.) The next push to `main`
publishes a properly signed `latest`. CI checks that a release APK isn't
signed with the debug key when the secrets are set.

**Back up the keystore and its passwords** in a password manager or another
safe place, and never replace it. Android only installs an update signed with
the same key as the installed app: with a new or lost key, every user has to
uninstall (losing the app's local data) and install again. The certificate's
SHA-256 fingerprint is also registered for App Links (`assetlinks.json`) and
Google sign-in, which break if the key changes.

## Installing on a phone

1. On the phone, open the release page and download `yuyu-…-release.apk`.
2. Open it. Android asks to allow installs from that app (your browser or
   Files); allow it under **Settings → Apps → Special app access → Install
   unknown apps**, then go back and install.
3. Later builds install over it as updates, as long as they are signed with
   the same key and have a higher `versionCode`.

**Moving from a debug-signed build:** builds published before the signing
secrets were set are signed with a debug key. A build signed with the release
key can't update them; uninstall the old app once (its local data, such as
saved sign-in and offline tickets, is removed), then install the new one.
After that, updates install normally.
