# Architecture

```
UI (Activities / Fragments, ViewBinding)
  -> ViewModel (EventDetail, Search, AI, AdminDashboard, OrganizerDashboard)
    -> UseCase (RSVP, SaveEvent, Poll, ModerateReport)
      -> Repository (Auth, Event, Search, AI)
        -> Room (AppDatabase v19, DAOs)  |  Gemini API (optional, with local fallback)
```

Pure logic lives in `domain/` (`RsvpPolicy`, `AccessPolicy`, `TicketCrypto`) and `search/` so it can be unit-tested without Android.
The Room schema is exported to `app/schemas/.../19.json`; open it for the ER diagram. Unique indices enforce one RSVP per user per event, one vote per user per poll and one check-in per attendee.

## RBAC

| Capability | Student | Organizer | Admin |
|---|---|---|---|
| Browse, search, RSVP, chat, polls | yes | yes | yes |
| Add event memory photos (with a seat, after start) | yes | yes | yes |
| Create / edit own events | no | yes | yes |
| Open polls on an event | no | own events | yes |
| Edit / delete any event | no | no | yes |
| Scan QR attendance | no | yes | yes |
| Export / email attendees | no | own events | yes |
| Moderate reports, manage users, ads, analytics | no | no | yes |

Enforced by `AccessPolicy` (not only by hiding buttons). Admin and organizer screens call `RoleGuard`, which re-reads the role and account status from the database, so a demoted or suspended account is sent back out even if the cached session says otherwise. Event chat and polls check enrollment themselves.

## Security (built)
PBKDF2-SHA256 password hashing, escalating login lockout (5 min doubling up to 24 h), forced password change for the build-seeded admin, no self-registered admins, demo accounts only in debug builds,
HMAC-signed QR tickets checked against the database at the door (scanner must own the event or be admin, event active, RSVP still "going", ticket matches; no replay), tickets only for the holder's own confirmed seat, role re-check before privileged operations, cascade delete of user and event data, https-only ad links, shared login/password-change lockout.

## Limitations (be upfront in the viva)
- No backend: single-device data; two phones do not share events.
- The QR HMAC secret is shared by every install (compiled in).
- The Gemini key is embedded in the APK via `local.properties`.
- The runtime Gemini key an admin enters in-app is stored in plain private SharedPreferences (not Keystore-backed).
- Debug builds fall back to a public default QR secret (release builds refuse it), so debug-build tickets are forgeable.
- No foreign keys: cascades are written by hand in `eraseUser` / `deleteEventCascade`; a new table must be added to both.
- Several timestamp columns (`created_at`, `joined_at`) are text, not epoch millis; formatting is centralised in `DateFormats`.
- A downgrade install wipes the local database (`fallbackToDestructiveMigrationOnDowngrade`).
- AI prompts delimit untrusted text, but prompt injection through event text can't be fully prevented without a server.

## Consistency rules
- Multi-step writes (join/leave RSVP with waitlist promotion, QR check-in, moderation removal) run inside `TransactionRunner`, via `AppContainer.transactions`.
- Admin actions return a `ModerationOutcome` so the UI reports the real result; admin accounts cannot be suspended, warned or re-roled from the moderation screens.
- Gemini calls run on `Dispatchers.IO` with a 30 s total budget, then the local parser takes over.

## Future work
Firebase/Supabase backend, server-issued tickets, push notifications, server-side AI proxy.
