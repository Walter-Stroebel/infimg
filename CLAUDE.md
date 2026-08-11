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
user-set config flag, never auto-probed, never bundled as a hard
dependency. That pattern itself passes the one-sentence test ("install the
tool, flip a switch in Menu") and is the template for any future one.

## Build and Run
```bash
mvn package
java -jar target/infimg-1.1-jar-with-dependencies.jar [-0..-9] [optional-image-file]
```
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
| `ImageView` | Entry point and the entire UI: toolbar (Load/Save/Paste/Copy/Rotate toggle/Fit/Menu/Exit), the `ImageCanvas` inner class (zoom/rotate/pan, mouse handling, `paintComponent`'s single `AffineTransform`), `AppConfig`/`ViewConfig` (persisted to `~/.infimg.json`), and every `Menu` feature (window slots, ImageMagick metadata, Look & Feel, brightness/contrast). See `MANUAL.md` for the user-facing behavior of each. |
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
</content>
