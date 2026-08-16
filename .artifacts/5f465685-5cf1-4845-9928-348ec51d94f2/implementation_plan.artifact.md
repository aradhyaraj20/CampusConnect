# Event Visual Enhancement & Content Expansion Plan

This plan aims to replace the placeholder "green grids" with high-quality, aesthetic campus event images and expand the variety of events available to users.

## Proposed Changes

### [Phase 1] UI Improvements
- [MODIFY] `item_event.xml`:
    - Ensure `eventImage` uses `android:scaleType="centerCrop"`.
    - Add a subtle gradient overlay or rounded mask if needed for better text legibility over images.

### [Phase 2] Adapter Logic
- [MODIFY] `EventAdapter.kt`:
    - Integrate **Coil** to load `event.image_uri` into `eventImage`.
    - Add a placeholder and error drawable (using the new `ic_campus_logo` or similar).

### [Phase 3] Content Expansion
- [MODIFY] `SeedData.kt`:
    - Assign high-quality Unsplash URLs to existing events.
    - Add 4-5 **new events** across different categories:
        - *Basketball Finals* (Sports)
        - *Late Night Jazz* (Social)
        - *UI/UX Workshop* (Academic)
        - *Campus Clean-up* (Volunteer)
        - *Chess Tournament* (Groups)

## Verification Plan

### Manual Verification
- Open the **Home Feed** and verify that all events now show real, colorful images instead of green blocks.
- Scroll through the feed to ensure all 8+ events are visible and loading correctly.
- Verify that the image aspect ratio is consistent across all cards.
