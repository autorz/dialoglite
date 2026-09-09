# Dia Log Lite

Monorepo for **Dia Log Lite**, a lightweight, privacy-first time tracking and
overtime tool.

| Directory | What it is | Docs |
|---|---|---|
| [`server/`](server/) | The Flask/ASGI web app + MCP server. Published as the container image `ghcr.io/autorz/dialoglite`. | [`server/README.md`](server/README.md) |
| `android/` | Android client. Published as an APK asset on each GitHub release. | `android/README.md` |

Everything else at the repository root is shared across the whole monorepo:
[`CHANGELOG.md`](CHANGELOG.md) (one timeline — releases are cut at the repo
level and ship both artifacts), [`.gitignore`](.gitignore), and
[`.github/`](.github/) (CI).

> [!WARNING]
> **Security notice:** the server has **no built-in authentication**, and its
> MCP endpoint at `/mcp` grants full read/write access to your data. Deploy it
> behind an identity-aware proxy. See [`server/README.md`](server/README.md)
> for the full notice.

## Quick start

```bash
cd server
docker compose up -d   # dashboard on http://localhost:8000
```

Or use the pre-built multi-arch image (`linux/amd64` + `linux/arm64`):
`ghcr.io/autorz/dialoglite:latest`. Deployment recipes live in
[`server/README.md`](server/README.md).

## Releases and CI

A single workflow, [`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml),
runs on `release: published` (and manually via `workflow_dispatch`). It has two
**independent** jobs that run in parallel:

| Job | Produces | Build context |
|---|---|---|
| `build-and-push` | Multi-arch image pushed to `ghcr.io/autorz/dialoglite` (tags: `X.Y.Z`, `X.Y`, `sha-…`, `latest`) | `./server` |
| `build-android-apk` | `dialoglite-<tag>.apk` uploaded as an asset of the release | `./android` |

The Android job runs `./gradlew testDebugUnitTest assembleRelease` on JDK 21,
with `platforms;android-36` and `build-tools;36.0.0` installed explicitly
rather than assumed from the runner image.

Two properties are deliberate and should not be "simplified" away:

- **The image job is the priority.** The container image is what runs in
  production, so the APK job is a separate job with no `needs:` on it and is
  marked `continue-on-error: true`. An Android build breaking can never turn a
  good image publish into a failed release.
- **The APK job skips gracefully when `android/` is absent.** It probes with
  `hashFiles('android/**')` and, finding nothing, emits a `::notice::` and
  exits green instead of failing red.

The image job's build context is `./server`, not the repository root — see
[Repository layout](#repository-layout) below for why that matters.

### Signing the APK

The workflow signs the release APK with a keystore supplied through **GitHub
Actions secrets**:

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore file, base64-encoded on a single line |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Password of that key |

> [!WARNING]
> **If those secrets are not configured, the APK is signed with Android's
> throwaway debug key.** The job still succeeds and still attaches the APK, but
> it prints a `::warning::` in the log and the file is named
> `dialoglite-<tag>-debug-signed.apk`. A debug-signed APK is fine for sideload
> testing and **must not** be published as a real release: the debug key is
> public, anyone can forge an update, and Google Play rejects it. A debug-signed
> build can also never be upgraded in place to a properly signed one — Android
> refuses an update whose signature changed, so users have to uninstall first.

**The workflow is the single signing authority.** `app/build.gradle.kts` can
sign by itself when it sees `DIALOGLITE_KEYSTORE*` in the environment, but CI
deliberately does **not** export those: letting Gradle sign would create two
signing paths and two possible output filenames. Gradle always emits
`app-release-unsigned.apk`, and the workflow zipaligns and signs it with
`apksigner`. One place to audit, and `apksigner` never has to re-sign an
already-signed APK. (Building locally with those variables set still works —
that path is just not what CI uses.)

**No keystore is stored in this repository, and none should ever be committed**
(`*.jks` / `*.keystore` are in [`.gitignore`](.gitignore)). Generate one
locally, keep it in your password manager, and load it into the repo secrets:

```bash
# 1. Create the keystore (25+ years, so it outlives the app).
keytool -genkeypair -v \
  -keystore dialoglite-release.jks \
  -alias dialoglite \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12

# 2. Encode it for the secret — -w0 keeps it on ONE line; without it,
#    base64 wraps at 76 columns and the decode step in CI fails.
base64 -w0 dialoglite-release.jks > dialoglite-release.jks.b64

# 3. Store it (or paste the file's contents in Settings → Secrets → Actions).
gh secret set ANDROID_KEYSTORE_BASE64 < dialoglite-release.jks.b64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD

# 4. Delete the base64 copy; keep the .jks itself backed up somewhere safe.
shred -u dialoglite-release.jks.b64
```

Losing the keystore means you can never ship an in-place update to anyone who
installed a build signed with it. Back it up.

### This is a public repository

Release assets and workflow logs are world-readable. The APK job is written
accordingly:

- The keystore is decoded into `$RUNNER_TEMP` (outside the checkout, so it can
  never be picked up by Gradle as a resource or committed), and deleted in an
  `always()` cleanup step.
- Passwords reach `apksigner` through `env:` references, never as command-line
  arguments or echoed values.
- After building, a scan step greps the APK for the keystore password and for
  private hostnames/addresses, and **fails the job** if it finds the password,
  a private key, or a GitHub token. Do not put internal endpoints, tokens, or
  default credentials in the Android sources — assume everything shipped in the
  APK is public.

### Accepted internal-address patterns

Internal addresses (mesh IPs, LAN IPs, own hostnames) are a *warning*, not a
failure — some are deliberate. But a warning that fires on every single release
becomes noise, and then the `192.168.x` that shows up by accident six months
from now scrolls past unnoticed.

So deliberate ones get written down in
[`.github/apk-scan-accepted.txt`](.github/apk-scan-accepted.txt) — pattern, who
accepted it, when, and why. A match that is registered there prints a
`::notice::` with the reason; anything unregistered still prints a loud
`::warning::`.

**A registered pattern that no longer matches anything fails the job.** An
orphaned acceptance is the same silent debt the file exists to prevent: when the
string leaves the app, its line has to leave with it.

## Repository layout

```
.
├── .github/workflows/   CI: image + APK, both released together
├── CHANGELOG.md         one timeline for the whole repo
├── server/              Flask app — Docker build context lives HERE
└── android/             Android client
```

The Docker build context is `server/`, not the root. The `Dockerfile` ends with
`COPY . /app`, so a root context would drag `android/` (Gradle caches, build
outputs, and any keystore sitting in a developer's working tree) into the
published image. Keeping `.dockerignore` and the `Dockerfile` next to the code
they describe keeps that blast radius closed.
