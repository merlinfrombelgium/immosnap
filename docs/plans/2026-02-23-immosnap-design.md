# ImmoSnap — Design Document

## Overview

Android app that lets users photograph a house with a for-sale sign, extracts real estate info via OCR, identifies the listing online, and opens it in the browser.

**Market:** Belgium (Immoweb, Zimmo, ImmoVlan)

## Architecture

```
Camera → ML Kit OCR (on-device) ─┐
                                  ├→ Matching Engine → Result → Open in Browser
GPS → Reverse Geocode (Maps API) ─┘
```

**Stack:** Kotlin, Jetpack Compose, CameraX, ML Kit Text Recognition, Google Maps Geocoding API, Google Custom Search API, Gemini Flash API

## Core Flow

### Screen 1 — Camera
- Full-screen CameraX viewfinder
- Single "Snap" button
- GPS acquired silently in background on launch

### Screen 2 — Processing
- Shows captured image with progress indicators
- Pipeline: OCR → geocode → listing search → Gemini Flash image match

### Screen 3 — Result
- **Match found:** Listing thumbnail, address, agency, price. "Open Listing" button → browser
- **Multiple candidates:** Top 3 ranked by Gemini confidence, user taps correct one
- **No match:** Retry option

## Matching Strategy (Hybrid)

1. **OCR** extracts agency name, phone number, reference number from sign
2. **GPS** reverse geocodes to street address and postal code
3. **Reference number lookup** (if found) — direct search on Immoweb
4. **Location + agency search** — Google Custom Search API across `immoweb.be`, `zimmo.be`, `immovlan.be`
5. **Image matching** — Gemini Flash compares user's photo against listing exterior photos to confirm/rank the correct match

## Listing Search

Google Custom Search API querying `site:immoweb.be OR site:zimmo.be OR site:immovlan.be` with extracted agency name, street, and postal code. Returns listing URLs directly.

## Data & Privacy

- No user accounts
- No backend/database — stateless, direct API calls from device
- Photo sent to Gemini Flash for matching, not stored
- GPS used only for geocoding, not tracked

## API Dependencies

| API | Purpose | Cost |
|-----|---------|------|
| Google Maps Geocoding | Reverse geocode GPS → address | Free tier: 28k/month |
| Google Custom Search | Find listings | $5/1000 queries (free: 100/day) |
| Gemini Flash | Image matching | Generous free tier |

All keys stored in `local.properties` (not in version control).

## Technology Summary

| Component | Technology | Runs |
|-----------|-----------|------|
| Camera | CameraX | On-device |
| OCR | ML Kit Text Recognition | On-device |
| Location | FusedLocationProvider | On-device |
| Geocoding | Google Maps API | Cloud |
| Listing search | Google Custom Search API | Cloud |
| Image matching | Gemini Flash | Cloud |
| UI | Jetpack Compose (3 screens) | On-device |
| Backend | None | N/A |
