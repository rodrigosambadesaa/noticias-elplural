# Noticias ElPlural
Independent Android RSS reader for ElPlural.

RSS: https://www.elplural.com/uploads/feeds/feed_elplural_es.xml

Includes search, refresh, article view, sharing, offline cache, passive NetworkObserver and full connectivity diagnostics. Remote RSS/image operations use a cheap isConnected() guard, execute the real request directly, and only run the active Gist diagnosis after an ambiguous connectivity failure. Valid HTTP responses never trigger a redundant general probe. The complete requested gist is vendored under third_party/connectivity.

This is not an official ElPlural application.

## Remote request policy

Every RSS load, refresh, retry, pagination request and remote image load first checks
the current usable network with ConnectivityAndInternetAccess.isConnected(). If the
guard fails, the app skips the request and keeps the cached/offline UI.
The guard also requires an actual usable transport (Wi-Fi, mobile, Ethernet, VPN or
Bluetooth); a stale capability without an active transport is treated as offline and
cannot start the RSS request or its refresh progress indicator.
The network badge follows the passive NetworkState and changes its color to warning
or offline instead of remaining green; offline fallback does not emit repeated toasts.

When the guard passes, the real RSS or HTTP request runs directly with its own
timeouts, redirects, HTTP status handling and exception handling. A valid HTTP
response is never followed by a redundant general connectivity probe. Only ambiguous
network failures such as DNS, connect, timeout or TLS errors trigger the Gist's
post-failure general diagnosis, which distinguishes a feed-specific outage from a
general connectivity problem.

When Android recreates the main screen after an orientation change, the app restores
the cached news list and the RecyclerView layout state, including the current scroll
position, without starting another RSS request.

Validation:

    ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
