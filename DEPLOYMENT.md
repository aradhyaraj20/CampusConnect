# CampusConnect — Review 5 deployment

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Student | aradhya@email.com | password |
| Organizer | cs@campus.edu | password |
| Admin | admin@campus.edu | password |

## Local setup

1. Open the project in Android Studio.
2. Confirm `local.properties` has `sdk.dir`.
3. Optional Gemini:

```
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
```

4. Sync Gradle. Build **Debug**.
5. Install on an emulator/device (API 24+).

## Release build

Debug signing is used unless you add a keystore:

```
RELEASE_STORE_FILE=C\:\\path\\to\\campusconnect.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=campusconnect
RELEASE_KEY_PASSWORD=...
```

Then:

```
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Never put `GEMINI_API_KEY` or keystore passwords in Kotlin, XML, or git.

## Section 40 checklist

- [ ] Debug build works
- [ ] Release assemble works
- [ ] No crashes on the three role journeys
- [ ] No hardcoded user IDs
- [ ] No hardcoded API keys
- [ ] Database migrations 11→14 applied (non-destructive)
- [ ] Login + session persist
- [ ] Student / Organizer / Admin dashboards
- [ ] NLP search + Ask AI
- [ ] AI event creation + preview + conflict
- [ ] RSVP + waitlist promotion
- [ ] QR ticket + scanner (organizer)
- [ ] Notifications, chat, polls
- [ ] Skill Exchange + Karma
- [ ] Admin moderation
- [ ] Dark mode
- [ ] Home skeleton / empty / offline
- [ ] Accessibility labels on Home search, chips, FABs, cards
- [ ] Offline fallback banner
