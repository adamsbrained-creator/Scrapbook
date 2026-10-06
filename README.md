# Scrapbook (v0.1)

A personal notes and archive app for Android: notes, quotes, links and photos with eight page layouts,
collections, search, a Rediscover swipe, per-page and app-wide styling, backup/restore, a home-screen
widget, and sharing into the app from other apps.

## Get it on your phone

**Easiest, no tools to install (GitHub):**
1. Create a GitHub repository and push this folder to it (branch `main`).
2. Open the repo's **Actions** tab, open the latest **Android CI** run, and download the
   `scrapbook-debug-apk` artifact (a zip containing `app-debug.apk`).
3. Copy the APK to your phone and open it. Android will ask you to allow installing from that source.

**With Android Studio:** open this folder, let Gradle sync, plug in a phone (USB debugging on) or start an
emulator, and press Run.

The debug APK is fine for personal use. To publish or keep a stable signed build, see `SIGNING.md`.
Fonts: see `FONTS.md`. Widget: long-press your home screen, Widgets, Scrapbook.
