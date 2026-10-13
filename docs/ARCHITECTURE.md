# Architecture

```
UI (Activities / Fragments, ViewBinding)
  -> ViewModel (EventDetail, Search, AI, AdminDashboard, OrganizerDashboard)
    -> UseCase (RSVP, Moderation, Karma, Cancellation, AdminAnalytics, AISummary)
      -> Repository (Auth, Event, Search, AI, Notification, Skill)
        -> Room (AppDatabase v16, DAOs)  |  Gemini API (optional, with local fallback)
```

Pure logic lives in `domain/` (`RsvpPolicy`, `AccessPolicy`, `TicketCrypto`) and `search/` so it can be unit-tested without Android.
The Room schema is exported to `app/schemas/.../16.json`; open it for the ER diagram.

## RBAC

| Capability | Student | Organizer | Admin |
|---|---|---|---|
| Browse, search, RSVP, chat, polls, marketplace | yes | yes | yes |
| Create / edit own events | no | yes | yes |
| Edit / delete any event | no | no | yes |
| Scan QR attendance | no | yes | yes |
| Export / email attendees | no | own events | yes |
| Moderate reports, manage users, ads, analytics | no | no | yes |

Enforced by `AccessPolicy` (not only by hiding buttons).

## Security (built)
PBKDF2-SHA256 password hashing, login lockout (5 min), invite-code organizer signup, demo accounts only in debug builds,
HMAC-signed QR tickets, role re-check before privileged operations, cascade delete of user data.

## Limitations (be upfront in the viva)
- No backend: single-device data; two phones do not share events.
- The QR HMAC secret is shared by every install (compiled in).
- The Gemini key is embedded in the APK via `local.properties`.

## Future work
Firebase/Supabase backend, server-issued tickets, push notifications, server-side AI proxy.
