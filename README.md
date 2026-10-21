# Yuyu for Android

The native Android app for [Yuyu](https://github.com/twilighty-abhi/yuyu), the
free, self-hosted platform for community events. It covers the phone-first
jobs: signing in to your community's Yuyu server, your registrations and live
tickets (offline too), and organiser day-of work: check-in at the door, guests,
emails, live programme delays and closing sign-ups.

The app talks to the Yuyu mobile API (`/api/mobile/v1`). The contract lives in
[`docs/MOBILE_API.md`](docs/MOBILE_API.md) and
[`docs/mobile-api.openapi.yaml`](docs/mobile-api.openapi.yaml) (authoritative),
with sample responses in [`docs/mobile-api-fixtures/`](docs/mobile-api-fixtures).

## Requirements

- JDK 21 (the Gradle wrapper downloads Gradle 9.8.1)
- Android SDK with platform `android-37` (compile) and build-tools 36
- A `local.properties` with `sdk.dir=/path/to/Android/sdk` (not committed)

Targets Android 8.0 (API 26) and up; `targetSdk` 36.

## Build

```sh
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # R8-minified release APK
./gradlew testDebugUnitTest        # JVM + Robolectric unit tests
./gradlew lintDebug                # Android lint (errors fail the build)
```

Release signing reads `YUYU_KEYSTORE_PATH`, `YUYU_KEYSTORE_PASSWORD`,
`YUYU_KEY_ALIAS` and `YUYU_KEY_PASSWORD` from the environment. Without them,
`assembleRelease` falls back to the debug key so it still builds locally; never
ship such a build.

App Links are verified for one web host, `yuyu.idliapps.com` by default. Build
for another host with `-Pyuyu.appLinkHost=events.example.org` (see
[Setting up a Yuyu server for the app](#setting-up-a-yuyu-server-for-the-app)).

## Connecting to a local Yuyu

Run the Yuyu web app on your computer (`npm run dev`, port 3000), start an
emulator and install a **debug** build. On the "Connect to your Yuyu" screen,
enter:

```
http://10.0.2.2:3000
```

`10.0.2.2` is the emulator's alias for your computer. Plain `http` is accepted
only in debug builds and only for `10.0.2.2`, `localhost` and `127.0.0.1`
(`app/src/debug/res/xml/network_security_config.xml`); release builds are
HTTPS-only. On a physical device, use `adb reverse tcp:3000 tcp:3000` and
`http://localhost:3000`.

Google sign-in appears only when the server's `GET /auth/config` reports it as
enabled with a web client id (see below).

## Setting up a Yuyu server for the app

There is nothing to configure in the app itself: each person types their
server's address (for example `events.example.org` or
`https://events.example.org`) on the "Connect to your Yuyu" screen and signs in.
The operator of the Yuyu server sets up the following.

- **HTTPS with a publicly trusted certificate.** Release builds accept only
  HTTPS and trust only the system CAs
  (`app/src/main/res/xml/network_security_config.xml`), so a self-signed or
  private-CA certificate fails with a network error.
- **`NEXT_PUBLIC_BASE_URL`** set to the same `https://` origin people type in
  the app. The server builds image, event, short-link and certificate URLs
  from it.
- **Google sign-in (optional).** It is offered in the app only when Google
  sign-in is configured on the server, in either place:
  - `AUTH_GOOGLE_ID` and `AUTH_GOOGLE_SECRET` in the server environment, or
  - the client ID and secret saved in **Super admin → Settings** (these win
    over the environment).

  The app asks Google for an ID token for that web client, so the same Google
  Cloud project also needs an **Android** OAuth client for package
  `com.twilighty.yuyu` with the SHA-1 fingerprint of the certificate that
  signed the installed APK. Optionally, set `GOOGLE_MOBILE_CLIENT_IDS` (a
  comma-separated list, environment only, with no Settings field) to that
  Android client ID: the server then accepts only tokens issued to the listed
  clients. If you set it, it must include the Android client ID, or every
  Google sign-in from the app fails.
- **App Links (optional).** So that links to the server open in the app, set
  `ANDROID_APP_SHA256_CERT_FINGERPRINTS` on the server to the SHA-256
  fingerprint of the app's signing certificate (`AA:BB:…` form,
  comma-separated for several; with Play App Signing, use Play's signing key).
  The server then lists it in `/.well-known/assetlinks.json`. The app must
  also be built for that host with `-Pyuyu.appLinkHost=events.example.org`;
  the default build claims only `yuyu.idliapps.com`. Without either, links
  open in the browser, and the app still works through the address typed in.

## Continuous integration and releases

`.github/workflows/android.yml` runs on every push to `main`, `feat/**` and
`fix/**`, on pull requests, and on `v*` tags. It runs the unit tests and lint,
then builds the debug APK, the release APK and the release bundle (AAB), and
uploads them as a `yuyu-<version>` artifact with a `SHA256SUMS.txt`, plus the
test and lint reports.

- **Newest build:** every push to `main` refreshes the `latest` pre-release on
  the [Releases page](https://github.com/twilighty-abhi/yuyu-android/releases).
- **Cut a release:** Actions → Android CI → Run workflow with a version, or
  `gh workflow run android.yml -f version=0.2.0`. It publishes `v0.2.0`.
- `versionCode` is the commit count, so published builds always increase.

See [`docs/RELEASING.md`](docs/RELEASING.md) for versioning, the signing
secrets (keep the keystore backed up and never change it) and installing on a
phone.

## Module map

| Module | What it holds |
|---|---|
| `:app` | `MainActivity` (splash, edge-to-edge, deep links), Hilt application, top-level navigation and bottom bar (Tickets, Organise, Account) |
| `:core:common` | `AppResult`/`AppError`, dispatchers, `EventTimeFormatter` (event-timezone date styles), `AppearanceMode`, `AppBuildInfo` |
| `:core:designsystem` | `YuyuTheme`, colour/type/spacing/radius/elevation/motion tokens, event accent maths (`AccentMath`, `EventAccentScope`), brand (`DotMark`, `DotLockup`, `DotFace`, `DotLoader`), components, bundled fonts, debug gallery |
| `:core:network` | Retrofit APIs (`YuyuMobileApi`, `YuyuWebApi`, `StationApi`), DTOs for the whole contract, error envelope parsing, runtime base URL, bearer + refresh authenticator, SSE ticket client, station cookie-jar clients, Coil image loader |
| `:core:database` | Room: cached own tickets, offline check-in roster, offline check-in queue (schemas exported to `core/database/schemas`) |
| `:core:data` | Repositories (Auth, Me, PublicEvents, OrganiserEvents, Guests, CheckIn, Station, Broadcasts, DayOf), the Keystore-encrypted session store, server store, preferences, offline check-in sync worker |
| `:feature:auth` | Server setup, sign-in (email/password, two-step code, Google), Account |
| `:feature:attendee` | My tickets, discover, event page, registration, live ticket, feedback |
| `:feature:organiser` | Organisation events, event hub, guests, guest detail, day-of controls |
| `:feature:checkin` | Organiser scanner, door-station PIN unlock, door station |
| `:feature:broadcasts` | Compose, preview and history of emails to guests |

Shared Gradle setup lives in `build-logic/` (convention plugins
`yuyu.android.application`, `yuyu.android.library`, `yuyu.android.compose`,
`yuyu.android.feature`, `yuyu.hilt`) and versions in
`gradle/libs.versions.toml`.

## Contributing / feature modules

- **Stay in your module.** Each feature exposes `NavGraphBuilder.xxxGraph(...)`
  and its `@Serializable` routes in `feature/<name>/.../navigation/`. Replace
  the placeholder `composable<…>` entries there; `:app` already wires every
  graph. Feature modules never depend on each other: to leave your feature, use
  a callback the graph function receives (e.g. `OrganiserExternalNavigation`),
  and add one in `:app` if you need a new exit.
- **Data comes from repositories** in `:core:data` (inject the interface into a
  `@HiltViewModel`). They return `AppResult<T>` with a typed `AppError`; show
  `error.message`, it is always UI-safe. Don't duplicate server rules beyond UI
  hints (`EventPermissions` only hides controls).
- **MVVM**: immutable `UiState` in a `StateFlow`, collected with
  `collectAsStateWithLifecycle()`. Keep text-field values in the composable or
  in snapshot state rather than round-tripping each keystroke through a flow.
- **Design system first.** Use `YuyuTheme.colors/typography`, the components in
  `core/designsystem/component`, `EventAccentScope(event.accentColor)` on
  event-facing screens only, and `EventTimeFormatter` with the event's
  timezone. Never hard-code hex values. Check light and dark in the debug
  gallery (Account → Design system).
- **Secrets**: never log or persist access/refresh tokens, ticket tokens or
  station PINs in clear. DTOs holding them override `toString()`.
- **Contract changes**: copy the new fixtures into
  `core/network/src/test/resources/fixtures/`; `FixtureContractTest` decodes
  every fixture strictly, so DTO drift fails the build.
- Branch per feature (`feat/<name>`), merge back with `--no-ff`, and run the
  four Gradle commands above before merging.
