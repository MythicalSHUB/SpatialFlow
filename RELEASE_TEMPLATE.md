![SpatialFlow Update](https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExMmdjaDhpeGd4aHhpc280cHFuajBxa3hoenZiOG5sczUybDB2bDh0MCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/TyCVCdmXJHnK3yHjqy/giphy.gif)

## What's New in SpatialFlow v1.8.1

### New Features & Enhancements

- **OPUS Audio & Downloads:** Full OPUS streaming and download support with complete metadata tagging.
- **Material 3 Expressive UI:** Applied Material 3 Expressive shapes, dynamic colors, spring physics, and fresh visual polish.
- **Dynamic Theme by Default:** Real-time album art color extraction enabled by default for all users.
- **Apple Music-Style Karaoke Engine:** Pure syllable sync with smoothstep word fill, font-weight stepping, and word elevation.
- **120 FPS Focal Auto-Scroll & Bounce:** Accordion Spring Bounce wave with 120 FPS focal auto-scroll engine.
- **Spring Lyrics Bottom Sheet:** Converted to a button-controlled spring Bottom Sheet sharing the dynamic player background.
- **Lyrics Controls & Sync Adjustment:** Song controls directly on Lyrics view + custom Lyrics timing sync controller.
- **Search Bar Enhancements:** Integrated Moods and Genre Charts directly into the search bar.
- **M3 Shape Shimmers:** Replaced legacy Skeleton loaders with smooth Material 3 Shape shimmer animations.
- **Blur & Glassmorphism Effects:** Blur transitions on navigation and Mini Player background dragging.
- **Rebuilt Playback Queue UI:** Drag-reorder, multi-select, and queue synchronization logic.
- **Music Management Tools:** Full Library Rescan and Database Rebuild options added to Settings.
- **System Share Integration:** Open YouTube music links directly in SpatialFlow via system "Share" menu.
- **Crash Detection & Logging:** App Crash Detection with automatic recovery logging.
- **Rich Release Notes:** GIF and markdown support in the In-App Update Bottom Sheet.

### Bug Fixes & Stability

- **Audio Engine:** Fixed Reverb-EQ conflict causing audio routing issues.
- **Search Performance:** Fixed Search bar performance lag and improved layout structure.
- **Settings Persistence:** Fixed "Pause Listening History" toggle state not persisting.
- **Database Rebuild Safety:** Rebuilding database now preserves listening history and Personal Listening Recap statistics while clearing disk caches.
- **Mini Player:** Fixed Mini Player dismiss gesture logic.
- **Library Visibility:** Fixed Library visibility when logged out — now correctly displays Playlists, Recap, and Device Files only.
- **Markdown Rendering:** Fixed What's New dialog to properly render Markdown formatting.

### Improvements & Refactoring

- **Cache Engine:** Converted `CacheManager` to Kotlin using Coroutines and Data Layer architecture.
- **Optimized Cold Starts:** Cache optimizations for faster launch times and reduced memory usage.
- **Codebase Cleanup:** Removed unused Java classes and legacy Home Screen widgets for a cleaner, leaner app footprint.

---

### Important Notes

> Dynamic Theme requires Android 12+ for full Material You color extraction.
 
> This update includes a database migration — please allow the initial rescan to complete.

> Clear app cache if you experience any UI artifacts after upgrading.
