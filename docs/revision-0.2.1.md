# Requested revision 0.2.1

- Branding: Камеры дома №9; icon based on existing illustrated building.
- Login: centered title, form at bottom, remove slogans/server/theme controls. System theme by default. Saved credentials trigger automatic login through illustrated loading screen, with login fallback on failure.
- Camera wall: grid/feed toggle at top left; real bottom tabs. Video-only tiles, bottom-left translucent name, small top-right connection lamp. Animated drag placement moves neighbors without recreating players.
- Fullscreen: video fills screen; controls and name overlay it, respect system/cutout insets.
- Archive viewer: empty initial selection, single-column feed, minimal overlays and no decorative headings.
- Reliability: investigate stale callbacks, player ownership, subscriptions and reconnect when returning from background/tabs. Actual phone crash root cause remains unconfirmed without a crash log.
