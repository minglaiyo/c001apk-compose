# c001apk Compose — native skeleton

A runnable-skeleton Kotlin + Jetpack Compose Android app: the starting
point for a native rewrite of the c001apk Coolapk client (currently
Flutter/Dart). Local work only: this directory is not connected to any
GitHub repo, and it contains no keystore, password, token, or secret.

## What it is

One module, MVVM, Material3:

- Feed list (hot ranking tab) with pull-to-refresh, endless load-more,
  Coil images, and tap-to-detail navigation.
- Detail screen with author header, title, body, images, like/comment
  counts, and the reply list with paging (api2 replyList).
- `applicationId = app.awavenue.c001apk.compose`, deliberately distinct
  from the Flutter build's `app.awavenue.c001apk`, so both apps can
  coexist on one device during the transition.

## Verified endpoints (ported from the Flutter code)

Base URL: `https://api.coolapk.com` (the Flutter `URL_API_SERVICE`).
All paths and parameter names below were read from
`apps/flutter/lib/logic/network/api.dart` and `network_repo.dart` on
the fork's `cloud-debug-build` branch — none were invented.

| Call | Parameters |
| --- | --- |
| `GET /v6/page/dataList` | `url`, `title`, `subTitle`, `page`, `firstItem?`, `lastItem?` |
| `GET /v6/feed/detail` | `id` |
| `GET /v6/product/detail` | `id` |
| `GET https://api2.coolapk.com/v6/feed/replyList` | `id`, `listType=lastupdate_desc`, `page`, `lastItem?`, `discussMode=1`, `feedType=feed`, `blockStatus=0`, `fromFeedAuthor=0` |
| `GET /v6/search` | `type=hotSearch`, `refresh=1`, `returnType=all` |
| `GET /v6/search/suggestSearchWordsNew` | `searchValue` |

The default feed tab is the Flutter home HOT tab, passed verbatim as
the `dataList` url parameter: `/page?url=V9_HOME_TAB_RANKING`.
Pagination follows the Flutter controllers: pages start at 1, and
load-more passes the previous page's last item id as `lastItem`.

Client headers mirror the Flutter `ApiInterceptor` (User-Agent pattern,
`X-App-Id: com.coolapk.market`, version 16.2.0 / code 2604201, api
version 16, locale `zh-CN`, channel `coolapk`, mode `universal`).

## What is implemented vs stubbed

Implemented:

- Retrofit + OkHttp client with the header set above, kotlinx-serialization
  models mapped from the real JSON keys (including the `hot_num_txt`
  key the Flutter `Datum` maps via `@JsonKey`).
- `FeedRepository` with `StateFlow` refresh + load-more + end detection.
- Feed list UI (avatar, author, headline, first image, like/comment
  counts) and detail navigation.

Stubbed / deliberately left out:

- **Request signing for anonymous browsing.** Every request carries the
  signed `X-App-Token` v3 header, ported from the Flutter app's
  `TokenUtils.getTokenV3` (same algorithm, same public protocol lookup
  data, plus bcrypt), with a per-process device code in the Flutter
  format and an in-memory cookie jar for the anonymous session cookie.
  No account is involved: no login uid/token is sent. Login itself
  (account session, posting, likes) is still not built.
- Detail rendering is now full (author, images, counts, replies);
  reply posting, reply sorting, and nested reply threads are not
  built yet. Anonymous detail requests are captcha-gated by the
  server (HTTP 200, message "当前访问需要验证码", data null): the
  detail screen shows the server's message when content comes
  back empty, with a 去验证 button that opens the Netease widget
  in a WebView; on success the proof replays the request via the
  validate= cookie and is kept 24 hours.
- Search, product detail screens, login, settings, and the Flutter
  fork's custom features (scroll-hide bars, back-to-top button,
  clipboard detection, and so on) are not built yet.
- List and detail images upgrade the server's http Coolapk URLs to
  https (Android blocks cleartext) and load through the app's
  OkHttp client, because the image CDN rejects non-app
  User-Agents with a 567. The Flutter fork's `.m.jpg`
  thumbnail rewrite is still a later port.
- Only `entityType == "feed"` items are rendered; other list card
  types (carousels, icon grids, title cards) are skipped for now.

## Build

Requirements: JDK 17 and an Android SDK with platform 34
(`ANDROID_HOME` set, or `local.properties` with `sdk.dir`).

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Cloud build: `.github/workflows/android.yml` checks out, sets up
JDK 17, runs `./gradlew assembleDebug`, and uploads the debug APK
for 7 days. No signing, no secrets.

## Flutter → Compose module map

| Flutter (apps/flutter/lib) | This project |
| --- | --- |
| `logic/network/api.dart` | `data/api/CoolapkApi.kt` |
| `logic/network/interceptor.dart` (headers) | `data/api/CoolapkHeaders.kt` |
| `logic/network/request.dart` (Dio client) | `data/api/ApiClient.kt` |
| `logic/model/feed/datum.dart`, `data_list_model.dart` | `data/model/FeedModels.kt` |
| `logic/network/network_repo.dart` (list loading) | `data/repository/FeedRepository.kt` |
| `pages/home/feed/home_feed_controller.dart` | `ui/feed/FeedViewModel.kt` |
| `pages/home/feed/home_feed_page.dart`, `common_body.dart` | `ui/feed/FeedListScreen.kt` |
| `pages/feed/feed_page.dart` (detail) | `ui/detail/FeedDetailScreen.kt` |
| `router/` | `ui/AppNavHost.kt` |
| `main.dart` + `DynamicColorBuilder` theme | `MainActivity.kt` + `ui/theme/Theme.kt` |

## Status note

This skeleton was assembled without an Android SDK in the authoring
environment, so it has NOT been compiled here. The first real check is
the cloud workflow above (or a local `./gradlew assembleDebug` with
an SDK installed). Version choices are a known-compatible set
(AGP 8.7.3 + Kotlin 2.0.21 + Compose compiler 2.0.21 + Compose BOM
2024.11.00 on JDK 17), but a first build may still surface a version
or import issue that only the compiler can catch.
