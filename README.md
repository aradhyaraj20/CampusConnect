# CampusConnect

An offline-first Android app that brings campus life into one place: discover events, RSVP with a waitlist, check in
with QR tickets, chat, vote in polls, share event photos and compete on a course leaderboard. Three roles
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
- **Home:** "My Events" shows the upcoming events you have joined, then personalised "For You" picks, live flash
  sessions and every other upcoming event (joined ones are not repeated)
- **Groups:** study groups you have not joined yet, plus a course-scoped karma leaderboard
- **My Events tab:** a calendar of events; tapping an empty day says so instead of showing a blank list. The tab badge
  counts your upcoming events
- Natural-language event search with explanations of why each result matched
- RSVP with automatic waitlist promotion when a seat opens
- QR tickets, per-user event reminders and add-to-calendar
- Event chat and polls (one vote each, with optional deadlines)
- Event memories: attendees add photos once the event has started
- Location screen that opens the venue in the maps app
- Notifications with unread markers and timestamps

### Organizers
- Sign up directly by choosing **Organizer** on the Register screen
- Create events manually or by describing them to the AI assistant (both go through the same validation)
- Event cover photos, edit and delete with attendee notifications on date or location changes
- Open polls for attendees, with an optional voting deadline
- QR attendance scanner for check-in
- Email attendees and export the attendee list as CSV

### Admins
- Paged user management with search and role filters
- Moderation: warn, suspend, remove or dismiss reports
- Analytics dashboard and admin-controlled ads
- Cleanup of inactive accounts after a course ends

### Everywhere
- Material 3 design: collapsing headers, one compact event-card style, consistent icons, type scale and spacing
- Light and dark mode that follow the phone's setting by default (can be forced in Settings)
- Inline form errors and Snackbars, loading indicators and empty states on every list
- Landscape and tablet layouts for lists and the QR ticket
- Offline banner, and a local parser plus database Q&A when Gemini is not configured or is down

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, XML views, Material 3 |
| Architecture | MVVM with use cases and repositories |
| Storage | Room (SQLite), schema v19, with tested migrations |
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
ADMIN_INITIAL_PASSWORD=<8+ chars>            # seeds admin@campus.edu in release; must be changed at first login
```

## Demo accounts

Seeded in **debug builds only**. Release builds never create these.

| Role | Email | Password |
|---|---|---|
| Student | aradhya@email.com | campus123 |
| Organizer | cs@campus.edu | campus123 |
| Admin | admin@campus.edu | campus123 |

New users pick **Student** or **Organizer** on the Register screen. Admin accounts can't be self-registered.
If an app update ever fails to open with an old local database, clear the app's data (debug builds reseed the demo
content on next launch).

## Build, test and release

```
./gradlew assembleDebug testDebugUnitTest     # build and unit tests
./gradlew connectedDebugAndroidTest           # Room migration tests, v16 -> v19 (needs emulator or device)
./gradlew :app:assembleRelease                # release build (see Configuration)
```

## Architecture

Source lives under `app/src/main/java/com/example/campusconnect`:

| Package | Role |
|---|---|
| `activities`, `fragments`, `adapters` | Screens and list rendering |
| `viewmodel`, `presentation` | UI state |
| `domain` | Use cases (RSVP, event save, polls, moderation) |
| `repository`, `data`, `database` | Room entities, DAOs, migrations and repositories |
| `search` | Natural-language search and Gemini integration |
| `worker` | WorkManager jobs (reminders, account cleanup) |
| `core`, `utils` | Shared helpers such as date formatting |

`AppContainer` wires dependencies together. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for role-based access
control, security design and limitations.

## Reliability and security

- **Atomic RSVP and check-in:** join, leave with waitlist promotion, QR check-in and moderation removal each run in a
  single database transaction, so seat counts stay consistent.
- **Validated event editing:** create, edit and AI-created events are checked and authorised (trimmed fields,
  positive capacity, no past dates, capacity not below the going count).
- **Clean deletes:** removing a user or an event also removes or fixes everything tied to it (poll totals, waitlist
  promotion, reports, karma) and notifies affected attendees in the same transaction.
- **Per-user reminders:** reminders only fire for the signed-in user who asked for them and are cancelled when they
  leave an event.
- **Safer moderation:** every admin action re-checks that the actor is an active admin; admins cannot be warned,
  suspended or re-roled, and screens show the real outcome.
- **Authentication:** PBKDF2-SHA256 password hashing (off the main thread), escalating login lockout, lockout-aware
  password changes, untrimmed passwords, and a forced password change for the build-seeded admin.
- **Access control:** anyone can sign up as a student or organizer (admins can demote or suspend accounts); admin
  accounts are never self-registered, and demo accounts exist only in debug builds.
- **Gemini hardening:** calls run off the main thread with short timeouts, and untrusted text is delimited in prompts.

## Known limitations

- Single-device, offline-first design: there is no server, sync or push notification service.
- Anyone can register as an organizer; admins manage roles afterwards from user management.
- The QR ticket signing secret is shared across installs, and the Gemini key is embedded in the APK. A production
  version would move both behind a backend.

## Documentation

- [Demo script](docs/DEMO_SCRIPT.md)
- [Architecture, RBAC, security, limitations](docs/ARCHITECTURE.md)
- [Deployment notes](DEPLOYMENT.md)
- [Roadmap](ROADMAP.md)
- [Change history](CHANGES.md)
