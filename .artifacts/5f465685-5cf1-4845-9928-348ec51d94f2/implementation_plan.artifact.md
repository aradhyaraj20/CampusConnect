# CampusConnect "Ultimate community" Features Plan

This plan introduces high-impact, unique features that turn the app into a self-sustaining campus ecosystem.

## 1. Event "Memories" Wall
Instead of events just disappearing when they end, they leave behind a visual legacy.
- **How it works**: For events that have passed, the "RSVP" button is replaced by a "View Memories" button.
- **UI**: A masonry grid of photos uploaded by attendees.
- **Uniqueness**: Turns a one-time utility into a social archive of campus life.

## 2. Campus "Legend" Leaderboard
A high-visibility ranking system for the most helpful community members.
- **How it works**: A "Top Contributors" banner at the top of the **Study Groups** screen.
- **Data**: Sorts users by `karma_points`.
- **Uniqueness**: Gamifies helpfulness and gives students "social proof" to add to their real-world resumes.

## 3. Lost & Found "Karma Bounty"
A dedicated category for lost items with an integrated reward system.
- **How it works**: When posting a lost item, you can offer a "Karma Reward".
- **Logic**: When someone returns the item, the "Bounty" is transferred to their account.
- **Uniqueness**: Standard apps just have a list; this creates an incentive for community service.

---

## User Review Required

> [!IMPORTANT]
> **Database Version**: These changes will increment the Room database to **version 5**. I will use destructive migration to ensure all seed data is updated with these new categories and photos.

> [!TIP]
> Should the "Legend Leaderboard" be global (all students) or subject-specific (e.g., "Math Legend")? I propose a **Global Top 5** for maximum prestige.

## Proposed Changes

### [Phase 1] Data & Models
- [MODIFY] `EventDao.kt`: Add query to get top users by karma.
- [NEW] `RecapPhotoAdapter.kt`: To display the memory grid.

### [Phase 2] Event Memories UI
- [NEW] `activity_event_recap.xml`: A stylish grid layout for event photos.
- [NEW] `EventRecapActivity.kt`: Logic to display and add new recap photos.

### [Phase 3] Hall of Fame UI
- [MODIFY] `fragment_study_groups.xml`: Add a horizontal "Campus Legends" RecyclerView at the top.
- [NEW] `LegendAdapter.kt`: To show circular avatars of top karma earners with their rank.

### [Phase 4] Marketplace "Bounty" Update
- [MODIFY] `activity_marketplace.xml`: Add a "Bounty" tag to items in the Lost & Found category.

## Verification Plan

### Manual Verification
- Check the **Study Groups** fragment and verify the "Campus Legends" banner shows users ranked by karma.
- Open a past event (like the Hackathon Night in seed data) and verify the "View Memories" button works.
- Verify you can "Add a Photo" to an event recap.
- Post a "Lost Item" and check if the price field correctly displays as a "Karma Bounty".
