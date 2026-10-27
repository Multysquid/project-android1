# Architecture

Developer notes for Demento, the shopping list app (code name and package: Einkaufsliste,
`de.jw.einkaufsliste`). The product plan (German) is in
`/mnt/project-files/plan/shopping-list-app-plan.md`.

## Builds

| Build | Path | Needs | Command |
|---|---|---|---|
| `core` | `core/` (own Gradle build, included via `includeBuild`) | JDK 17 or 21, Maven Central | `./gradlew -p core test` |
| `app` | `app/` | Android SDK 35 (`ANDROID_HOME` or `local.properties`) + Google Maven (`dl.google.com`) | `./gradlew :app:assembleDebug` |

The wrapper is Gradle 8.14, which does not run on JDK 25 (Gradle 9.1+ does).

CI (`.github/workflows/apk.yml`, GitHub Actions) runs the `core` tests and
`:app:assembleRelease` on every branch push and attaches `demento.apk` to the run as the
artifact `demento-apk`. Pushes to `main` (and to `einkaufsliste-app` until it is
merged) also replace the public release `apk`, so
`releases/latest/download/demento.apk` is always the newest build (the same file is also
published as `einkaufsliste.apk`, the app's first download link). A failed build
posts the Gradle errors as a separate check run named "Build-Fehler". `versionCode` is the
Actions run number (1 for local builds).

There is no release key yet: release builds are signed with the debug key of the machine
that builds them. On CI that key is new for every run, so an update may need an uninstall
(the user keeps the list with "Liste sichern").

`core` is pure Kotlin/JVM: everything that can be tested without a phone lives
there. `app` depends on it as `de.jw.einkaufsliste:core` (composite build).

## Stack

Kotlin 2.1, Jetpack Compose (BOM 2024.12.01, Material 3), Navigation Compose
(string routes), Room 2.6 (KSP), DataStore, WorkManager, CameraX + ML Kit barcode
(bundled model, offline), Play Services Location (fused location, geofencing),
OkHttp, kotlinx.serialization (JSON parsing in `core`). minSdk 29, target 35.

No DI framework: `AppContainer` holds everything; get it with
`context.appContainer`. Screens take the container as a parameter.

## Conventions

- All user-facing text is German and written inline in Kotlin (the app is German
  only; `strings.xml` holds just `app_name`).
- Code identifiers and comments are English.
- ViewModels: `viewModel { FooViewModel(container) }`; state via `StateFlow` and
  `collectAsStateWithLifecycle()`.
- Network calls go through `OkHttpClient.fetch()` (`data/net/Http.kt`), which sets
  the User-Agent OSM and Open Food Facts ask for.
- Number formatting that ends up in URLs or queries must use `Locale.ROOT`.

## Packages and ownership

| Package | Content |
|---|---|
| `core/.../core/model` | Domain data classes shared by everything |
| `core/.../core/catalog` | German default categories, shop-tag labels, `CategoryGuesser` |
| `core/.../core/match` | `ShopMatcher` |
| `core/.../core/notify` | Quiet hours and per-shop cooldown |
| `core/.../core/geofence` | Which shops get a geofence |
| `core/.../core/osm` | Overpass query/parser, `opening_hours` evaluation |
| `core/.../core/products` | Open*Facts URL, parser, category mapping, barcode check digits |
| `core/.../core/gemini` | Gemini request body, response parsing, German error messages |
| `app/.../data` | Room, DataStore settings, API key store, HTTP helper, repositories, `MatchService` |
| `app/.../ui` | Theme, navigation, notification channels, shared `CategoryPicker` |
| `app/.../feature/list` + `categories` | List screen with Unterlisten, add/edit, category editor |
| `app/.../feature/scan`, `photo`, `products`, `gemini`, `camera` | Barcode scanner, photo capture, lookups; `camera` holds camera permission handling, CameraX preview binding and the add-item bottom sheet |
| `app/.../feature/shops` | Overpass download + cache (`ShopRepository`), "In der Nähe" screen |
| `app/.../feature/background` | Location, WorkManager, foreground service, geofences, notifications, purchase learning |
| `app/.../feature/settings` | Settings screen, permissions flow, Samsung battery setup |

## App icon

The launcher icon (brain with a lightning bolt) is an adaptive icon made of vector
drawables. Its masters are the SVGs in `docs/icon/` (foreground, monochrome for themed
icons, the 24 dp notification icon; background colour in `background.txt`). After changing
one, regenerate the drawable, e.g.
`python3 docs/icon/svg2vd.py docs/icon/foreground.svg app/src/main/res/drawable/ic_launcher_foreground.xml 108`
(monochrome: `ic_launcher_monochrome.xml`, 108; notification: `ic_notification.xml`, 24) and
copy the background colour into `ic_launcher_background` in `res/values/colors.xml`.
The converter accepts only the SVG subset it documents and fails on anything else.

## Data model

See `app/.../data/db/Entities.kt`. Categories have a stable `slug` (defaults from
`CategoryCatalog`, `custom_<uuid>` for user ones) and comma-separated `osmTags`.
Items reference a category (`SET NULL` on delete; the repository moves items to
"Sonstiges" first). Shops are cached OSM objects with id `node/123`.

The default categories are inserted by `CategoryRepository.ensureSeeded()` (started in
`EinkaufApp.onCreate`, once per process behind a mutex). Every category read of the
repository (`observeAll`, `getAll`, `idForSlug`) waits for it, so on a fresh install no
screen, photo or barcode lookup sees an empty table and saves an item without Unterliste.
"Standard-Unterlisten wiederherstellen" (menu of the category editor,
`CategoryRepository.restoreMissingDefaults()`) brings back deleted defaults by slug, after
the existing ones; a hand-made category with the same name takes over the default slug
instead of getting a twin.

Backup ("Liste sichern" in the settings): JSON `{"format":"einkaufsliste","version":1,...}` with categories and items only (`core/.../core/backup`); import merges by slug and skips items already on the list.
This is the only backup: Android Auto Backup and device-to-device transfer are off
(`allowBackup="false"`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`),
because the database holds the purchase history and the settings hold the location of the
last shop download.

## Navigation

String routes in `ui/navigation/AppNavHost.kt`. The settings take an optional section
(`settings?section={section}`); `Routes.settings(Routes.SECTION_GEMINI)` opens them at the
Gemini key (used by the photo screen). Settings and "In der Nähe" link to each other;
`openOrReturn` goes back to the other one when it is already on the back stack.

## Matching ("a shop nearby has the product")

Shops do not publish stock, so matching is by category:

1. A shop within the radius matches an item when the shop's tag (e.g.
   `shop=supermarket`) is in the item's category `osmTags`.
2. It also matches when the item (normalized name) was bought at that shop before
   (purchase history, recorded when an item is ticked off within ~75 m of a shop).
3. An item pinned to a shop matches only that shop. The picker lists the cached shops;
   when there are none it offers "Läden laden" (asks for the location permission, then
   `ShopRepository.ensureShopsAround`).
4. Shops are ranked by number of matched items (learned matches weigh extra), then
   distance. Duplicate OSM objects for the same shop are merged.

## Background check

Settings: interval (1, 2, 5, 10, 15, 30, 60 min; default 15), radius (default
500 m), quiet hours, opening hours, per-shop cooldown.

- Interval >= 15 min: WorkManager unique periodic work.
- Interval < 15 min: foreground service of type `location` with a permanent
  notification, driven by fused location updates at that interval; WorkManager
  15 min stays scheduled as a backup in case the service gets killed.
- In both modes: geofences (radius = setting) around the nearest matching shops
  (max 90), refreshed on each check, re-registered after boot.
- Each check: location -> refresh shop cache if needed (Overpass, download
  radius max(3 km, 4x radius) up to 10 km, refresh after 7 days or when leaving
  the downloaded area) -> match -> filter closed shops / quiet hours / cooldown ->
  notify (max 3 shops per check) -> update geofences.
- The foreground service is started only when background location is granted
  or the app is in the foreground; otherwise only the 15-minute WorkManager
  backup runs (Android 14 refuses a location foreground service started from
  the background without "Immer zulassen"). Failures are caught.
- Geofences are re-registered only when the set of shops or the radius changed,
  or after 3 h, and checks started by a geofence do not re-register. Each
  registration fires ENTER for shops the user is already inside
  (`INITIAL_TRIGGER_ENTER`), so this prevents a loop.
- After boot or an app update, one check is enqueued with a 1-minute delay
  (geofences are lost on reboot).
- A purchase is recorded only with a location fix of 100 m accuracy or better.

## External services

| Service | Used for | Data sent |
|---|---|---|
| Overpass API (OpenStreetMap): `overpass-api.de`, fallback `overpass.private.coffee` | Shop locations | Approximate area (center rounded to 3 decimals, about 100 m, + radius) |
| Open Food Facts and sister DBs (Open Products/Beauty/Pet Food Facts) | Barcode -> product name | The barcode |
| Gemini API (user's own key) | Photo -> name + category | The photo and the names of all categories, including the user's own (`GeminiApi.prompt`) |
| ML Kit barcode scanning (bundled model) | Reading barcodes on the device | No images; per Google's ML Kit disclosure, usage and performance metrics (device model, app version, error codes) |

Attribution shown in the app: "Kartendaten © OpenStreetMap-Mitwirkende (ODbL)",
"Produktdaten: Open Food Facts und Schwesterprojekte (ODbL)". The README's table of data
leaving the phone must match this one.
