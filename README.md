# infimg

A small, cross-platform (Linux/macOS/Windows) Java/Swing image viewer that
does the one thing no OS default viewer seems to get right: fit-to-window
plus genuinely arbitrary-angle rotation, in one native, embeddable,
scriptable tool.

## Why

- **Linux**: no strong default at all. Nemo's built-in viewer only rotates
  in right angles; feh needs `-Z`/`--auto-zoom` to fit-to-window and still
  feels rough; nomacs/geeqie are close but neither is pre-installed
  anywhere.
- **macOS**: Preview.app is solid (fit-to-window, zoom/pan, fast) but
  right-angle rotation only.
- **Windows**: the stock Photos app is slow and cloud-oriented; the old
  Windows Photo Viewer (right-angle only) got buried. IrfanView has
  free-angle rotation but is Windows-only and a closed black box — no
  programmatic hooks, nothing to embed.

Nothing free-angle-rotates *and* is cross-platform *and* is
embeddable/scriptable — except GIMP, which is enormous overkill for "look
at an image and maybe straighten it."

## Features

- Fit-to-window on load — rescales up or down to exactly fill the current
  window, no letterbox margin.
- Mouse wheel zooms; a toolbar toggle switches the wheel to rotate through
  an arbitrary angle instead (not just 90° steps).
- Click-drag pan.
- **Fit** button re-fits after a manual window resize or a wandered-off
  zoom/pan.
- **Save** writes exactly the current on-screen pixels — zoom, rotation,
  pan, and crop all baked in — not a full-resolution re-render.
- Remembers the window's last position/size across runs, in a tiny
  `~/.infimg.json`.
- **Exit** button closes with no "unsaved changes?" nag — nothing is ever
  really lost, just a Save you'd have to redo.

## Build & run

```bash
mvn package
java -jar target/infimg-1.0-jar-with-dependencies.jar [optional-image-file]
```

Sample start scripts (assume `java` is on `PATH`, resolve the jar relative
to their own location, so they work from anywhere):

- `scripts/infimg.sh` — Linux
- `scripts/infimg.command` — macOS (double-click in Finder)
- `scripts/infimg.bat` — Windows

## CI / Releases

Every push builds the jar (`.github/workflows/build.yml`) and uploads it
as a build artifact. Pushing a tag matching `v*.*.*` (e.g. `v1.0.0`)
additionally builds and publishes a GitHub Release with the fat jar
attached (`.github/workflows/release.yml`).

## Dependencies

Just `jackson-databind`, used only to read/write the small window-bounds
config file. Everything else is JDK Swing/AWT/`ImageIO`.

## Status

Extracted 2026-08-10 from a Voynich manuscript research project, where it
started as a "show me something" tool for pulling traced page regions up
on screen. General-purpose, no dependency on that project.
