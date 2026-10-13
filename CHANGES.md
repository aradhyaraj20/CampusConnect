# CampusConnect — Changes in this update

Everything below was implemented directly in the Android project. This app has
no backend server (Room/SQLite only), so "scalability" and "cleanup" are
handled on-device via indices, pagination, and a WorkManager job.

## ⚠️ Before you rely on this build
I could not run `./gradlew assembleDebug` in this environment — my sandbox's
network allowlist doesn't include Google's/Gradle's Maven repos, so I couldn't
fetch dependencies to compile-check. I did do a full static pass (brace/paren
balancing, XML well-formedness, cross-checking every `binding.xxx` reference
against its layout's `id`, confirming every referenced string/dao/model method
actually exists), but **please build it in Android Studio before trusting it
in production** — treat the first build as a normal "fix a stray import"
pass, not a guarantee of zero errors.

## 1. Branding — new logo
- Regenerated all launcher icons (mdpi→xxxhdpi, square + round) from your
  uploaded logo image.
- Rebuilt the adaptive icon (`drawable/ic_launcher_background.xml` /
  `ic_launcher_foreground.xml`) as a vector matching the logo's purple
  background (`#5146B6`) and gold/white compass-arrow mark.
- Updated the in-app toolbar logo (`drawable/ic_campus_logo.xml`) to match.

## 2. Icons — "Ask AI" and other panels
- New `ic_ai_sparkle.xml` (chat bubble + spark) replaces the magnifying-glass
  icon that was on the "Ask AI" button.
- All three bottom navigation menus (student, organizer, admin) now use
  purpose-built icons (home, groups, calendar, profile, dashboard, shield for
  moderation, bar-chart for analytics, mail, campaign/megaphone for ads, etc.)
  instead of generic `@android:drawable/ic_menu_*` icons.

## 3. Registration: course + duration
- `User` entity gained `course`, `course_duration_months`, `course_end_at`,
  `last_login_at` (migration 14→15, with indices on `email`, `role`,
  `course`, `account_status`).
- Register screen asks students for their course name and duration in
  months (hidden for the Organizer signup path). `course_end_at` is computed
  at registration time.

## 4. Course-scoped competition (leaderboard)
- The "Legends" leaderboard on the Study Groups screen now ranks students
  against classmates on the **same course** (`UserDao.getTopUsersByCourse`)
  instead of the whole app.

## 5. Auto-delete inactive accounts after course ends
- `AccountCleanupWorker` (WorkManager, runs daily) deletes STUDENT accounts
  where `course_end_at` has passed **and** the account hasn't logged in for
  60+ days since. Scheduled from `CampusConnectApp.onCreate`.
- Admin can also trigger this manually from Admin → Users → ⋮ → "Run
  inactive-account cleanup now".

## 6. Admin scalability (thousands of users)
- `AdminUsersActivity` was rewritten to page through users with SQL
  `LIMIT`/`OFFSET` (25 at a time, "Load more" button) with a search box and
  role filter chips, instead of loading every row into memory.
- Backed by new indices on `users(email/role/course/account_status)`.

## 7. Admin-controlled ads
- New `Ad` entity/DAO/migration (15→16).
- `AdminAdsActivity`: create an ad (title, message, optional image/link),
  toggle active/inactive, delete. Reachable from Admin → Users → campaign
  icon, or from Admin's own Profile screen.
- Active ad shows as a dismissible banner on the student Home feed
  (`HomeFragment` + `fragment_home.xml`).

## 8. Organizer freedom: event photos, edit, delete, email
- `CreateEventActivity` now supports picking a photo for the event and has a
  full edit mode (pass `EXTRA_EVENT_ID`) — pre-fills every field, including
  the existing photo.
- `EventDetailActivity` gained three buttons (creator or admin only):
  **Full Edit** (opens the form above), **Delete Event**, **Email
  Attendees** (opens the device's email app pre-addressed via BCC to
  everyone who RSVP'd "going"). The original AI quick-description-edit
  button is still there alongside these.
- Admins now also get edit/delete rights on any event, not just the
  creator (`EventDetailViewModel.isEditVisible`).

## 9. "My Profile" tab for Organizer & Admin
- New `MyProfileActivity` — avatar, bio, interests chips, and role-relevant
  stats (Organizer: events created / registrations; Admin: total users /
  total events), plus Settings and Log Out.
- Both dashboards' bottom-nav "Profile" tab now opens this instead of
  jumping straight into a bare edit form.

## Files touched
Models: `User.kt`, `Ad.kt` (new)
Database: `AppDatabase.kt`, `UserDao.kt`, `AdDao.kt` (new), `EventDao.kt`
  (unchanged, already had what was needed)
Repository: `AuthRepository.kt`
Activities: `RegisterActivity`, `CreateEventActivity`, `EventDetailActivity`,
  `MyProfileActivity` (new), `AdminUsersActivity`, `AdminAdsActivity` (new),
  `AdminDashboardActivity`, `OrganizerDashboardActivity`
Fragments: `HomeFragment`, `StudyGroupsFragment`
Worker: `AccountCleanupWorker.kt` (new)
Adapters: `AdminUserAdapter`, `AdAdapter` (new)
Resources: new layouts (`activity_admin_users`, `activity_admin_ads`,
  `activity_my_profile`, `item_ad_row`, `dialog_create_ad`), new drawables
  (nav icons, AI icon, launcher icons/logo), new strings, new menu
  (`admin_users_menu`), `AndroidManifest.xml` updated with new activities.
