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
  rotate, click-drag pan. EXIF-orientation-aware, so a phone photo opens
  upright rather than however the sensor recorded it.
- **Save**/**Copy** write exactly the current on-screen pixels — zoom,
  rotation, pan, crop all baked in.
- **Load**/**Paste** from a file or the system clipboard; slow sources (a
  NAS share) show a "Loading" placeholder instead of freezing the window.
- **Prev**/**Next** step through every file given on the command line, in
  the order listed there.
- **Menu → Rotate 90°/180°/270°** and **Flip Horizontal/Vertical** for
  quick exact square-ups, alongside the free-angle wheel rotate.
- Full command-line control for scripting: `--rotate DEG`, `--flip-hor`/
  `--flip-ver`, `--lighter`/`--darker`/`--more-contrast`/`--less-contrast`,
  and `--config PATH` to point at an alternate config file. See
  [MANUAL.md](MANUAL.md).
- 10 remembered window-position slots, selectable at launch (`-0`..`-9`) —
  lets any caller, in any language, embed infimg with "open at the user's
  preferred position/size" for free. See [MANUAL.md](MANUAL.md).
- A **Menu** button tucks away growing extras (window slots, optional
  external-tool integrations like metadata viewing, look-and-feel) without
  cluttering the main toolbar. See [MANUAL.md](MANUAL.md) for the full
  feature reference.
- **Menu → Pixel Microscope**: a separate window that zooms into
  individual pixels around the cursor, with per-pixel sRGB/YUV/CIELab/HSB
  values and a colour-frequency readout for the visible area. See
  [MANUAL.md](MANUAL.md).
- Pick a look-and-feel — system default or [FlatLaf](https://www.formdev.com/flatlaf/)
  Light/Dark/IntelliJ/Darcula — from Menu; applies instantly and persists.
- **Menu → Lighter / Darker / More Contrast / Less Contrast**: one click,
  one fixed step, no sliders or numbers — nudge until it looks right,
  click again if it doesn't. Under the hood it's a perceptually-uniform
  CIELAB L* shift/S-curve, not the naive per-channel sRGB scaling almost
  every other "brighten this photo" tool (including
  `java.awt.Color.brighter()`) actually does. Full-image recompute,
  parallelized across every core, on every click.

## Install & run

See [INSTALL.md](INSTALL.md) — install [MITSA](https://github.com/Walter-Stroebel/mitsa)
once, then `mitsa add infimg Walter-Stroebel infimg` gets you a working
`infimg` command on `PATH` that stays updated.

## Build (development)

```bash
mvn package
java -jar target/infimg-1.6-jar-with-dependencies.jar [-0..-9] [optional-image-file]
```

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

- **v1.6** — Installable via [MITSA](https://github.com/Walter-Stroebel/mitsa)
  (see [INSTALL.md](INSTALL.md)); per-OS `scripts/infimg.*` and the
  hand-written `~/bin/infimg` are retired in favor of MITSA's managed
  shim. Also fixed a CLI bug where `--help` (or any unrecognized `--`
  flag) fell through to file-open logic and popped a GUI error dialog
  instead of printing usage — `--help`/`-h` now prints usage and exits
  cleanly, and unknown `--` flags print an error instead of trying to
  open as a file.
- **v1.5** — Added **Menu → Pixel Microscope...**, a separate window for
  zooming into individual pixels of the current image (drag-to-pan grid
  view, per-pixel sRGB/YUV/CIELab/HSB, colour-frequency readout). Works
  for both file-loaded and clipboard-pasted images. Window bounds persist
  to the existing `infimg.json` (MITSA app-data dir), no separate config file. See
  [MANUAL.md](MANUAL.md).
- **v1.4** — Added long-form CLI flags mirroring existing Menu actions —
  `--rotate DEG`, `--flip-hor`/`--flip-ver`,
  `--lighter`/`--darker`/`--more-contrast`/`--less-contrast`, `--slot` —
  applied once to the first file shown, not as a batch mode. Added
  `--config PATH` to point at an alternate config file instead of the
  shared `infimg.json` (MITSA app-data dir), mainly so more than one instance can run
  side by side without fighting over the same window-position slots.
  Multiple positional file arguments now populate a **Prev**/**Next**
  list, navigating in the order given on the command line with no
  directory scanning.
- **v1.3** — Fixed EXIF orientation being double-applied (baked into
  pixels and fed into the rotation state), which opened images rotated
  180° off instead of upright; Fit-to-window ignoring rotation entirely
  and clipping rotated images; an `AffineTransform` composition-order bug
  that swapped Flip Horizontal/Vertical; file loading blocking the EDT
  with no feedback on slow sources (NAS/CIFS); and a crash loading
  non-JPEG files (e.g. PNG) caused by EXIF-orientation parsing assuming
  JPEG's metadata format unconditionally. Added **Menu → Rotate
  90°/180°/270°** (absolute, not additive) with a live rotation-degree
  toolbar label; **Menu → Flip Horizontal/Vertical**; a "Loading"
  placeholder for slow reads, backgrounded via `SwingWorker`; Metadata
  now works for clipboard pastes too, with a shared last-modified/size
  header; a one-click **Detect ImageMagick** probe; and Look & Feel now
  defaults to FlatLaf Darcula on fresh Linux installs instead of System
  Default (GTK's `JFileChooser` looked dated).
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
  switches instantly and the choice persists to `infimg.json` (MITSA app-data dir). Added
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
