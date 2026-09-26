# BookmarkTV

A Google TV / Android TV app: bookmark any video site's URL, and it opens in a
remote-friendly browser that detects the site's actual video stream and hands
it off to a native player for a proper TV playback experience.

## What it actually does

- **Home screen**: your bookmarked sites as TV-style cards (Leanback), with a
  best-effort thumbnail (the page's `og:image`, falling back to favicon).
- **Browser screen**: loads the real page in a WebView, with injected
  JavaScript (`assets/tvnav.js`) that makes it navigable by D-pad — arrow keys
  move focus between links/buttons/thumbnails using simple on-screen spatial
  logic, and SELECT clicks the focused element. Because it's the site's real
  page, its own thumbnails, suggestions, and playlists all show up normally.
- **Stream detection**: every network request the page makes is passively
  inspected (`shouldInterceptRequest`) for an HLS (`.m3u8`), DASH (`.mpd`), or
  direct `.mp4` URL. Nothing is blocked or altered — this is observation only.
- **Player screen**: once a stream is detected, SELECT jumps straight to a
  native ExoPlayer (Media3) full-screen player with proper remote seek/
  play/pause and a forced "always pick the highest available bitrate/
  resolution" track selection — this is what "max quality" means in practice
  for adaptive streams. If more than one stream was seen during the session,
  the rest queue up as a simple "up next" playlist.

## What this is *not*

Being upfront about the limits so you don't hit surprises:

- **It doesn't reconstruct each site's suggestions/playlist UI natively.**
  Every site is structured differently; there's no generic way to turn
  "recommended videos" or "related playlist" into native TV cards for
  arbitrary sites. You'll see the site's own version of that UI, just made
  D-pad navigable. The player's "Up next" queue is only an approximation
  based on what streams were detected during that browsing session.
- **DRM-protected video won't play in the native player.** Widevine-encrypted
  streams need a licensed CDM path this app doesn't implement; those will
  only work (if at all) inside the WebView itself. Ad-supported free
  streaming sites are usually not DRM-protected, but some are.
- **Spatial navigation is a heuristic**, not a real accessibility API. It
  works well on conventional link/button/thumbnail-grid pages. Complex
  JS frameworks with virtualized lists, shadow DOM, or custom widget sets
  may navigate awkwardly — there's no universal fix for this since it
  depends entirely on the target site's markup.
- **Only use this with sites you're actually allowed to access** (ad-supported
  free services, a broadcaster's own site, anything with authorized/licensed
  streaming). This app doesn't fetch or bypass anything — it's a remote-
  friendly viewer for pages you already have legitimate access to.

## Project layout

```
BookmarkTV/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/tvnav.js              # injected D-pad navigation JS
│       ├── java/com/bookmarktv/app/
│       │   ├── MainActivity.kt          # Leanback home screen
│       │   ├── BrowserActivity.kt       # WebView + stream sniffing + nav
│       │   ├── PlayerActivity.kt        # ExoPlayer full-screen playback
│       │   ├── BookmarkCardPresenter.kt
│       │   ├── Bookmark.kt
│       │   ├── BookmarkRepository.kt
│       │   └── SiteMetadataFetcher.kt
│       └── res/                         # layouts, theme, banner, icons
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/build-apk.yml      # CI build -> downloadable APK
```

## Building it

You have two options — pick whichever is easier for you.

### Option A: Android Studio (recommended if you want to keep editing it)

1. Install [Android Studio](https://developer.android.com/studio).
2. `File → Open`, select the `BookmarkTV` folder.
3. Let it sync (it will fetch dependencies and, on first open, generate the
   Gradle wrapper files automatically — this project was generated without
   internet access to Gradle's own servers, so those wrapper files aren't
   pre-included; Android Studio creates them itself on sync).
4. `Run ▸ Run 'app'`, targeting a Google TV device or emulator
   (`Tools ▸ Device Manager ▸ Create device ▸ TV`).

### Option B: GitHub Actions (no local Android Studio needed)

1. Push this folder to a new **public** GitHub repository (public matters — see
   below).
2. The included workflow (`.github/workflows/build-apk.yml`) runs
   automatically on push to `main`, or trigger it manually from the
   **Actions** tab (`Run workflow`).
3. It builds the APK, uploads it as a workflow artifact, *and* publishes it
   as a public **GitHub Release** asset (tagged `build-<run number>`).
4. Once it finishes, either download the artifact from the Actions run
   yourself, or — if the repo is public — tell Claude the `owner/repo` name.
   Claude's sandbox can reach `github.com`, `api.github.com`, and
   `release-assets.githubusercontent.com` directly, so it can fetch the
   finished APK from the release and hand it back to you in the
   conversation, no manual download needed. This only works for **public**
   repos — a private repo's releases need an auth token Claude doesn't have.

### Installing the APK on your Google TV

With [adb](https://developer.android.com/tools/adb) and your TV's IP address
(Settings → System → About → Network):

```bash
adb connect <TV_IP_ADDRESS>:5555
adb install app-debug.apk
```

## Replacing the placeholder artwork

The launcher icon and TV banner (`res/drawable/ic_launcher_foreground.xml`,
`res/drawable/tv_banner.xml`) are simple placeholder vector shapes so the
project builds out of the box. Swap in real artwork before you use this
seriously — Google TV requires a 320×180 banner for the home screen tile.

## Ideas for extending it

- Add a settings toggle to always force the highest track vs. adaptive.
- Persist per-bookmark "last watched position" for resume.
- Add a proper HTML parser instead of the current regex-based title/og:image
  fetch in `SiteMetadataFetcher` for more reliable metadata on odd markup.
