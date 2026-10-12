# Scrapbook (v0.1)

A personal notes and archive app for Android: notes, quotes, links and photos with eight page layouts,
collections, search, a Rediscover swipe, per-page styling, a Settings page (theme, white or soft grey background,
accent, lists, boxes, backup/restore; seven box styles: Tabbed, Paper stack, Tinted tiles, Folder tabs, Chips, List rows, Notebook covers), a home-screen widget, and sharing into the app from other apps.

## Get it on your phone

**Easiest, no tools to install (GitHub):**
1. Create a GitHub repository and push this folder to it (branch `main`).
2. Do the one-time key setup in `SIGNING.md` (about 10 minutes). This is what lets every new version
   install over the old one without deleting the app.
3. Open the repo's **Actions** tab, open the latest **Android CI** run, and download the
   `scrapbook-release-apk` artifact (a zip containing `app-release.apk`).
4. Copy the APK to your phone and open it. Android will ask you to allow installing from that source.

**With Android Studio:** open this folder, let Gradle sync, plug in a phone (USB debugging on) or start an
emulator, and press Run.

`scrapbook-debug-apk` is also built and signed with the same key, but use the release one day to day.
Fonts: see `FONTS.md`. Widget: long-press your home screen, Widgets, Scrapbook.
