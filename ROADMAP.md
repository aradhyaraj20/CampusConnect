# CampusConnect Roadmap (college project edition)

Goal: a project that **runs reliably in a live demo, is easy to explain, and scores well** on functionality,
design, code quality and documentation. No Play Store release, no real users, no production ops.

## Where it stands

An offline Android app (Kotlin, XML views, Room v16) with three roles: events + RSVP/waitlist, QR tickets and
check-in, chat, polls, marketplace, study groups, AI search (Gemini with local fallback), admin moderation and ads.
The security hardening is done (invite-code organizer signup, debug-only demo accounts, PBKDF2-SHA256, login
lockout, cascade delete, release guards).

**The "no backend" design is fine for a college project.** Present it deliberately: *"offline-first, single-device
demo; a production version would add a server"*. The known weak spots (shared QR secret, Gemini key in the APK)
become a "limitations and future work" slide, not a failure. You don't need to fix them.

---

## Phase 1 — Make the demo bulletproof (the one that matters most)
- Build and run on an emulator and a real phone; fix any crash on the three role journeys:
  Student (register → browse → RSVP → QR ticket), Organizer (create event → scan ticket → view attendees),
  Admin (users → moderation → analytics → ads)
- Run `connectedDebugAndroidTest` once to confirm the migration test passes
- Keep the debug demo accounts (they still seed in debug builds) and make sure a fresh install seeds correctly
- Test with **no internet and no Gemini key**, since the demo room Wi-Fi may fail; confirm the local fallback works
- Put the folder under git (`git init`), keep `local.properties` ignored, and rotate the Gemini key that is in it
- Write a one-page **demo script**: exact click path, the accounts, and what to say at each step

**Done when:** you can run the full demo twice in a row from a fresh install with no surprises.

## Phase 2 — Polish what evaluators see (1–2 weeks)
- UI consistency: spacing, empty states, loading and error states on every list screen; dark mode check
- Fix visible rough edges: hardcoded text, missing icons/content descriptions, text clipping on small screens
- Add 2–3 "wow" touches that already half-exist: the AI assistant answering from real event data, QR ticket
  with a styled card, analytics charts on the admin dashboard
- Seed data that looks realistic (names, event photos, dates relative to *today* so the feed isn't stale)
- Make sure every feature in the README works or is removed from the README

## Phase 3 — Code quality you can talk about (1–2 weeks)
Pick the cheap items evaluators and viva questions focus on:
- More unit tests on the pure logic you already have (RSVP/waitlist, search scoring, auth rules, `eraseUser`);
  a coverage number and green CI badge look good in a report
- Add GitHub Actions CI (build + unit tests) — a few lines, big credibility
- Tidy the biggest files (`SeedData`, `HomeFragment`, `AIRepository`) and move hardcoded strings to `strings.xml`
- Optional: simple dependency injection via an `AppContainer` class (skip Hilt; keep it explainable)
- Skip: real foreign keys migration, dependency-version bumps, performance tuning (risk > reward)

## Phase 4 — Documentation and report (1 week, start early)
- Architecture diagram (layers: UI → ViewModel → UseCase → Repository → Room) and an ER diagram from
  `app/schemas/.../16.json`
- Feature list per role, screenshots, and the RBAC table (who can do what)
- A **Security** section: what you built (hashing, lockout, invite code, signed QR tickets, role re-check)
  and an honest **Limitations** section (no server, shared secret, API key in the APK)
- **Future work** slide: backend (Firebase/Supabase), server-issued tickets, push notifications, AI proxy
- Merge README / CHANGES / DEPLOYMENT into one README + a short `docs/` folder; remove stale "Phase A–F" text
- A 2–3 minute screen recording of the demo as a backup if the live demo fails

## Phase 5 — Optional stretch (only if time remains)
Pick **one**, not all:
1. **Firebase backend lite:** Firestore for events + RSVPs only, so two phones see the same data — the most
   impressive upgrade, because it demonstrates real multi-user behavior
2. **Calendar export:** ICS file from an event's detail screen (small, useful, low-risk)
3. **Attendee CSV export** for organizers
4. **Event recommendations** improvements using the existing interests and RSVP history

---

## Cut list (deliberately not doing)
Play Console, signing and tracks; privacy policy; crash/analytics services; baseline profiles; min-version kill
switch; server-side AI proxy; multi-campus scale; i18n beyond English.

## Suggested timeline (adjust to your deadline)
| Week | Focus |
|---|---|
| 1 | Phase 1 (stability), start report |
| 2 | Phase 2 (polish) |
| 3 | Phase 3 (tests, CI, cleanup) + Phase 4 (docs) |
| 4 | Stretch item if any, rehearse demo, record backup video |

If your deadline is shorter: do Phase 1, then the report (Phase 4), then whatever polish fits.

## Risks and how to handle them
- Live demo fails → fresh-install rehearsal plus a backup video
- Gemini or network down → local fallback is already built; test it explicitly
- Viva question "why no server?" → answer: scoped to offline-first for the course; production path is in Future work
- Last-minute refactors breaking things → freeze features one week before the demo; only bug fixes after
