# Demo script (about 5 minutes)

Run from a **fresh debug install** (uninstall first so the seed data is recreated; event dates are relative to install day).
Test once with Wi-Fi off and no `GEMINI_API_KEY`: search and Ask AI fall back to the local parser.

| Role | Email | Password |
|---|---|---|
| Student | aradhya@email.com | password |
| Organizer | cs@campus.edu | password |
| Admin | admin@campus.edu | password |

## 1. Student (2 min)
1. Log in as the student. *"Offline-first: everything is stored on-device in Room."*
2. Home feed: point out the skeleton/empty/offline states, the ad banner and flash events.
3. Search: type "coding events this week". *"Natural-language search with a local fallback."*
4. Open **Hackathon Night** and tap **RSVP**. If it were full you would be waitlisted and auto-promoted on a cancel.
5. Open the **QR ticket**. *"Tickets are HMAC-signed, so they can't be forged."*
6. Study Groups: the course-scoped Legends leaderboard.

## 2. Organizer (1.5 min)
1. Log out, log in as the organizer.
2. **Create event** (or describe one to the AI assistant and check the preview and conflict handling).
3. **Scan ticket**: scan the student's QR; attendance is recorded.
4. Open the event, then overflow menu: **Export Attendees (CSV)**, **Email Attendees**, **Full Edit**.

## 3. Admin (1.5 min)
1. Log in as admin. Users: paged list, search, role filter.
2. Moderation: warn / suspend / remove / dismiss a report.
3. Analytics, then Ads: create an ad and show it on the student feed.

## If something fails
Switch to the backup screen recording. Do not debug live.
