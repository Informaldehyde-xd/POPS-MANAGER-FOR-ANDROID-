# POPS Manager (scaffold)

## Building from a phone only (no computer)

1. Unzip this on your phone (any file manager / "Files" app can unzip).
2. Go to github.com in your phone's browser (or the GitHub app) and create a
   new empty repository.
3. Open the repo → **Add file → Upload files**, then select/drag every file
   and folder from the unzipped project (mobile Chrome's file picker lets
   you multi-select; keep the folder structure - `.github/workflows/build.yml`
   must land at that exact path).
4. Commit directly to `main`.
5. Go to the **Actions** tab - the workflow runs automatically. When it's
   done, open the run → **Artifacts** → download `pops-manager-debug-apk`
   (a zip containing the `.apk`) straight to your phone.
6. Install it: tap the downloaded `.apk` in your Files app (you'll need to
   allow "install unknown apps" once).

No Gradle, Android Studio, or terminal required anywhere in this flow -
GitHub Actions' cloud runner does the actual build.


Android app to manage a USB drive of PS1-on-PS2 (POPS) games: convert
BIN/CUE → VCD, rename + fetch cover art, and install everything into
`USB:/POPS` in the layout POPStarter/OPL expect.

## What's wired up

- **Gradle + GitHub Actions** (`.github/workflows/build.yml`): builds a debug
  APK on every push/PR, and a release APK on `main` (upload as artifact -
  wire up signing secrets to actually publish it). CI installs Gradle
  itself (`gradle/actions/setup-gradle`), so there's **no Gradle wrapper to
  generate** - nothing you need a computer for.
- **Conversion** (`converter/Cue2PopsConverter.kt`): runs a bundled native
  `cue2pops` binary (compiled from https://github.com/israpps/cue2pops
  during CI — see `Vendor cue2pops source` step) via `ProcessBuilder`.
  Shipped as `libcue2pops.so` so Android's exec-sandbox permits running it.
- **POPStarter install** (`popstarter/PopstarterInstaller.kt`): writes the
  converted `.VCD` into `USB:/POPS/SERIAL.Title.VCD` via Storage Access
  Framework (required since Android 10+ blocks raw filesystem paths to
  USB/SD for non-owned apps).
- **Cover art** (`cover/CoverArtFetcher.kt`): stubbed against TheGamesDB —
  needs your API key and the actual serial→title mapping (see below).

## Things you need to decide / supply before this actually works

1. **POPSTARTER.ELF / POPS_IOX.PAK** — these are POPStarter's own binaries.
   I haven't bundled them (they're a separate homebrew project's build
   output, not something to vendor blindly). You supply your own copies and
   the app copies them in via `installBootFiles()`.
2. **Cover art source** — TheGamesDB needs a free API key; ScreenScraper is
   another option with better PS1-specific art. Also: neither indexes by
   PS1 serial directly, so you'll want a serial→title lookup table first
   (redump.org's `.dat` files are the standard free source for this) and
   then search the art API by title.
3. **Multi-track games** (BIN split per track, or CD-audio tracks) —
   cue2pops expects a single merged BIN. If you want to support those,
   add a merge step (`psx-vcd` or `binmerge`) before calling cue2pops.
4. **Signing** — release build currently produces an unsigned APK; add a
   `signingConfig` block in `app/build.gradle.kts` reading from the
   `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` secrets already passed
   into the workflow if you want CI to produce an installable release APK.

## Repo layout

```
app/src/main/java/com/popsmanager/
  converter/     BIN/CUE -> VCD (native bridge)
  cover/         title + boxart lookup
  popstarter/    USB /POPS installer
  ui/            activities (MainActivity has the pipeline stub)
app/src/main/cpp/
  CMakeLists.txt builds cue2pops (vendored by CI, see workflow)
.github/workflows/build.yml
```
