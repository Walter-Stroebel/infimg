# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Memory
At the start of a new session, actively recall your memories for this project
(`/home/walter/.claude/projects/-home-walter-github-infimg/memory/`) rather
than waiting for a memory to happen to become relevant — a saved memory
doesn't surface itself.

infimg is a sibling of `~/github/Voynich` (extracted from it 2026-08-10) and
shares its owner, its Java style rules, and its general engineering taste.
Voynich's own `CLAUDE.md` carries a lot of hard-won project history that
doesn't apply here (its catalog/region/checkpoint architecture is specific
to that app) — don't import that detail, but its Java Style section below
is duplicated here deliberately, because it's a standing rule for this
author's code everywhere, not a per-project preference.

## Feature scope — the one-sentence test
infimg started as "fit-to-window plus genuinely arbitrary rotation" and
grew a `[Menu]` button specifically so new features would have somewhere to
go without bloating the always-visible toolbar. That escape hatch makes it
easy to keep saying yes to "wow, cool" ideas — which is exactly why it
needs a deliberate brake, not vibes, deciding what's in scope.

**The test: could you explain the feature to a non-technical user in one
sentence, with no jargon, and have them immediately know what it does and
how to use it?** "Click Lighter until it looks right" passes. "Drag the L*
offset slider" does not — even though it's the same underlying operation.
A feature that needs a paragraph, a unit, or a mental model specific to
this app (a mode, a numeric parameter, a technical term) has crossed from
"the good tool nicer to live with" into "mini GIMP," which is explicitly
out of scope. When a good, technically-correct idea fails this test, the
fix is usually to simplify the *interaction* (see the brightness/contrast
slider-dialog → four-plain-buttons rewrite, 2026-08-11), not to reject the
underlying feature — the CIELAB math stayed, the UI got dumber.

External-tool integrations (see `MANUAL.md`'s "Optional external-tool
features") are a different, already-settled category: gated behind a
config flag that's off by default and only ever probed on an explicit
Menu click (e.g. Menu → Detect ImageMagick), never at startup or in the
background, and never bundled as a hard dependency. That pattern itself
passes the one-sentence test ("install the tool, click Detect in Menu")
and is the template for any future one.

## Build and Run
For development, build and run directly from Maven:
```bash
mvn package
java -jar target/infimg-1.6-jar-with-dependencies.jar [-0..-9] [optional-image-file]
```
End users install and run infimg through [MITSA](https://github.com/Walter-Stroebel/mitsa)
instead — see `INSTALL.md`. MITSA resolves the latest GitHub Release jar
and owns the `~/bin/infimg` launcher; infimg's release workflow only needs
to keep publishing `infimg-*-jar-with-dependencies.jar` on tagged releases
for MITSA to pick up, nothing else changes here.

There are no automated tests — this is a team of two (Walter plus this
assistant) with a tight manual-verify loop (see Feedback memory
`feedback_no_test_framework_by_design`); every feature in this file was
built, packaged, and clicked through live (screenshots via `import
-window`, `xdotool` for input) before being called done, not just compiled.

## Architecture
Single Maven module, Java 17, Swing. Deliberately a near-single-file app:
`ImageView.java` is the whole application (toolbar, canvas, menu, config,
adjustments); `EnhancedColor`/`FloatColor`/`YUV` are a self-contained
colour-math library carried over from Voynich, used only for the CIELAB
round-trips behind Menu → Lighter/Darker/More Contrast/Less Contrast.

| Class | Role |
|-------|------|
| `ImageView` | Entry point and the entire UI: toolbar (Load/Save/Paste/Copy/Rotate toggle/Fit/Menu/Exit), the `ImageCanvas` inner class (zoom/rotate/pan, mouse handling, `paintComponent`'s single `AffineTransform`), `AppConfig`/`ViewConfig` (persisted to `infimg.json` (MITSA app-data dir)), and every `Menu` feature (window slots, ImageMagick metadata, Look & Feel, brightness/contrast). See `MANUAL.md` for the user-facing behavior of each. |
| `EnhancedColor` | `java.awt.Color` subclass carrying the CIELAB/XYZ math (`getCIELAB`/`fromCIELAB`/`getXYZ`, `deltaE`, gamut checks). Pulled in from Voynich rather than reimplemented — see its class doc for the full API; infimg only exercises the CIELAB round-trip, allocation-free int-keyed variants (`getCIELAB(int, double[])`) for per-pixel-parallel work. |
| `FloatColor` / `YUV` | Supporting colour types `EnhancedColor` depends on (float RGBA, YUV distance) — not used directly by infimg's own features, kept because `EnhancedColor` references them. |

### Window-position slots
`AppConfig.slots` is a fixed 10-entry `ViewConfig[]` (index = CLI `-0`
through `-9`), not a single remembered position — see `MANUAL.md` for the
full mechanic (live-tracking follows whichever slot the window started on;
`Menu → Save as Slot` promotes the current geometry into a different slot
and, if promoting away from slot 0, reverts slot 0 to its pre-session
value so drift picked up during the run doesn't clobber the default). This
is the feature most worth understanding before touching `loadConfig`/
`saveIntoSlot`/`saveAsSlot` — the slot-0-revert logic is subtle and has
already had one real bug (an empty pre-session slot 0 wasn't reverting to
empty, fixed 2026-08-11 by replacing a null-as-sentinel check with an
explicit `preSessionSlotZeroCaptured` boolean).

### CIELAB adjustments
`Menu → Lighter/Darker/More Contrast/Less Contrast` each apply one fixed
step (`BrightnessStep`/`ContrastStep`, both `LabStep` implementations) via
`mapPerPixelLab`, which parallelizes the sRGB→XYZ→CIELAB→adjust→XYZ→sRGB
round trip by row across `Runtime.getRuntime().availableProcessors()`
threads. Clicks compound on whatever's currently on screen (not the
original file) via `ImageCanvas.replaceImageKeepingView`, which — unlike
`setImage` — leaves zoom/rotation/pan untouched. Pushing either control
many clicks past the intended "gentle nudge" range can drive a saturated
color's L* to a value outside the sRGB gamut at that lightness, which
`fromCIELAB`'s hard per-channel RGB clamp then renders as a visible hue
shift plus posterization — confirmed 2026-08-11 against a real
mixed-content test image (saturated purple costume, skin tones, green
foliage) and accepted as expected behavior at extreme settings, not a bug
to fix (see "Feature scope" above — this tool doesn't add gamut-mapping
complexity to protect against a user clicking a button ten times).

## Pixel Microscope (added 2026-08-17)

`Menu → Pixel Microscope...` opens a separate `AFrame`-based window (grid
view of the pixels around the cursor, drag-to-pan like a stage, per-pixel
sRGB/YUV/CIELab/HSB + colour-frequency readout) on the current
`canvas.source` `BufferedImage` — works for both file-loaded and
clipboard-pasted images, no temp file involved
(`ImageView.openPixelMicroscope`).

Started as a standalone prototype (`~/Prj/PixelInspector`, since deleted —
nothing reusable was left behind; it was purpose-built for this
integration) built by a sibling Claude Code session in this same
conversation with Walter. Landed here 2026-08-17 by copying its classes
into `nl.infcomtec.infimg.pixelmicroscope` (chassis: `AFrame`/`APanel`/
`GBCompass`/`TabSignals`/`BoundsRecallCallback`/`SwingProps`, all copied
verbatim from the `advswing` catalog module) and `nl.infcomtec.infimg`
(`ColorBase`/`ColorImage`/`TriElm`, copied from Voynich's lineage,
repackaged to reuse this project's *existing* `EnhancedColor`/`FloatColor`/
`YUV` rather than bringing in a second copy). `AwtColor` was also pulled in
fresh, at `nl.infcomtec.jacksonwrap.AwtColor` — `SwingProps`'s one external
dependency, small and self-contained.

**Window-bounds persistence deliberately does not use its own config
file.** `PixelMicroscopeFrame` takes a `BoundsPersistence` callback
interface in its constructor rather than owning a JSON file directly —
when embedded here, `ImageView.PixelMicroscopeBoundsPersistence` backs it
with two new `AppConfig` fields (`pixelMicroscope`/
`pixelMicroscopeSideWidth`) in the *existing* `infimg.json` (MITSA app-data dir), so there's
still just the one config file for the whole app. `PixelMicroscopeFrame`
only falls back to its own `pixelmicroscope.json` (MITSA app-data dir) when run standalone
via its own `main` (not the path used from infimg's menu).

This code was written under `nl.infcomtec.pixelinspector`'s (the
prototype's) own default Java conventions before the rename/copy, then
repackaged — it was re-checked against this file's "No `->` and no `::`"
rule and comes up clean (verified via `grep -rn '\->|::'` across the new
package, no hits), but it wasn't written *with* this CLAUDE.md loaded, so
treat it with slightly more scrutiny than code written natively in this
repo if something looks off.

Follows the "Feature scope" one-sentence test: "click Menu → Pixel
Microscope to zoom into individual pixels and their colour values" is a
complete, jargon-free description — the radius slider and colour-frequency
figures are details of *that one screen*, not new concepts layered onto
infimg's own toolbar/mental model.

## Toolbar icons (added 2026-08-23)

Every toolbar button now carries a real icon alongside its text label —
Load, Save, Paste, Copy, Rectangle, Lasso, Rotate (wheel), Prev, Next,
Fit, Menu, Exit — sourced from a growing catalog icon set the catalog
session built and extended three times over the same day
(`/home/claude/catalog/tools/src/main/resources/icons_svg`, now 43 icons;
infimg's own vendored copy holds only the 26 names actually used here:
the original "generic app chrome" set plus `marquee`/`lasso` for the
selection tools and `copy`/`paste`/`rotate` added on request once Walter
did a full toolbar pass and flagged the remaining text-only buttons).
Same integration pattern as Pixel Microscope: `nl.infcomtec.icons.Icons`
(`Icons.java`) and the `icons_svg` resource were copied verbatim into
this repo rather than taken as a Maven dependency — confirmed directly by
the catalog maintainer session as `catalog/`'s actual documented
distribution convention project-wide ("a poor man's Maven Central, chosen
deliberately"), not an infimg-specific workaround.

`ImageView.toolbarIcon(String)` wraps `Icons.getIcon(name, 24, "#404040")`
— fixed dark-gray backing square, not transparent or LAF-derived: the
icon stroke color is hardcoded `#e0e0e0` light gray in the catalog set
(see `ICON_STYLE.md`), so a dark backing rect is what gives it visible
contrast. First pass used 16px on a transparent background and Walter
immediately flagged it as "too black and too small" — a transparent rect
exposes whatever's behind the button, which reads as low-contrast/
blob-like at that stroke color and size, not as a crisp icon. Returns
`null` on any miss — `Icons.getIcon` already logs the reason to stderr,
and a `null` `Icon` just leaves a `JButton` as text-only, so no extra
fallback handling was needed; this is also the rule for any future
toolbar addition — check the icon set actually has a fitting name before
wiring one in, a bad-fit icon reads as *more* confusing than a plain
label, not less (this is why Magic Wand-adjacent or clipboard-shaped
icons were never invented ad hoc here, only ever requested from the
catalog session when a real gap was found).

**Prev/Next needed an explicit `setDisabledIcon` call, not just an icon
constructor argument.** Swing auto-generates a disabled-state icon via
`GrayFilter` the first time `getDisabledIcon()` is read, and that filter
collapses this icon set's already-light `#e0e0e0` stroke into a nearly
featureless gray square — confirmed 2026-08-23 by rendering a disabled
`JButton` offscreen and inspecting the actual pixels, not by guessing.
`prevButton.setDisabledIcon(prevButton.getIcon())` (same for `nextButton`)
skips that filter and just reuses the enabled icon, which reads fine —
apply the same fix to any future icon-carrying button that can start (or
go) disabled.

**Confirmed 2026-08-23, not fixed: this icon set only reads well in a
dark-background Look & Feel** (Darcula, FlatLaf Dark) — a light LAF
(System Default, FlatLaf Light/IntelliJ) flattens the fixed `#404040`
backing square and the light `#e0e0e0` stroke into a muddy, low-contrast
gray toolbar, screenshot-confirmed by Walter. Investigated properly
before deciding to ship anyway: this is exactly the problem FlatLaf's own
`FlatSVGIcon`/`ColorFilter` mechanism exists to solve (recognizing a
placeholder stroke color and remapping it per active theme, the same
general pattern IntelliJ's `_dark.svg` suffix convention and VS Code's
`currentColor`/CSS-variable icons use) — but that requires switching from
`Icons.getIcon`'s ImageMagick-rasterize-to-PNG pipeline to FlatLaf's own
SVG-aware icon class, and the catalog `icons_svg` set's literal `#e0e0e0`
would need to become a recognized theme-aware placeholder rather than a
baked-in color. That's a real, correctly-scoped future improvement, not
attempted here — Walter's call (2026-08-23): "Ship without it,
Darcula-only for now." Practically: **the icon toolbar assumes Menu →
Look & Feel → FlatLaf Darcula (or another dark FlatLaf variant)**; if a
future session revisits light-mode icon support, start from FlatLaf's
`ColorFilter` docs/`FlatSVGIcon`, not another runtime color-inversion
hack layered on the current ImageMagick pipeline — that was considered
and rejected as the wrong layer to fix this in.

(Dark mode also draws less power on an OLED/most modern panel — so
Darcula-only is, in a small way, the environmentally responsible default
too.)

Needs ImageMagick's `convert` on PATH at runtime (icons rasterize via a
subprocess call, cached in-memory by name+size+background) — same
runtime assumption already carried by the optional `imageMagick`
metadata feature (see "Feature scope" above), so this doesn't add a new
kind of dependency to the app, just a second use of one already gated by
presence-checking rather than being bundled.

## Rectangle/Lasso selection (added 2026-08-23)

Toolbar gains two mutually-exclusive `JToggleButton`s, Rectangle and Lasso,
next to Copy. When one is armed and a selection is committed (a completed
rectangle drag, or a lasso closed by clicking near its first vertex —
right-click undoes the last placed vertex), Save and Copy operate on that
selection instead of the full view: cropped to the selection's bounding
box, with any area inside that box but outside the actual selected shape
(the lasso's non-rectangular slack) painted solid black. Toggling a tool
off then back on — or switching to the other tool — is the reset gesture;
there's no separate Clear/Reset button, matching the toolbar's existing
"each toggle watches its sibling" idiom (`SelectionToolToggle`,
mirroring `ImageView`'s pre-existing pattern for Look & Feel-adjacent
toggles).

**Explicitly out of scope**: Magic Wand / flood-fill selection. Walter's
own words when scoping this: "No magic wand, that's a nice 'extra catch'
but the core lasso/rect refinement... is new to it." Only Rectangle and
Lasso exist here — the vendored `SelectionMasks.java` had its
`fromFloodFill` factory (and the `ColorToleranceCondition`/`MaskContour`
classes it and the wand-only contour-outline drawing depend on) stripped
out during vendoring for exactly this reason, not left in unused.

**Vendored, not a Maven dependency** — same distribution model as Pixel
Microscope and the toolbar icons: `catalog/selection`
(`nl.infcomtec.selection`: `RubberBandLine`, `VertexLasso`, `PointMapper`,
`XorDrawing`, a trimmed `SelectionMasks`) and the two classes of
`catalog/images` it depends on (`nl.wers.library.images.BitSet2D`,
`MaskComposite`) were copied verbatim into this repo's own package tree.
Confirmed directly by the catalog maintainer session afterward: this
vendor-by-copy approach is `catalog/`'s actual documented distribution
convention project-wide (see `catalog/INDEX.md`, "a poor man's Maven
Central, chosen deliberately"), not an infimg-specific workaround — the
same session had originally (and incorrectly) suggested a Maven
dependency for the icons integration before correcting itself.
`RubberBandBox` was vendored too at first but removed once nothing in
this repo called it any more (see "Selection is paint-mode, not XOR"
below) — a straight port of `SelectionToolsDemo.java`'s wiring, XOR mode
included, shipped first and got corrected live against real feedback.

**Coordinate space is deliberately trivial**: selection lives entirely in
`ImageCanvas`'s own component space via an identity `PointMapper`
(`ImageCanvas.IdentityPointMapper`) — no mapping through the zoom/
rotation/pan `AffineTransform` at all, because Save/Copy already operate
on `renderCurrentView()`'s rasterized output (see that method's own doc),
which *is* component space. A selection dragged out on screen therefore
maps 1:1 onto exactly the pixels Save/Copy would otherwise write, with no
separate coordinate transform to keep in sync with `paintComponent`'s.

**Selection outlines are paint-mode, not XOR** (corrected 2026-08-23,
after initially following `SelectionToolsDemo.java`'s XOR wiring
line-for-line): `Graphics2D#setXORMode` draws `background XOR color`, not
`color` — against a light background that XOR result happens to look
close to the chosen color, but against a dark image region it can land on
a completely different, much less visible hue (confirmed live: yellow
selection lines went near-invisible blue-gray over a dark truck in
Walter's real test image). The committed rectangle outline, the lasso's
placed segments/closing edge/vertex dots/close-tolerance ring, and the
live "next vertex" guide line are all drawn as ordinary paint-mode shapes
fresh in `paintComponent` (`ImageCanvas.drawRectangleOverlay`/
`drawLassoOverlay`), not XOR — driven by plain fields (`rectAnchor`/
`rectCursor`, `lassoCursor`) updated on `mouseMoved`/`mouseDragged` plus
`repaint()`, the same pattern the canvas already used for panning. This
is also why the vendored `RubberBandBox` and `VertexLasso`'s own
`updateCursor`/`drawPlacedSegments` methods are unused/removed: mixing a
paint-mode `repaint()` (which redraws the whole canvas from scratch,
erasing XOR content without pairing it with the matching XOR erase call)
with XOR bookkeeping desyncs the erase/redraw pairing that XOR mode
depends on — confirmed as a real bug twice (the lasso's live guide line,
then the rectangle's XOR crosshair below), not a hypothetical concern.

**Rectangle mode's crosshair ruler genuinely is XOR, on purpose** — the
one deliberate exception to the above. Once Rectangle is armed, a
full-width/full-height yellow line pair follows the cursor continuously
(not just while dragging), via real `Graphics2D#setXORMode` erase/redraw
against `getGraphics()` (`ImageCanvas.crosshairPos`/`updateCrosshair`/
`eraseCrosshair`). Walter's own framing for why this needed to exist
(2026-08-23): it's a ruler for placing the *first* corner precisely
("'I want the box to pass just over her head and in front of her tits'
is now VISIBLE due to the XOR lines... then dragging the box... is
automatic") — ordinary mouse-position feedback, not the selection outline
itself, and genuinely too cheap-per-pixel-of-movement to justify a full
repaint the way the outline drawing is. Because a rectangle drag's own
paint-mode repaint (for the drag-rectangle outline) can invalidate the
crosshair's last-drawn position the same way a lasso click's repaint
once did, every call site that repaints during Rectangle mode
(`handleSelectionPress`, `mouseDragged`, `handleRectangleRelease`) forces
that repaint synchronously via `paintImmediately` first, then resets
`crosshairPos` to null and redraws fresh — the same erase-desync class of
bug as the lasso's, fixed the same way, just for a genuinely-XOR field
instead of a paint-mode one this time.

Mouse-handling wiring otherwise still follows
`catalog/demos/SelectionToolsDemo.java`'s general shape (press/drag/
release, click-sequence lasso, the `clearAnchor()`-not-`begin(null)`
gotcha) where the two apps' shapes actually match — that demo remains
the validated reference implementation for the *interaction sequence*,
just not for its XOR-vs-paint-mode drawing choices, which infimg's real
test image proved wrong for this app's use case (arbitrary photo
content, not the demo's flat-color test image).

**Save/Copy/Paste/Load share one mutable image list** (`ImageView.
imageList`/`imageIndex`, redesigned 2026-08-23 after an initial two-
parallel-lists version — one for command-line files, one for paste
history — that Walter flagged as over-complicated: "prev/next should
operate over a shared single and mutable list of images, a list element
should 'know' its source"). Each `ImageEntry` is either file-backed
(`file` set, reloadable from disk any time) or an unsaved paste
(`pastedImage` holding the actual pixels, since there's nothing on disk
to reload from). Load (toolbar button, recent-files menu) and Paste both
insert a new entry right after the current one, truncating anything
after it first — same "new branch abandons the old redo tail" rule as a
text editor's undo stack — rather than either overwriting the current
view or blindly appending past a point the user had stepped back to.
Save converts the *current* entry to file-backed in place (sets `file`,
clears `pastedImage`) — Walter's words: "Save will REPLACE that with
'new on disk' location in both cases" (paste and file entries alike) —
so stepping back to that entry later with Prev/Next reloads from the
just-written file, not stale in-memory pixels or the file's old
location. `--rotate`/`--flip-*`/etc. CLI flags still apply once, to the
first entry only, via `ImageView.loadInitialFiles`, which seeds the
whole list from argv up front so Prev/Next work across all of them
immediately (not lazily discovered one file at a time).

## Java Style — Non-Negotiable
(Identical standing rule across this author's Java projects — see
Voynich's `CLAUDE.md` for the fuller rationale/history behind each point;
summarized here so this file is self-sufficient.)

### What Java Is
Java is a mature, complete, high-performance language on a JIT JVM at
roughly 2x C performance. Not a slow legacy system. Write it with
confidence in what it is.

### Two Different Kinds of "New"
Java 1.7 (2011) is this author's line for the last version whose additions
were unambiguously good — after which the language commission's changes
read as cargo-culting whatever C#/Rust/Go had just shipped, chasing
brevity for its own sake rather than closing a real gap. But that's not a
blanket "nothing after 1.7" rule, and being precise about *why* some later
additions are fine and others aren't is what keeps this from collapsing
into reflexive nostalgia the next time a genuinely good stdlib method
ships.

The actual distinction: **stdlib convenience vs. new syntax/inference.**
`InputStream.readAllBytes()` (Java 9), `String.isBlank()` (Java 11) —
these are "a library finally grew the method everyone was hand-rolling
anyway." No new grammar to parse, nothing hidden — the call site still
says exactly what type and method are involved. Both are used or
acceptable in this codebase without a second thought (`readAllBytes()` is
in `ImageView.showMetadata`).

`->`/`::` (lambdas, method references), `var`, records — these buy
brevity by deleting information the reader (human or LLM) has to
reconstruct from context: which functional interface, which concrete
type, which method is actually being called. That's a second grammar for
something Java already had a perfectly good name for, not a missing
convenience. This is why the no-lambda rule below is absolute rather than
"avoid when it hurts readability" — the harm is structural (information
removed from the source), not a matter of degree that trivial cases could
be exempted from.

### Language Idiom
Prefer explicit, named, `Object`-contract-respecting Java.

**No `->` and no `::`, anywhere, full stop — not even for a "trivial"
one-liner.** Use an explicit anonymous (or named) class implementing the
real functional interface instead: `new ActionListener() { public void
actionPerformed(ActionEvent e) { ... } }`, not `e -> ...`. A lambda or
method reference hides the actual type being constructed from both the
compiler's target-type inference and the reader (human or LLM) — the
concrete type is information genuinely missing from the source, not a
style nicety. (This bit twice in this repo's own history: 2026-08-11,
`ImageView::adjustBrightness`/`ImageView::adjustContrast` method
references were caught and replaced with named `LabStep` implementations
— `BrightnessStep`/`ContrastStep` — while writing this file.)

Streams beyond a trivial filter-and-collect chain carry the same cost —
prefer a plain loop.

Records automate the `Object` contract rather than fulfilling it. Fulfill
it explicitly.

### Threading
Normal hardware has 2–16 cores. Single-threaded Java is a special case
requiring justification. `mapPerPixelLab` (see above) is this codebase's
example: a fixed-size `ExecutorService` sized to
`Runtime.getRuntime().availableProcessors()`, one task per row-range.

### Multi-Monitor
Users have 0 to N monitors — this author's own machine runs three 4K
monitors (see memory `user_dev_environment`). `AppConfig.slots` and
`GraphicsEnvironment.getLocalGraphicsEnvironment()` (used for the
first-run default size) are already written with this in mind; don't
assume single-monitor coordinates when touching window placement code.

### Time
Represent instants as a `long` epoch-millisecond
(`System.currentTimeMillis()`), not `java.time.Instant`/`LocalDateTime`.
infimg's own clipboard-paste title (`"(clip) %tH:%<tM:%<tS.%<tL"`) already
follows this. Reach for `java.time` only for genuine calendar arithmetic.

### UI
Swing is the UI toolkit. Complete, stable, in the JDK. Do not reach for
JavaFX.

### Frameworks
Spring is not Java. Not applicable to a single-JFrame desktop app anyway,
but the principle — explicit construction over annotation-driven magic —
still governs.

### Dependencies
Every dependency is a transitive closure of decisions not made, upgrade
cycles now owned. This repo's whole dependency list is `jackson-databind`
(tiny config file I/O) and `flatlaf` (a small, pure-Java, no-native-code
jar, added 2026-08-11 for the Look & Feel picker) — both add a genuine
capability the JDK doesn't, neither pulls in a deep transitive graph.
ImageMagick is deliberately *not* a Maven dependency: it's an external
CLI tool, gated behind a user-set config flag (see "Feature scope" above),
never bundled or auto-installed.

### Javadoc
Readers (human or LLM) are expected to read and understand the code —
Javadoc documents what can't be recovered by reading: a class's
role/lifecycle, a field's ownership/persistence contract, non-obvious
behavior (e.g. why `renderCurrentView` uses `TYPE_INT_RGB` not `_ARGB` —
see that method's doc for the actual Linux/X11 clipboard bug it works
around). Don't document getters/setters or anything already stated by a
name plus its immediate context.
