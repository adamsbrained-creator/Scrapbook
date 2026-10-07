# Custom fonts

Scrapbook uses Newsreader (serif) and Figtree (sans) when they are present, and falls back to the system
serif/sans otherwise, so the app builds either way.

1. Download Newsreader and Figtree from Google Fonts.
2. Copy one regular .ttf of each into `app/src/main/res/font/` and name them exactly
   `newsreader.ttf` and `figtree.ttf` (lowercase, no dashes).
3. Rebuild. Headings and body text pick them up automatically.
