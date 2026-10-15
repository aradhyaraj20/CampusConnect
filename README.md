# CampusConnect 2.0

Kotlin + XML + Material 3 + Room + MVVM campus app.

## Demo accounts (debug builds only)

Release builds never seed these accounts.

| Role | Email | Password |
|---|---|---|
| Student | aradhya@email.com | password |
| Organizer | cs@campus.edu | password |
| Admin | admin@campus.edu | password |

## Release configuration (`local.properties`)

```
TICKET_HMAC_SECRET=<long random string>      # required for release
RELEASE_STORE_FILE=C\:\path\to\key.jks   # required for release
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
ADMIN_INITIAL_PASSWORD=<8+ chars>            # seeds admin@campus.edu in release
ORGANIZER_INVITE_CODE=<code>                 # blank = organizer self-signup disabled
```

## Gemini (optional)

In `local.properties` (do not commit a real key):

```
GEMINI_API_KEY=your_key_here
GEMINI_MODEL=gemini-2.5-flash
```

If the key is empty or Gemini is down, the app uses the local parser / database Q&A and does not crash.

## Features

- **Student:** NLP event search with explanations, RSVP with waitlist promotion, QR tickets, chat, polls, marketplace,
  skill exchange + karma, course-scoped leaderboard, badges, event reminders, add to system calendar.
- **Organizer:** create events (manually or by describing them to the AI), event photos, edit/delete, QR attendance
  scanner, email attendees, **export attendees as CSV**.
- **Admin:** paged user management, moderation (warn / suspend / remove / dismiss), analytics, ads, inactive-account cleanup.
- Offline banner, loading/empty states, dark mode, local fallback when Gemini is unavailable.

## Reliability and security

- **Atomic RSVP / check-in:** join/leave with waitlist promotion, QR check-in and moderation removal run inside a
  single database transaction (`TransactionRunner`), so seat counts stay consistent.
- **Validated event editing:** `SaveEventUseCase` checks and authorises create/edit (trimmed fields, positive capacity,
  no past dates, capacity not below the going count) and notifies going/waitlisted attendees when date or location changes.
- **Safer moderation:** admins cannot be warned, suspended or re-roled; screens show the real outcome.
- **Auth:** lockout-aware password change via `AuthRepository.changePassword`.
- **Gemini hardening:** calls run off the main thread with short timeouts and delimited untrusted prompt text.

## Build and test

```
./gradlew assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest   # Room migration test (needs emulator/device)
```

## Docs

- [Demo script](docs/DEMO_SCRIPT.md)
- [Architecture, RBAC, security, limitations](docs/ARCHITECTURE.md)
- [Roadmap](ROADMAP.md)
- [Change history](CHANGES.md)
