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

- Fit-to-window on load, mouse-wheel zoom or (toolbar toggle) arbitrary-angle
  rotate, click-drag pan.
- **Save**/**Copy** write exactly the current on-screen pixels — zoom,
  rotation, pan, crop all baked in.
- **Load**/**Paste** from a file or the system clipboard.
- 10 remembered window-position slots, selectable at launch (`-0`..`-9`) —
  lets any caller, in any language, embed infimg with "open at the user's
  preferred position/size" for free. See [MANUAL.md](MANUAL.md).
- A **Menu** button tucks away growing extras (window slots, optional
  external-tool integrations like metadata viewing, look-and-feel) without
  cluttering the main toolbar. See [MANUAL.md](MANUAL.md) for the full
  feature reference.
- Pick a look-and-feel — system default or [FlatLaf](https://www.formdev.com/flatlaf/)
  Light/Dark/IntelliJ/Darcula — from Menu; applies instantly and persists.
- **Menu → Lighter / Darker / More Contrast / Less Contrast**: one click,
  one fixed step, no sliders or numbers — nudge until it looks right,
  click again if it doesn't. Under the hood it's a perceptually-uniform
  CIELAB L* shift/S-curve, not the naive per-channel sRGB scaling almost
  every other "brighten this photo" tool (including
  `java.awt.Color.brighter()`) actually does. Full-image recompute,
  parallelized across every core, on every click.

## Build & run

```bash
mvn package
java -jar target/infimg-1.4-jar-with-dependencies.jar [-0..-9] [optional-image-file]
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

Just `jackson-databind`, used only to read/write the small config file.
Everything else is JDK Swing/AWT/`ImageIO`. Some **Menu** features use
optional external command-line tools if you tell infimg they're installed —
see [MANUAL.md](MANUAL.md).

## Status

Extracted 2026-08-10 from a Voynich manuscript research project, where it
started as a "show me something" tool for pulling traced page regions up
on screen. General-purpose, no dependency on that project.

Full feature-by-feature reference, including the config file format:
[MANUAL.md](MANUAL.md).

### Changelog

- **v1.2.0** — Added a **Menu** button (keeps the main toolbar simple
  while leaving room to grow) with **Load Slot**/**Save as Slot**
  submenus over 10 remembered window-position slots, selectable at launch
  with `-0` through `-9`. Promoting the current geometry into a new slot
  while running as the default slot 0 reverts slot 0 to what it held
  before this session started, undoing any autosave drift picked up along
  the way. Added a **Metadata** menu item that shells out to ImageMagick's
  `identify -verbose`, gated behind an `imageMagick` config flag the user
  sets themselves after installing it — see MANUAL.md. Added a
  **Look & Feel** submenu (System Default plus FlatLaf's four bundled
  themes — Light/Dark/IntelliJ/Darcula) via a new `flatlaf` dependency;
  switches instantly and the choice persists to `~/.infimg.json`. Added
  **Lighter**/**Darker**/**More Contrast**/**Less Contrast** — one-click,
  no-dialog CIELAB L* nudges (a fixed offset per click for
  brightness, a sigmoid S-curve step for contrast), each recomputed
  across all CPU cores. (An earlier slider-dialog version of this was
  replaced — a numeric L* offset dialog was more control than most users
  actually want; click-and-see repeatable nudges are simpler and just as
  effective.) The title bar now gets a trailing `*` once a pixel
  adjustment has been made (informative only, not enforced) — zoom,
  rotate, and pan don't count, only Lighter/Darker/More Contrast/Less
  Contrast do.
- **v1.1.0** — Clipboard **Paste**/**Copy** buttons alongside Load/Save.
  Fixed the rotate/zoom pivot drifting off to wherever the image had been
  panned to (it's now pinned to the true viewport center regardless of
  pan). Copy renders as opaque RGB, not ARGB, avoiding a JDK Linux/X11 bug
  where the clipboard manager's PNG round-trip (which keeps clipboard data
  available after this process exits) can't be read back via
  `imageFlavor` for images carrying an (always-opaque-anyway) alpha
  channel.
- **v1.0.0** — Initial release.
</content>
