# Custom fonts

The new design uses **Outfit**. Without the font file the app falls back to the system sans-serif, so it builds either way.

1. Download Outfit from Google Fonts (a regular .ttf is enough; the variable font file also works).
2. Put it in `app/src/main/res/font/` and name it exactly `outfit.ttf` (lowercase).
3. Rebuild. Headings and note text pick it up automatically.

Optional extras for the older serif look: `newsreader.ttf` (serif) and `figtree.ttf` (sans fallback), same folder.
