# LanBoost for Android

An independent Android implementation of the console/CDN selection workflow.
Version 0.5.0 is a test build, not an official XboxDownload port or a console manufacturer's app.
The scope is CDN testing and LAN DNS. There is no game download manager, store browser or local package relay.

## Included

- Nine independent profiles: Xbox CN1/CN2/COM/applications, Nintendo Switch, PlayStation, EA, Battle.net international, and other Akamai resources. Import Xbox, PS or Akamai IPv4 lists from the upstream project's public URLs, or local text / pasted IP lists.
- Catalog requests follow the system active network and proxy/VPN configuration. Try GitHub Raw, then GitHub Contents API with raw-text responses; permit one extra transient-error retry. Each request is capped at 10 seconds and 1 MiB, and oversized catalogs are rejected instead of silently truncated.
- Persist the last complete catalog per profile, its source/update time, and the latest error. A refresh failure retains this cache. Only without a complete cache, try the current download hostname's IPv4 answers via Wi-Fi and the configured upstream DNS; these are explicitly labeled temporary DNS candidates. Refresh merges by IP and retains measurements, imported/DNS candidates, and selected nodes.
- Combine carrier, province/region, city and whitespace-separated keyword filters. Choices come from catalog labels; multi-carrier labels match each carrier, and unrecognized metadata remains unlabelled. No geolocation lookup or device-location access is used. Criteria persist per profile and apply consistently to the list, tests, continuation and best-node selection. Probe TCP reachability with six workers, then measure real HTTP/HTTPS throughput sequentially by latency.
- Preserve the original hostname, Host header and TLS validation while pinning the socket to each candidate IP. Redirects and HTML/JSON error responses are not treated as successful download samples.
- Choose the best measured candidate or manually select a separate address for each group. Save results and settings locally. Browse all candidates in pages of 60; show total, filtered and measured counts separately. Continue the next throughput batch while skipping nodes already attempted with the current URL, including failed attempts.
- Run a LAN DNS server over UDP and TCP, with exact/domain-suffix mappings and upstream forwarding. Wildcards respect label boundaries. SRV additional address records are rewritten where applicable.
- NS/EA/Battle.net profiles offer the original alternate-CDN block rules, enabled only when that profile has a selected IP. Disable blocking per profile in its DNS rule details if a download fails.
- Use a backup upstream on timeout, invalid response, SERVFAIL or REFUSED; valid negative answers are preserved.
- Three redesigned pages: node selection, DNS service and network activity. See per-client DNS requests, rule hits and connection diagnostics.
- HTTP 307 redirection from known Xbox international download domains to the corresponding Chinese domain, preserving the raw request path and query string. Enabled by default once CN1 is selected; a saved manual opt-out is preserved.
- A foreground notification, wake lock, cancellation, query logs, network-change shutdown, scoped Root NAT rules and cleanup.

## Requirements

- Android 8.0 or newer. Android 16 is the initial physical-device test target.
- Root for publishing standard DNS port 53 and optional HTTP port 80. `su -c` and `iptables -t nat` must work; KernelSU, Magisk and APatch use the same interface, but not all device combinations have been tested.
- Phone and console/PC on the same non-isolated LAN. Custom router firmware is not required.
- Root is not required for candidate import and download tests.

## Use

1. Install the APK and allow notifications. In KernelSU, enable superuser access for **LanBoost**.
   Allow background activity in the system battery settings and lock the app in Recents if supported. Some ROMs can freeze the service despite its foreground notification. An Android 16 test with Recents locking passed four LAN checks during about four minutes of screen-off operation while USB-powered. Long-running, unplugged and other-device behavior remain unverified.
2. Connect the phone to home Wi-Fi. Open the speed tab, choose a group, fetch or import candidates, and filter for your provider/region where useful.
3. Start a test. Choose the default batch (up to 12 reachable candidates, configurable from 1–50) or all filtered candidates. The confirmation shows scope, count and maximum test payload. Default per-node limits are 5 seconds and 32 MiB; edit these in Settings. All mode bypasses only the batch count, preserving per-node limits and cancellation. Continue skips prior attempts with the current URL and offers batch or all remaining candidates. Unreachable nodes are skipped; batch results are not an exhaustive ranking.
4. Select a result or use "adopt fastest". All nine groups save independent selections. NS, PS and EA each use their own default test URL, not an Xbox file. Applications, Battle.net and generic Akamai default to TCP port 80 latency only; set a valid matching download URL for throughput testing. All URLs are editable if a file expires or is not cached. Low latency is not proof of faster downloads.
5. Open the service tab. Domestic HTTP redirection is enabled by default and takes effect after selecting CN1. Without CN1, DNS and other platform mappings still work. Old-game redirection also requires CN2. Existing explicit settings are retained; switch it on in Settings if upgrading with a saved opt-out.
6. Check Root, start the service, and enter the phone IP as the console's primary DNS (or the PC network adapter's DNS). Leave secondary DNS blank, or use the same phone IP if required. Do not change the entire router's DNS for the first test.
7. Pause/resume downloads after a rule change. Restore device DNS to automatic before shutting down or taking the phone away.

The phone handles DNS and, optionally, the initial HTTP redirect. It does not relay the game data. Actual Xbox throughput may differ from phone measurements due to Wi-Fi, cache availability, ISP routing and console limits.

## Networking details

- App listeners: phone Wi-Fi IPv4:1053 (UDP/TCP), optional :1080 (HTTP).
- Root rules map incoming Wi-Fi traffic addressed specifically to the phone from its own IPv4 subnet: 53 -> 1053, optional 80 -> 1080.
- Only the dedicated `LANBOOST_IN` NAT chain and its PREROUTING jump are created. No OUTPUT, FORWARD, default policy or system DNS is changed.
- The upstream is a configurable IPv4 DNS resolver, default `223.5.5.5:53`, with `119.29.29.29:53` as backup. Leave backup empty to disable failover. The app does not intercept its own outbound DNS, so ordinary port 53 upstreams are supported.
- Benchmark and DNS/HTTP service socket traffic is bound to the Wi-Fi network; catalog downloads use the system active network. Device-wide Root proxy modules may still affect routing and should be considered when interpreting measurements.
- An external Root watcher attempts cleanup within about two seconds after process death. Some ROMs may kill that watcher too. The "clean port rules" action is also available; restarting the device removes nonpersistent rules.
- Wi-Fi disconnect/address changes stop the server. There is no automatic boot start.
- The IPv4 switch suppresses AAAA/HTTPS/SVCB responses only for overridden names. It is not a blanket IPv6 firewall. If the console still chooses an IPv6 path outside these rules, compare with IPv6 temporarily disabled for the console/network.
- DNS/HTTP access is limited to the local subnet. No router WAN port forwarding is required.

## Boundaries

This version provides candidate measurement, selection and LAN DNS serving. It does not implement Xbox disk conversion, local package uploads, Microsoft Store metadata, transparent HTTPS interception, a VPN tunnel, account sign-in or Battle.net HTTP redirects. It is not an online-game latency proxy. Custom exact DNS records are available.

Rule details in the app list each group's domains, including NS content/update suffixes and application `*.tlu.dl.delivery.mp.microsoft.com` hosts. More specific platform groups take precedence over the generic Akamai group, even when unselected. Custom exact records have highest priority. Leaving a group unselected preserves upstream resolution for that group. Stop DNS before changing rules, then restart it to apply changes.

The generic Akamai profile includes the original Epic/Ubisoft/Riot/Steam-static-resource patterns and broad Akamai suffixes. It may also affect matching non-game resources. Its Steam entries are static store/video assets, not a promise to accelerate Steam game depots. This profile is inactive until explicitly assigned an IP.

HTTP redirection maps supported international names to the phone; HTTPS requests to those names can fail because the phone does not serve port 443. Keep redirection off for HTTPS downloads and use the international CDN group instead. DNS success alone cannot guarantee faster downloads, and a working generic test file does not prove every game is cached on that CDN.

## Build and test

Run the following commands from the repository root, which can also be opened directly in Android Studio. Install JDK 17 and Android SDK platform 35. Set `JAVA_HOME` and `ANDROID_HOME` (or create your own `local.properties` with `sdk.dir`).

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses the local Android debug key. The fixed-signature release build preserves the original test certificate for upgrade compatibility and disables debugging. It reads credentials from a private properties file or environment variables, and refuses missing or mismatched credentials. GitHub Actions tests PRs without signing secrets, builds a signed release APK after default-branch pushes, and publishes a GitHub Release with the APK, source archive and checksums when a `v<versionName>` tag is pushed. See [signing and Actions](SIGNING.md). No signing secrets are included in the source archive.

For on-device protocol checks (no Root required):

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.lanboost.android.test/dev.lanboost.android.DeviceChecks
adb uninstall dev.lanboost.android.test
```

The instrumentation checks DNS UDP/TCP, platform isolation, block rules, HTTP redirect parsing and pinned HTTP downloads using local fixtures. Optional `-e lan_hold_ms 20000` temporarily listens on Wi-Fi ports 1053 and 1080 for LAN checks, then closes them. Optional `-e platform_probe true` resolves and samples NS/PS/EA default URLs over Wi-Fi, at most 256 KiB each. Fixture IPs in `192.0.2.0/24` are documentation addresses, not actual CDN recommendations. The default checks do not grant Root or change app preferences/firewall rules. An explicit emulator-only `-e filtered_service true` check temporarily seeds synthetic nodes/settings, runs 15 bounded local HTTP downloads through the real service, verifies combined filters and continuation, then restores the prior preferences. Do not use the fixture mode on a physical phone.

## Dependencies and sources

Full shipped dependency license notices are bundled in `app/src/main/assets/THIRD_PARTY_NOTICES.txt` and in the APK assets.

- dnsjava 3.6.3, BSD-3-Clause: DNS wire parsing and record encoding.
- OkHttp 4.12.0 / Okio, Apache-2.0: HTTP clients and pinned-IP throughput tests.
- Apache HttpCore 4.4.16, Apache-2.0: bounded HTTP header parsing.
- SLF4J 2.0.16, MIT: logging facade used by dnsjava.
- JUnit 4.13.2, EPL-1.0 and MockWebServer 4.12.0, Apache-2.0: development tests.
- Gradle wrapper 8.11.1, Apache-2.0.

Reference behavior, endpoint addresses and optional remotely fetched IP lists:
https://github.com/skydevil88/XboxDownload

Domain patterns and sample URLs were checked against commit `9acd0cde323636dd54bd0bfcef2297bed306a314`: `Services/DnsMappingGenerator.cs`, `Resources/Akamai.txt`, `Models/SpeedTest/SpeedTestDataBuilder.cs` and the `IP/` directory.

LanBoost uses its own Android implementation and icon; the reference sources above describe the domain patterns, sample URLs and candidate list locations. IP lists are fetched at the user's request and are not bundled in the APK. This implementation does not claim upstream endorsement or full feature parity.

LanBoost's original implementation is licensed under [GNU GPL version 3 only (GPL-3.0-only)](../LICENSE). Copyright and scope are documented in [NOTICE.md](../NOTICE.md). Third-party libraries and the Gradle wrapper retain their respective licenses. This grant does not establish permission for XboxDownload's referenced rules or datasets. APKs include LICENSE, NOTICE.md and dependency notices; open Help → Open-source licenses in the app to read them. See the [privacy notice](PRIVACY.md) and [publishing guide](PUBLISHING.md).
