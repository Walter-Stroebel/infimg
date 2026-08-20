# infimg manual

Feature-by-feature reference. For the pitch and build instructions, see
[README.md](README.md).

## Main toolbar

| Button | Does |
|---|---|
| Load | Open an image file via the OS file chooser. If the file has an EXIF orientation tag (most phone camera JPEGs do), the image is automatically rotated to display upright — matching what Nemo, Photos, and browsers already show you, rather than the raw sensor pixels. The read runs in the background, so a slow source (a NAS share has been the noticeable real case) shows a large "Loading" placeholder instead of freezing the window. |
| ▾ | Pops up the last 10 files opened (most recent first); click one to load it directly, skipping the file chooser. |
| Save | Write exactly the current on-screen pixels (zoom, rotation, pan, crop all baked in) to a new file. |
| Paste | Load whatever image is on the system clipboard. |
| Copy | Copy exactly the current on-screen pixels to the system clipboard. |
| Rotate (wheel) | Toggle — when down, the mouse wheel rotates the image through an arbitrary angle instead of zooming it. A live degree label (e.g. "45°") sits right next to this button, always showing the current rotation, however it got there — wheel or Menu → Rotate. |
| Prev / Next | Step to the previous/next file among those given on the command line, in the order they were listed there. Disabled (greyed out) unless infimg was launched with more than one file. Plain navigation only — any `--rotate`/`--flip-*`/`--lighter`/etc. flags given at launch (see "Command-line arguments" below) apply once, to the first file shown, not to every file you step to. |
| Fit | Rescale and re-center to fill the window as it is now, showing the entire image at whatever angle it's currently rotated to — for after a manual resize, a wandered-off zoom/pan, or a rotation. At an angle like 45° the image's corners swing wider than its own width/height, so Fit zooms out further there than at 0°/90° — that's correct, not a bug, since the whole rotated image genuinely takes up more on-screen room. |
| Menu | Everything below this line. |
| Exit | Close with no "unsaved changes?" nag — Save first if you want the current view kept. |

Click-drag pans; mouse wheel zooms (or rotates, with the toggle down).

**Menu → Rotate 90° / 180° / 270°** are quick exact square-ups — for
photos or scans that just need to be turned a quarter-turn or flipped
upside down, without needing a steady hand on the wheel to land exactly
on the angle. Each one is **absolute**, not a nudge: clicking Rotate 90°
always sets rotation to exactly 90°, no matter what it was before —
clicking it twice in a row stays at 90°, it doesn't compound to 180°. It's
the same rotation state the wheel drives, just settable to an exact value
instead of only nudged continuously.

**Menu → Flip Horizontal / Flip Vertical** mirror the image left-right or
top-bottom — the fix for a photo taken in a mirror, or one a phone's
camera app flipped on its own (both genuinely happen). Each is a toggle:
click again to undo it. Flip always mirrors the image as currently
displayed, including whatever rotation is already applied — flip a
90°-rotated photo and you get *that* image mirrored, not the original
file's raw orientation flipped and then rotated.

## Command-line arguments

```
java -jar infimg.jar [-0..-9 | --slot N] [-c|--config-file PATH]
                      [--rotate DEG] [--flip-hor] [--flip-ver]
                      [--lighter] [--darker] [--more-contrast] [--less-contrast]
                      [file ...]
```

- **Files**: zero, one, or many image paths. With more than one, the first
  is shown on launch and **Prev**/**Next** (see the toolbar table above)
  step through the rest in the order given. `find`, a shell glob, or a
  script assembling a list all work the same way — infimg never browses a
  directory on its own (that's the file manager's job), it only walks the
  list it was actually handed.
- **`-0` through `-9`** (or **`--slot N`**, the same thing spelled out):
  which of the 10 window-position slots to open at — see "Window-position
  slots" below. No flag defaults to slot 0.
- **`-c PATH` / `--config-file PATH`**: use `PATH` instead of `infimg.json` (MITSA app-data dir) for this run
  — window slots, the ImageMagick flag, and the Look & Feel choice are all
  read from and written to `PATH` instead. Mainly for running more than
  one infimg instance side by side without them fighting over the same
  slot set (two instances sharing the default config can otherwise
  overwrite each other's live-tracked window position).
- **`--rotate DEG`**: set rotation to exactly `DEG` degrees (0–359,
  free-angle — same rotation state the mouse wheel and Menu → Rotate
  drive, just settable to an exact value from the command line). Anything
  outside 0–359 is refused with an error and infimg exits without opening
  a window.
- **`--flip-hor`** / **`--flip-ver`**: same as one click of Menu → Flip
  Horizontal / Flip Vertical.
- **`--lighter`** / **`--darker`** / **`--more-contrast`** /
  **`--less-contrast`**: same as one click of the matching Menu button
  (see "Lighter / Darker / More Contrast / Less Contrast" below) — repeat
  the flag for multiple clicks, e.g. `--lighter --lighter` for two steps.

All of the adjustment/rotate/flip flags apply **once**, in the order given
on the command line, to the first file shown — they're a scriptable
stand-in for a few Menu clicks on the image you're about to look at, not a
batch-processing mode. Stepping to another file with Prev/Next shows it
exactly as loaded, with none of these flags reapplied — infimg is a
viewer, not a batch image editor (see the project's `CLAUDE.md` "Feature
scope" section for why that line is deliberate). Save is unaffected and
still always writes exactly the current on-screen pixels.

Example — open a scanned page pre-rotated and slightly lightened, third in
a folder of pages so Next walks the rest:

```bash
java -jar infimg.jar --rotate 90 --lighter page03.png page04.png page05.png
```

## The Menu button

The main toolbar is deliberately small and will stay that way. Every new
feature that isn't one of the core "look at an image" actions goes behind
**Menu** instead, as a popup with submenus — so the tool keeps its simple,
obvious front while still being able to grow arbitrarily much clutter
behind one button.

### Window-position slots

infimg remembers **10** window positions/sizes, not just one, in
`infimg.json` (MITSA app-data dir). Each is an independent slot, numbered 0–9.

- **Launch**: `-0` through `-9` picks which slot to open at. No flag
  defaults to `-0`. E.g.:
  ```bash
  java -jar infimg.jar -3 photo.png
  ```
- **Live tracking**: while running, moving or resizing the window
  autosaves into whichever slot it was launched with (slot 0 by default,
  or slot *N* if started with `-N`).
- **Menu → Load Slot → *N***: jumps this window to slot *N*'s stored
  geometry and switches live tracking to slot *N* — from then on, further
  moves/resizes autosave into *N* instead of the slot you started with.
  Disabled for empty slots.
- **Menu → Save as Slot → *N***: promotes the window's *current* geometry
  into slot *N* and switches live tracking to *N*, same as Load Slot.
  Special case: if the session started on slot 0 (the default) and you
  promote to a different slot, slot 0 is first reverted to whatever it
  held *before this session started* — undoing any autosave drift picked
  up while slot 0 was still the active slot. This means "start on the
  default, then decide this run deserves its own slot" doesn't cost you
  your actual default position.

**Why this matters for embedding**: any caller, in any language, can
launch `infimg -3 some/path.png` and get "open at the user's preferred
position and size for this integration" for free — no window-geometry
protocol to design or implement on the caller's side. The user configures
where each numbered slot lives (via Menu, from any running instance) once,
and every future launch with that slot number honors it.

### Optional external-tool features

Some Menu features shell out to a command-line tool instead of
reimplementing its functionality — infimg only bundles `jackson-databind`
as a real dependency, so anything more (metadata extraction, format
conversion, etc.) rides on tools the user already has or installs
separately.

**infimg never probes for these on its own, at startup or in the
background.** Each optional feature is gated behind a boolean flag in
`infimg.json` (MITSA app-data dir) that starts `false`. You turn the flag on either by
clicking a menu item that checks for the tool right then (see below) or
by editing the JSON file yourself — no auto-detection at launch, no
startup cost, no surprise external-process launches for tools you never
asked for.

#### Metadata

**Menu → Metadata** always works — for a file loaded normally, a
clipboard paste, with or without ImageMagick installed — it just shows
more the more of those are true. Every version starts with the same
"usual file info" any OS file browser shows:

- **A real file**: last modified date/time, size on disk.
- **A clipboard paste** (no file on disk to report on): when it was
  pasted, and the in-memory pixel buffer's raw size in bytes instead.

Below that:

- **Without ImageMagick, or a clipboard paste**: pixel dimensions, color
  model, and — for a real file — the raw EXIF `Orientation` tag (see the
  **Load** row in the main toolbar table above for the auto-rotate this
  tag drives) — built entirely from what infimg already reads to load the
  image, no external tool involved. (ImageMagick's `identify` has no file
  to read for a paste, so this is what you get regardless of whether
  ImageMagick is installed.)
- **With ImageMagick, for a real file** (see below to enable): runs
  `identify -verbose <file>` and shows its raw output instead — full
  EXIF, ICC profile, histogram, everything that ImageMagick version
  reports, unparsed.

To enable the ImageMagick version:

1. Install ImageMagick.
   - **Linux**: `sudo apt install imagemagick` (Debian/Ubuntu/Mint) or your
     distro's equivalent — it's packaged pretty much everywhere.
   - **macOS**: `brew install imagemagick`. (macOS also ships a fair bit of
     built-in tooling of its own — `mdls`, Preview's Get Info panel — if
     you'd rather not install anything at all, though infimg doesn't wire
     those up today.)
   - **Windows**: [download the installer](https://imagemagick.org/script/download.php#windows)
     and make sure "Add application directory to your system PATH" is
     checked during install. Yes, this is the one platform where "just
     apt/brew install it" doesn't exist — sorry, not sorry.
2. With an image on screen, click **Menu → Detect ImageMagick**. infimg
   runs `identify -version` once, right then, and tells you whether it
   found it — if so, the flag is saved and **Metadata** is enabled
   immediately, no restart needed.

If `identify` isn't on `PATH` yet, the dialog just says so; install it and
click **Detect ImageMagick** again. You can also skip the click and edit
`infimg.json` (MITSA app-data dir) directly, setting `"imageMagick": true` by hand — the
file is small and obvious, and the flag is re-read from disk every time
Menu is built.

### Lighter / Darker / More Contrast / Less Contrast

**Menu → Lighter**, **Darker**, **More Contrast**, **Less Contrast** are
four plain one-click actions — no dialog, no slider, no numbers. Click
one, look at the result, click again (the same one, or a different one)
if it's still not right. This is deliberately the "click until it looks
right" interaction people already know from a light switch or a car
stereo's volume buttons, not a dial with units to understand.

An earlier version of this feature was a slider dialog with a numeric
L* offset (-50..+50) and a live preview. It worked, but it was more
control surface than most people actually want when a photo of someone
now gone is simply too dark — most users don't want to reason about "L*
units," they want a button that makes it better. The four-button version
replaced it.

Under the hood, both operate in CIELAB, not raw sRGB:

- **Lighter**/**Darker** add or subtract a fixed amount (5 points) from
  every pixel's perceptual lightness (**L\***), clamped to the valid
  0–100 range. This is the same distinction as CIELAB vs. naive RGB
  distance for color difference: `Color.brighter()`/`darker()`, and most
  "brightness" controls in other tools, just scale each of R, G, B by a
  factor in sRGB space — which is *not* perceptually uniform, so results
  skew and clip unevenly across the tonal range.
- **More Contrast**/**Less Contrast** push L* through a logistic S-curve
  centered on middle grey (L\*=50): values above 50 move toward 100,
  values below 50 move toward 0, by an amount controlled by the curve's
  steepness. Because it operates on lightness rather than each RGB
  channel independently, color balance doesn't shift as a side effect of
  a contrast change the way naive RGB contrast stretches can.

No installation and no config flag needed for any of this — it's a
native Java implementation (see `EnhancedColor.getCIELAB`/`fromCIELAB` in
`EnhancedColor.java`), always available.

- Each click's full sRGB→XYZ→CIELAB→adjust→XYZ→sRGB round trip runs **per
  pixel**, parallelized by row across
  `Runtime.getRuntime().availableProcessors()` threads. On a
  many-megapixel image this is real work — deliberately: correctness was
  chosen over speed, on the assumption that modern multi-core hardware
  can afford a fraction of a second per click for a properly computed
  result.
- Clicks **compound**: each one starts from whatever's currently on
  screen, not from the original file, so Lighter, Lighter, More Contrast
  stacks all three. Reload the file (or Menu → Load Slot / re-**Load**)
  to start over from scratch — there's no separate "undo."
- View state (zoom/rotation/pan) is never touched by these — only the
  pixels change.
- The title bar gets a trailing **`*`** the first time any of these four
  is clicked, marking that the on-screen pixels now differ from
  `currentFile` (or the clipboard grab that was loaded) — informative
  only, not enforced (no unsaved-changes prompt anywhere, including on
  Exit; Save first if you want the marked state kept). Zoom/rotate/pan
  alone don't set it — those are just how the unchanged image is
  currently being looked at, not a modification to it, even though Save
  always bakes in whichever view is current regardless. Reloading clears
  it, same as it resets everything else about the current edit.

### Look & Feel

**Menu → Look & Feel** picks the Swing look-and-feel: System Default, or
one of [FlatLaf](https://www.formdev.com/flatlaf/)'s four bundled themes
— Light, Dark, IntelliJ, Darcula. Unlike the tools above this needs no
installation or config-flag: `flatlaf` is a real, always-present Maven
dependency (a small, pure-Java jar with no native/system requirements),
so all five options are always available.

Picking one applies it to the running window immediately (no restart
needed to preview it) and persists the choice to `infimg.json` (MITSA app-data dir), so
future launches start with it already installed — this is also *why*
it's a config field rather than session-only: a look-and-feel has to be
set before any Swing component is created, which only the next process
launch can do properly.

**Default for a fresh install** (no `laf` in `infimg.json` (MITSA app-data dir) yet): System
Default on macOS/Windows, where the native look (Aqua/Windows) is
genuinely fine — but FlatLaf Darcula on Linux, since System Default there
usually resolves to GTK's Swing bridge, whose widgets (the file chooser
especially) look noticeably dated compared to FlatLaf's pure-Swing
rendering. This only affects a config with no stored choice yet; picking
anything via Menu always sticks, on any platform.

### Pixel Microscope

**Menu → Pixel Microscope...** opens a separate window for zooming into
individual pixels and their colour values on the image currently on
screen — works whether that image came from **Load** or **Paste**.

- Move the mouse over the main image window; the microscope window shows
  a magnified grid of the pixels around the cursor, updating live.
- Click-drag inside the grid to pan around, like moving a microscope
  stage.
- Selecting a pixel shows its exact colour in several representations:
  sRGB, YUV, CIELab, and HSB.
- A colour-frequency readout lists which colours appear most often in the
  currently visible grid.

The microscope window's size and position are remembered independently
from the main window, in the same `infimg.json` (MITSA app-data dir) used for everything
else — no separate config file.

### Quad ΔE Overlay

**Menu → Quad ΔE Overlay...** opens a separate window showing the image
currently on screen with boundary lines overlaid, marking where colour
stops being visually uniform. It works by recursively splitting the image
into quarters wherever a box isn't uniform enough, stopping once it is —
so dense grids of small boxes point at busy regions (ink, faint
bleed-through, texture), and large empty boxes point at flat, uniform
areas. The lines are drawn live on top of the image, never baked into the
pixels — closing the window or picking a different detail level doesn't
touch the original.

- **ΔE Detail** menu: **Coarse**/**Fine**/**Finest** presets (looser to
  tighter colour-matching tolerance — Finest can pick up scan noise on a
  photographed page as well as real content, which is expected, not a
  bug), or **Custom...** to type a specific tolerance number directly.
- **Copy**/**Save** on this window's own menu bar capture exactly what
  it's currently showing (image + boundary lines) — same behaviour as the
  main toolbar's Copy/Save, just scoped to this window.

The overlay window's size and position are remembered independently from
the main window, in the same `infimg.json` (MITSA app-data dir) as
everything else.

## Config file format

`infimg.json` (MITSA app-data dir):

```json
{
  "slots": [
    { "x": 100, "y": 100, "width": 1200, "height": 900 },
    null,
    null,
    ...
  ],
  "imageMagick": false,
  "laf": "System Default",
  "pixelMicroscope": null,
  "pixelMicroscopeSideWidth": 0,
  "quadOverlay": null,
  "recentFiles": []
}
```

- `slots`: always 10 entries, index 0–9. `null` means that slot has never
  been saved to.
- `imageMagick`: see above.
- `laf`: one of `"System Default"`, `"FlatLaf Light"`, `"FlatLaf Dark"`,
  `"FlatLaf IntelliJ"`, `"FlatLaf Darcula"`.
- `pixelMicroscope`/`pixelMicroscopeSideWidth`: last on-screen bounds and
  side-column width of the Pixel Microscope window; `null` until it's
  opened for the first time.
- `quadOverlay`: last on-screen bounds of the Quad ΔE Overlay window;
  `null` until it's opened for the first time.
- `recentFiles`: absolute paths of the last 10 files opened, most recent
  first — backs the **▾** dropdown next to Load.

infimg writes this file itself on every window move/resize, every
Menu → Save as Slot, every Menu → Look & Feel pick, and every successful
Load; hand-editing is only needed to flip feature flags like
`imageMagick`, or to seed/correct a slot's geometry directly.

## CI / Releases

Every push builds the jar (`.github/workflows/build.yml`) and uploads it
as a build artifact. Pushing a tag matching `v*.*.*` (e.g. `v1.0.0`)
additionally builds and publishes a GitHub Release with the fat jar
attached (`.github/workflows/release.yml`).

## Changelog

- **v1.9** — Renamed `--config` to `-c`/`--config-file` to match the
  `-c`/`--config-file` convention now standard across this author's
  other CLI tools.
- **v1.8** — Added **Menu → Quad ΔE Overlay...**, a separate window
  showing a live CIELab ΔE quadtree overlay on the current image —
  recursively splits the image into boxes wherever colour isn't visually
  uniform, surfacing subtle content (faint bleed-through, texture,
  boundaries) that's easy to miss by eye. Coarse/Fine/Finest presets plus
  a Custom... entry for a specific ΔE value; Copy/Save on the overlay
  itself. Also added a **▾** recent-files dropdown next to Load (last 10
  files, jumps straight back in).
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
  to the existing `infimg.json` (MITSA app-data dir), no separate config
  file.
- **v1.4** — Added long-form CLI flags mirroring existing Menu actions —
  `--rotate DEG`, `--flip-hor`/`--flip-ver`,
  `--lighter`/`--darker`/`--more-contrast`/`--less-contrast`, `--slot` —
  applied once to the first file shown, not as a batch mode. Added
  `--config PATH` to point at an alternate config file instead of the
  shared `infimg.json` (MITSA app-data dir), mainly so more than one
  instance can run side by side without fighting over the same
  window-position slots. Multiple positional file arguments now populate a
  **Prev**/**Next** list, navigating in the order given on the command
  line with no directory scanning.
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
  sets themselves after installing it. Added a **Look & Feel** submenu
  (System Default plus FlatLaf's four bundled themes —
  Light/Dark/IntelliJ/Darcula) via a new `flatlaf` dependency; switches
  instantly and the choice persists to `infimg.json` (MITSA app-data
  dir). Added **Lighter**/**Darker**/**More Contrast**/**Less Contrast**
  — one-click, no-dialog CIELAB L* nudges (a fixed offset per click for
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
