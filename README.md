# ImmoSnap

Android app that photographs Belgian "for sale" signs and finds the matching real estate listing on immoweb.be / zimmo.be / immovlan.be.

Point your phone at a `TE KOOP` / `À VENDRE` / `FOR SALE` sign → the app reads the agency name, looks up your location, searches the big Belgian listing sites, and opens the most likely listing in your browser.

## How it works

```
  photo ──► Gemini vision OCR ──► agency name, phone, ref#
                  │
  GPS / EXIF ──► Google Maps geocoding ──► street, city, postal code
                  │
                  ▼
    Gemini 3 Flash + Google Search grounding
                  │
                  ▼
    candidate listing URLs (agency site → immoweb → zimmo → immovlan)
                  │
                  ▼
    scrape og:image + listing photos ──► Gemini vision ranking
                  │
                  ▼
    confidence-ranked matches → open in browser
```

## Build

**Requirements:**
- Android Studio Ladybug+ (or JDK 17+ and Android SDK 35 on the command line)
- 4 API keys (see below)

**Clone and configure:**

```bash
git clone https://github.com/merlinfrombelgium/immosnap.git
cd immosnap
cp local.properties.example local.properties
# Edit local.properties and fill in your keys
```

**Build from the command line:**

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

**Or open in Android Studio** and press Run.

## API keys

All four live in `local.properties` (gitignored). The `buildConfigField` wiring in `app/build.gradle.kts` exposes them to the app at compile time.

| Key | Where to get it | Used for |
|-----|-----------------|----------|
| `GEMINI_API_KEY` | https://aistudio.google.com/apikey | OCR, search grounding, image ranking |
| `MAPS_API_KEY` | Google Cloud Console → Maps APIs | Reverse geocoding (GPS → address) |
| `SEARCH_API_KEY` | *(unused — legacy)* | — |
| `SEARCH_ENGINE_ID` | *(unused — legacy)* | — |

`SEARCH_API_KEY` and `SEARCH_ENGINE_ID` are leftover from the pre-Gemini-grounding Custom Search implementation (see commit `64121eb`). Placeholder values are fine.

## Permissions

At first launch, the app requests:

- `CAMERA` — to photograph the sign
- `ACCESS_FINE_LOCATION` — to geocode the current address
- `ACCESS_MEDIA_LOCATION` *(Android 10+)* — to read EXIF GPS from gallery photos

Without these, the app shows a "permissions required" screen.

## Using the app

1. **Snap** — full-screen camera; tap the big button to capture a sign (works best when the agency logo and text are clearly readable)
2. **Gallery** — tap the photo icon to pick an existing photo (uses EXIF GPS if present — no device GPS needed)
3. **Processing** — shows the current pipeline stage (reading sign → finding address → searching listings → matching photos)
4. **Result** — top-ranked match opens in the browser; for lower-confidence cases you see the top 3 candidates to pick from

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| "No listings found" | Gemini grounding returned nothing, even after the built-in fallback chain (drop street → drop agency). Check the debug card — if OCR got the agency name and the address looks right, the listing may not be publicly indexed. |
| Times out on "Finding address..." | GPS lock failed after 15s. The error message will suggest the gallery picker — pick a photo with EXIF GPS instead. |
| Permissions screen keeps showing | Tap the **Grant permissions** button on the fallback screen to retry the system dialog. If you previously denied a permission, you may need to enable it in Settings → Apps → ImmoSnap. |
| Wrong listing as top match | The image-matching stage had poor signal (building photos not scraped, listing site blocks bots). Tap through the top-3 fallback. |

The **debug card** on the result screen shows OCR text, agency name, GPS, resolved address, the Gemini search query, and all candidate URLs with verification status. Use it to diagnose pipeline failures.

## Project structure

```
app/src/main/java/com/immosnap/
├── MainActivity.kt              # permission gate + root composable
├── ImmoSnapApp.kt               # NavHost: camera → processing → result
├── ocr/
│   ├── OcrService.kt            # Gemini vision sign reader
│   ├── SignInfo.kt              # parsed fields
│   └── SignInfoParser.kt        # regex fallback (not used by main path)
├── location/
│   └── LocationService.kt       # FusedLocationProvider wrapper
├── search/
│   ├── GeocodingService.kt      # Google Maps reverse geocoder
│   ├── ListingSearchService.kt  # Gemini Search grounding
│   ├── ListingCandidate.kt      # candidate + address models
│   └── [types]
├── matching/
│   ├── ImageMatchService.kt     # og:image scraper + Gemini ranker
│   └── MatchResult.kt
├── pipeline/
│   ├── SnapPipeline.kt          # end-to-end orchestration
│   ├── PipelineViewModel.kt     # UI state bridge
│   ├── PipelineState.kt         # sealed state + DebugInfo
│   └── DebugReporter.kt         # optional POST to a dev server
└── ui/
    ├── camera/CameraScreen.kt   # CameraX preview + gallery picker
    ├── processing/ProcessingScreen.kt
    └── result/ResultScreen.kt   # match cards + debug card
```

## Status

Working proof of concept — builds cleanly, captures photos, runs the full pipeline. Expect rough edges, particularly:

- Gemini can hallucinate listing URLs; the app HEAD-validates them but some fast-listings still slip through
- Image scraping is regex-based and brittle against captcha/JS-rendered listing pages
- No offline mode — every snap needs Gemini + Maps API round-trips
- Debug reporting is best-effort and disabled by default (see `DebugReporter.kt`)

## License

Personal project. No license granted.
