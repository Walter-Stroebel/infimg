# infimg manual

Feature-by-feature reference. For the pitch and build instructions, see
[README.md](README.md).

## Main toolbar

| Button | Does |
|---|---|
| Load | Open an image file via the OS file chooser. |
| Save | Write exactly the current on-screen pixels (zoom, rotation, pan, crop all baked in) to a new file. |
| Paste | Load whatever image is on the system clipboard. |
| Copy | Copy exactly the current on-screen pixels to the system clipboard. |
| Rotate (wheel) | Toggle — when down, the mouse wheel rotates the image through an arbitrary angle instead of zooming it. |
| Fit | Rescale and re-center to fill the window as it is now — for after a manual resize or a wandered-off zoom/pan. |
| Menu | Everything below this line. |
| Exit | Close with no "unsaved changes?" nag — Save first if you want the current view kept. |

Click-drag pans; mouse wheel zooms (or rotates, with the toggle down).

## The Menu button

The main toolbar is deliberately small and will stay that way. Every new
feature that isn't one of the core "look at an image" actions goes behind
**Menu** instead, as a popup with submenus — so the tool keeps its simple,
obvious front while still being able to grow arbitrarily much clutter
behind one button.

### Window-position slots

infimg remembers **10** window positions/sizes, not just one, in
`~/.infimg.json`. Each is an independent slot, numbered 0–9.

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

**infimg never probes for these itself.** Each optional feature is gated
behind a boolean flag in `~/.infimg.json` that starts `false` and that
*you* set to `true` once the underlying tool is installed and on `PATH`.
No auto-detection code, no startup cost, no surprise external-process
launches for tools you never asked for.

#### Metadata (ImageMagick)

**Menu → Metadata** runs `identify -verbose <file>` on the currently
loaded file and shows the raw output in a scrollable dialog — full EXIF,
ICC profile, histogram, everything ImageMagick reports, unparsed.

To enable:

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
2. Confirm it's on `PATH`: `identify -version` should print a version, not
   "command not found".
3. Edit `~/.infimg.json` and set `"imageMagick": true`.
4. Restart infimg (or just reopen Menu — the flag is re-read from disk
   every time Menu is built). **Metadata** is now enabled whenever an
   image loaded from a file (not a clipboard paste — there's no file to
   hand `identify`) is on screen.

If you'd rather not touch JSON by hand: the file is small and obvious,
just open it in any text editor.

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
needed to preview it) and persists the choice to `~/.infimg.json`, so
future launches start with it already installed — this is also *why*
it's a config field rather than session-only: a look-and-feel has to be
set before any Swing component is created, which only the next process
launch can do properly.

## Config file format

`~/.infimg.json`:

```json
{
  "slots": [
    { "x": 100, "y": 100, "width": 1200, "height": 900 },
    null,
    null,
    ...
  ],
  "imageMagick": false,
  "laf": "System Default"
}
```

- `slots`: always 10 entries, index 0–9. `null` means that slot has never
  been saved to.
- `imageMagick`: see above.
- `laf`: one of `"System Default"`, `"FlatLaf Light"`, `"FlatLaf Dark"`,
  `"FlatLaf IntelliJ"`, `"FlatLaf Darcula"`.

infimg writes this file itself on every window move/resize, every
Menu → Save as Slot, and every Menu → Look & Feel pick; hand-editing is
only needed to flip feature flags like `imageMagick`, or to seed/correct
a slot's geometry directly.
</content>
