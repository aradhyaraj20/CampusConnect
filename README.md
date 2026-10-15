# CampusConnect

An offline-first Android app that brings campus life into one place: discover events, RSVP with a waitlist, check in
with QR tickets, chat, run polls, trade in a marketplace, swap skills and compete on a course leaderboard. Three roles
(student, organizer, admin) each get their own experience, and an optional Gemini-powered assistant helps with event
search and event creation, with a local fallback when it is unavailable.

There is no backend server. All data lives on the device in Room/SQLite, which keeps the app easy to demo and easy to
reason about.

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Demo accounts](#demo-accounts)
- [Build, test and release](#build-test-and-release)
- [Architecture](#architecture)
- [Reliability and security](#reliability-and-security)
- [Known limitations](#known-limitations)
- [Documentation](#documentation)

## Features

### Students
- Natural-language event search with explanations of why each result matched
- RSVP with automatic waitlist promotion when a seat opens
- QR tickets, event reminders and add-to-calendar
- Event chat, polls, marketplace, and skill exchange with karma
- Course-scoped leaderboard and badges

### Organizers
- Create events manually or by describing them to the AI assistant
- Event photos, edit and delete with attendee notifications on date or location changes
- QR attendance scanner for check-in
- Email attendees and export the attendee list as CSV

### Admins
- Paged user management with search and role filters
- Moderation: warn, suspend, remove or dismiss reports
- Analytics dashboard and admin-controlled ads
- Cleanup of inactive accounts after a course ends

### Everywhere
- Offline banner, loading and empty states, dark mode
- Local parser and database Q&A when Gemini is not configured or is down

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, XML views, Material 3 |
| Architecture | MVVM with use cases and repositories |
| Storage | Room (SQLite) with migrations |
| Background work | WorkManager |
| QR | ZXing Android Embedded |
| Images | Coil |
| AI | Gemini (optional) with local fallback |
| SDK | minSdk 24, targetSdk / compileSdk 35 |

## Getting started

1. Install Android Studio and open this folder.
2. Make sure `local.properties` contains your `sdk.dir`.
3. Sync Gradle and build the **debug** variant.
4. Run on an emulator or device (API 24+).

Or from the command line:

```
./gradlew assembleDebug
```

The app works with no internet and no Gemini key.

## Configuration

Settings are read from `local.properties`. Never commit real keys or passwords.

### Gemini (optional)

```
GEMINI_API_KEY=your_key_here
GEMINI_MODEL=gemini-2.5-flash
```

If the key is empty or the service is unreachable, the app falls back to the local parser and database Q&A.

### Release

```
TICKET_HMAC_SECRET=<long random string>      # required for release
RELEASE_STORE_FILE=C\:\path\to\key.jks       # required for release
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
ADMIN_INITIAL_PASSWORD=<8+ chars>            # seeds admin@campus.edu in release
ORGANIZER_INVITE_CODE=<code>                 # blank = organizer self-signup disabled
```

## Demo accounts

Seeded in **debug builds only**. Release builds never create these.

| Role | Email | Password |
|---|---|---|
| Student | aradhya@email.com | password |
| Organizer | cs@campus.edu | password |
| Admin | admin@campus.edu | password |

## Build, test and release

```
./gradlew assembleDebug testDebugUnitTest     # build and unit tests
./gradlew connectedDebugAndroidTest           # Room migration test (needs emulator or device)
./gradlew :app:assembleRelease                # release build (see Configuration)
```

## Architecture

Source lives under `app/src/main/java/com/example/campusconnect`:

| Package | Role |
|---|---|
| `activities`, `fragments`, `adapters` | Screens and list rendering |
| `viewmodel`, `presentation` | UI state |
| `domain` | Use cases (RSVP, event save, moderation) |
| `repository`, `data`, `database` | Room entities, DAOs, migrations and repositories |
| `search` | Natural-language search and Gemini integration |
| `worker` | WorkManager jobs (reminders, account cleanup) |
| `core`, `utils` | Shared helpers such as date formatting |

`AppContainer` wires dependencies together. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for role-based access
control, security design and limitations.

## Reliability and security

- **Atomic RSVP and check-in:** join, leave with waitlist promotion, QR check-in and moderation removal each run in a
  single database transaction, so seat counts stay consistent.
- **Validated event editing:** create and edit are checked and authorised (trimmed fields, positive capacity, no past
  dates, capacity not below the going count).
- **Safer moderation:** admins cannot be warned, suspended or re-roled, and screens show the real outcome.
- **Authentication:** PBKDF2-SHA256 password hashing, login lockout and lockout-aware password changes.
- **Access control:** organizer sign-up requires an invite code; demo accounts exist only in debug builds.
- **Gemini hardening:** calls run off the main thread with short timeouts, and untrusted text is delimited in prompts.

## Known limitations

- Single-device, offline-first design: there is no server, sync or push notification service.
- The QR ticket signing secret is shared across installs, and the Gemini key is embedded in the APK. A production
  version would move both behind a backend.

## Documentation

- [Demo script](docs/DEMO_SCRIPT.md)
- [Architecture, RBAC, security, limitations](docs/ARCHITECTURE.md)
- [Deployment notes](DEPLOYMENT.md)
- [Roadmap](ROADMAP.md)
- [Change history](CHANGES.md)
