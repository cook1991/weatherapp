# SkyCast — Play Store Submission Checklist

Everything needed to publish SkyCast (`com.spritzlive.weather`). Work top to bottom.

## 0. Before you start
- [ ] **Google Play Developer account** ($25 one-time) at https://play.google.com/console
- [ ] **Back up signing files** (do this now, not later):
      `upload-keystore.jks` + `keystore.properties` (repo root, git-ignored) → password
      manager / offsite. Losing them before Play App Signing is enabled = can't update the app.
- [ ] **Host the privacy policy.** Put `PRIVACY.md` (rendered to a web page) at a public
      URL you control. Copy that URL — the Console requires it.

## 1. Create the app
- [ ] Play Console → **Create app**
- [ ] App name: **SkyCast** · Default language: English (US)
- [ ] Type: **App** · **Free**
- [ ] Accept the declarations

## 2. Upload the release
- [ ] **Release** → **Production** (or **Testing → Internal testing** first — recommended)
- [ ] Create release; keep **Play App Signing ENABLED** (default)
- [ ] Upload **`app/build/outputs/bundle/release/app-release.aab`**
      (versionCode 1 / versionName 1.0)
- [ ] Release name auto-fills to `1 (1.0)`; add a one-line "First release" note

## 3. Store listing (copy from LISTING.md)
- [ ] **App name:** `SkyCast: Weather Forecast`
- [ ] **Short description:** `Fast, private weather with a 7-day forecast. No ads, no tracking, no account.`
- [ ] **Full description:** paste the block from `LISTING.md`
- [ ] **App icon:** `store-icon-512.png` (512×512)
- [ ] **Feature graphic:** `feature-graphic.png` (1024×500)
- [ ] **Phone screenshots:** `01-current.png`, `02-search.png`, `03-daily.png`
      (need at least 2 — you have 3)

## 4. Store settings
- [ ] **App category:** Weather
- [ ] **Contact email:** jason@spritzlive.net
- [ ] **Privacy policy URL:** (the one you hosted in step 0)

## 5. Content rating
- [ ] Complete the questionnaire → expect **Everyone**. No violence, no user content,
      no ads, no purchases.

## 6. Data safety (be accurate — this app touches location)
- [ ] Does the app collect/share user data? → **Yes**
- [ ] Data type: **Location → Approximate location**
      - Collected: **Yes**
      - Shared: **Yes** (coordinates/city go to Open-Meteo for the forecast and to
        the device geocoder / BigDataCloud to name the place; the radar map viewport
        — centered on your location — goes to RainViewer and MapTiler)
      - Purpose: **App functionality**
      - Processed ephemerally / not stored on a server: **Yes** (we run no server)
      - Required or optional: **Optional** (city search works without it)
- [ ] Not collected: personal info, identifiers, financial, contacts, messages
- [ ] Data is **not** used for advertising or tracking

## 7. Other required declarations
- [ ] **Target audience:** 13+ (or your choice; app is fine for all ages)
- [ ] **Ads:** contains ads? → **No**
- [ ] **Government app:** No
- [ ] **News app:** No
- [ ] **COVID/health:** No
- [ ] **Data safety, content rating, privacy policy** all show green

## 8. Roll out
- [ ] Internal testing first (add your own Google account as a tester) → verify install
- [ ] Then **Production** → **Send for review**
- [ ] Review is typically hours-to-days for a first submission

## Reference — key facts
| Field | Value |
|---|---|
| Package (applicationId) | `com.spritzlive.weather` (permanent once published) |
| Version | code 1 / name 1.0 |
| Min Android | 8.0 (API 26) |
| Permissions | INTERNET, ACCESS_NETWORK_STATE, ACCESS_COARSE_LOCATION |
| Data source | Open-Meteo (weather), device geocoder / BigDataCloud (place names), RainViewer (radar), MapTiler (base map, data © OpenStreetMap) |
| Upload key SHA-256 | FE:15:A5:F4:79:45:69:61:24:B2:77:61:A3:FD:CF:BC:9A:F8:DB:65:2D:36:6F:FD:3F:EA:C2:1D:0E:BC:8D:24 |

## To ship an update later
1. Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts`
2. `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
3. `./gradlew.bat :app:bundleRelease`
4. Upload the new `app-release.aab` to a new release
