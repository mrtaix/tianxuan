# CTHarness verification evidence

## 2026-11-26: resource and identity integration

Base commit: `91dc5d91ecf3e21f9846e8688cf2806b6d5ba7d1`.
Branch: `feat/ctharness-vi`.

- PASS: `python3 tools/check-ctharness.py` — 7 tests, exit 0.
  Checks release/dev/debug identity, CTHarness labels, preserved JNI namespace,
  vi/en/zh-CN locale configuration, picker, startup call order, fork update
  source, pre-installer package guard and complete XML key/printf/flag parity.
- PASS: AAPT2 compile of every product values XML file — 46 files:
  17 values, 13 values-en, 16 values-vi; 0 failures. Each source file was
  compiled into a separate scratch directory, avoiding output collisions.
- PASS: all 14 original values-vi XML files parse. They contain 1,762 source
  strings, including 521 legacy settings strings. Main added another 8 keys
  for the language picker and package/update errors: total 1,770.
- PASS: `git diff --check` at resource integration.
- PASS: Gradle wrapper 9.7.0 starts and prints `--version` when
  GRADLE_USER_HOME is set to a directory in HARNESS_SCRATCH.
- BLOCKED: `architectureCheck :app:testDebugUnitTest --tests
  top.wkbin.tianxuan.AppLanguageTest` — exit 1 before task execution.
  Gradle daemon criteria require Java 25, but this environment has Java
  21.0.13.1. Error: `Cannot find a Java installation ... languageVersion=25`.
  No project toolchain requirement was downgraded.
- NOT RUN: full lint, Android unit tests, APK assembly/signature inspection,
  installation, API 29–32/33+ locale persistence, PTY, Shizuku and OAuth
  device smoke tests. AAPT2 compilation is not an APK build or runtime test.

## 2026-11-27: customization-flow continuation

- Scope: CTH-VI-002. Added 17 localized keys: 15 screen labels, one chat title and one generated user-visible prompt. Total Vietnamese inventory is now 1,787 keys in 16 XML files.
- RED EXPECTED: two new localization tests failed before the changes; see iteration-red.log. A subsequent ratchet test exposed navigation growth from 820 to 821 lines. The interrupted ratchet run is not counted as a completed suite.
- PASS: `python3 tools/check-ctharness.py`, executed using the tests tool after the ratchet fix — exit 0, `Ran 10 tests in 3.763s`, `OK`. The tool did not parse individual unittest cases, but returned the actual successful unittest summary.
- PASS: AAPT2 compilation of all 41 product strings.xml / legacy_strings.xml files — exit 0, zero failures. This is a subset of all values XML files, not the 46-file whole-values compilation from the earlier session. See iteration-aapt2.log.
- PASS: navigation source is back to 820 lines, matching the unchanged architecture baseline; three confirmed-unused imports were removed to accommodate localized-resource imports. The new contract test checks this ratchet.
- PASS: openjdk-25 installed through the approved package tool; `java -version` ran and reported 25.0.5.1. The earlier JDK-25-absent blocker is superseded.
- Native compile attempt 1 FAILED before task execution: Java defaults pointed the wrapper cache at inaccessible `/data/data/com.termux/files/home/.gradle`; see iteration-gradle.log.
- Native compile attempt 2 FAILED before task execution: Java temporary-directory defaults were inaccessible; see iteration-gradle-retry.log.
- Native compile attempt 3 (job 20) was superseded by later successful runs; see the compile results below. No SDK license was accepted automatically and no project toolchain/SDK requirement was lowered.
- Independent bounded review completed after an interrupted first attempt. No Critical/Important source finding; confirmed resource lookup occurs during composition, fork/dev identity and JNI/component names remain consistent. Minor: old branding remains in non-executable Gradle comments; deliberately deferred. Review did not replace compilation, runtime testing or an exhaustive resource-tail/dependency audit.
- NOT RUN: full architectureCheck/lint, Android unit tests, APK assembly/link/signature check, installation or device locale/customization smoke tests. Static key coverage does not establish whole-app translation coverage.

## 2026-11-27: durable in-app links

- Scope: CTH-VI-004, per the user's follow-up request to check links inside the app and replace fragile ones.
- Live target verification before editing (`.agent/link-target-checks.json`): GitHub API returned 200 and the exact asset for `android-sdk-tools-static-aarch64.zip` (35.0.2), `android-ndk-r29-aarch64.tar.xz` (with sha256 digest) and `jadx-1.5.0.zip`. `raw.githubusercontent.com/mrtaix/tianxuan/main/sponsors.json` returned 200. `api.github.com/repos/mrtaix/tianxuan` returned 200, public, default branch `main`.
- Live checks that are negative and remain unfixed by design: `repos/mrtaix/tianxuan/releases` returned `[]` and `releases/latest` returned 404, so the update flow and the release-history link stay empty until the fork publishes a release. `raw.githubusercontent.com/wkbin/tianxuan/main/sponsors.json` returned 404, which confirmed the sponsor list URL was dead.
- RED: the new link contract test failed — the sponsor list still pointed at `wkbin/tianxuan` and installer scripts still listed retired proxy hosts. See links-red.log.
- Change: sponsor list now reads the fork's `sponsors.json`, User-Agent renamed to `CTHarness-App`. Removed the four GitHub download proxies from the ARM64 SDK tools, NDK and JADX installers, keeping the versioned upstream GitHub asset URLs. Fixed a real JADX bug found by the new test: the script built `jadx-v1.5.0.zip` while the published asset is `jadx-1.5.0.zip`. Replaced the stale `xiaozhe7772222/tianxuan` link in all three settings locales and in its `SettingsLocalization` match key.
- PASS: `python3 tools/check-ctharness.py` — 11 tests, exit 0, `OK` (links-green.log).
- PASS: `sh -n` on all three changed installer scripts.
- PASS: retired proxy host scan found no remaining occurrence in the installer scripts or the sponsor list source.
- PASS: AAPT2 compiled the six changed resource files; `git diff --check` clean.
- NOT CHANGED, with reasons: sponsor email and its prefilled API-key request (a personal support channel, not a link to redirect), the QQ group (a community channel, not a URL), `FlutterSdkDownloader` proxies (that path verifies SHA-256 and keeps a direct GitHub fallback; removing it would trade durability for reachability on restricted networks), and the bundled `raw.githubusercontent.com` sponsor avatar hosts.
- PASS: `:feature:navigation:compileDebugKotlin` — `BUILD SUCCESSFUL in 15m 21s`, 226 actionable tasks, `GRADLE_EXIT=0` (`.agent/iteration-gradle-nav.log`). This compiled the changed Kotlin for `feature:custom_iteration`, `feature:navigation`, `feature:settings` and their dependency graph. Gradle did not report warnings for the changed files.
- Environment work needed for that compile, none of it committed to the project: approved install of openjdk-25 and openjdk-17; SDK licences accepted at the user's explicit approval and `build-tools;36.0.0` / `platforms;android-37.1` downloaded; a one-off script `.agent/build-nav.sh` installs the SDK under scratch and swaps Google's x86 `aidl` for the phone's ARM64 one, because `runtime:compileDebugAidl` cannot run the bundled binary.
- GitHub: committed locally as `9ec3725` (`feat: localize CTHarness and repair runtime flows`), pushed to `origin/feat/ctharness-vi` with upstream tracking. CI fix `ba57167` adds JDK 25 for the Gradle daemon; ratchet fix `329ba68` keeps the four flagged files at or below their recorded line counts. Local check still reports 13 tests OK and `git diff --check` clean.
- GitHub CI PASS: run `45983357409`, head `329ba68`, `Build & Package CTHarnessDev` succeeded in 5m40s; every build and packaging step passed. Artifact `tianxuandev-apk`, id `15190845874`, 46,116,952 bytes, digest `sha256:cecd5c9381d64ca14d71fd2ef5d64324e3abcd38181599c550d9a30549e4f260`, not expired. Workflow warnings only concern deprecated action versions; no failure.
- The earlier CI runs `45949973732` (JDK 25 toolchain missing) and `45955416128` (three architecture-ratchet violations) both failed and were fixed in later commits.
- NOT VERIFIED: APK package name/label/signature from the downloaded artifact, installation and on-device behavior. The workflow builds the dev variant, so expect `com.ctharness.app.dev` / `CTHarness Dev`, not the release package.
- STILL NOT RUN: APK assembly and linking, signing, installation, and on-device locale, customization, link and runtime smoke tests. A module compile is not an APK build or a runtime test.

## 2026-11-27: header logo and three runtime defects

- Scope: CTH-VI-005 and CTH-VI-006, requested by the user while the previous work was in progress.
- Header logo removed from `RuntimeTopBar`, `ChatTopBar` and `SessionsPaneHeader`; the regression test failed on all three before the edit. Brand badge stays in onboarding and the catalog preview, which are not headers.
- Heavy attachments: `ChatAttachment` previously aborted any copy above `BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES` (50 MB). It now streams up to 500 MB into `linux-runtime/attachments` and still references the sandbox path; no in-memory copy was added.
- Queued messages: `launchSessionJobLocked` now reports whether it actually launched a run, recovers a task left `RUNNING` by a previous process via `RECOVERING`, and `startNextQueuedLocked` emits a visible warning instead of silently losing a consumed item. `startSessionRun` drains first and only enqueues the new message when a consumer was actually started. `finishRun` catches drain exceptions so one lane error cannot abort the queue chain.
- Shizuku: `checkShizukuPrivilege` waits for a late sticky Binder before reporting "service not running". After a prior denial it no longer promises a popup that Shizuku will not show; it points to Shizuku App → Applications → CTHarness. Shizuku guidance now uses CTHarness branding.
- RED: the new combined contract test failed before these edits (50 MB constant, no Boolean launch result, old Shizuku strings). Verified by reading the pre-edit sources.
- PASS: `python3 tools/check-ctharness.py` — 13 tests, exit 0, `OK`.
- PASS: `:feature:navigation:compileDebugKotlin` after all logo, attachment, queue and Shizuku edits — `BUILD SUCCESSFUL in 5m 41s`, exit 0. This transitively compiled `feature:chat`, `feature:components`, `feature:custom_iteration`, `harness`, `runtime` and `feature:navigation`.
- NOT RUN: APK assembly, signing, installation and device reproduction of the three reported defects. The installed package on this phone is still upstream `top.wkbin.tianxuan` 0.36.4, so device logs cannot confirm the fork fixes.

## 2026-11-27 continued: broad localization + Shizuku investigation

- User reported too much Chinese remained. Source-level inventory found ~1,906 Chinese literals across 131 Kotlin files; the dominant cause was 42 settings files importing raw `androidx.compose.material3.Text` instead of the `LocalizedText` bridge.
- Changed 15 high-volume settings UI files to alias `LocalizedText as Text` (same signature, no line growth). Extracted all 23 remaining Chinese literals from OnboardingScreen into values/values-en/values-vi resources and replaced them, including Toast/dialog strings.
- Terminology per user: `tác nhân AI` -> `AI Agent`, `không gian làm việc` -> `Không gian` (128 occurrences across 13 Vietnamese resource files).
- Offline translation changed from English→Chinese to English→Vietnamese: `TranslationManager` now targets `TranslateLanguage.VIETNAMESE`, downloads the Vietnamese model, and five labels in `TranslationModelCard` use localized resources in all three locales.
- PASS: `python3 tools/check-ctharness.py` — 15 tests, exit 0.
- PASS: `:feature:settings:compileDebugKotlin :feature:onboarding:compileDebugKotlin :core:common:compileDebugKotlin` — `BUILD SUCCESSFUL in 4m 14s`, exit 0 (`.agent/compile-localization5.log`). Added the missing `R` import in TranslationModelCard after one failed compile.
- Pushed commit `19b7464` (`fix(i18n): translate onboarding and remaining settings screens`) to `origin/feat/ctharness-vi`.
- Shizuku timeout investigation: decompiled the installed `top.wkbin.tianxuan` APK with apktool (smali) and compared `ShizukuHostServiceClient`, `ShizukuHostUserService`, `PrivilegeManager.checkShizukuPrivilege`, `bindUserService`, manifest provider and permission grants against the fork and the CTHarnessDev APK. Both installed and fork use `bindUserService`, `CONNECTION_TIMEOUT_MS=20000`, `BIND_MAX_RETRIES=2`, the same `tianxuan-host-shell-v1` tag and the same `ProcessBuilder("/system/bin/sh","-c")` runner. No alternative fix exists in the installed APK to copy; it works because that package is authorized and its service instance is healthy. Both packages show `moe.shizuku.manager.permission.API_V23: granted=true`.
- Fork keeps its own improvements (late-Binder wait, actionable re-authorization text, stale-service reset helper) but has NOT resolved the UserService bind timeout. AndroidHarness itself uses a different `IShizukuService.newProcess()` + stdin/stdout runner path, but `moe.shizuku.server` is not a declared dependency of this fork; adopting it would be a larger runtime change, not a copy of the installed app's fix.

## Review boundaries

The original constellation icon contains no lettering. Its graphic and JNI
resource identifiers remain intact; visible labels and logo description use
`CTHarness` exactly. Paths such as `Pictures/TianXuan` are preserved because
they describe existing runtime paths, not the display name.

Resource coverage does not prove the absence of untranslated Kotlin literals.
A follow-up UI pass is still required before calling localization complete.
