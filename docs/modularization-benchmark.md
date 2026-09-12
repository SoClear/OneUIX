# Module Split and Build Benchmark

Measured on 2026-09-11 UTC, starting from commit
`21e06a42b0ce6b7256ea11214f8bec656cbdc99b`.

## Module Boundaries

| Module | Responsibility |
| --- | --- |
| `app` | Compose settings, ViewModel, DataStore persistence, settings Activity and tile services, final APK |
| `common` | Shared preference models, JSON configuration, package constants, platform information, module identity, shared strings |
| `hook` | Xposed entry point, hooks, DexKit caches, preference reader, RebootActivity, hook-only drawables |
| `stub` | Compile-time declarations for APIs provided by the target device |

Dependencies:

```text
app    --implementation--> common
app    --runtimeOnly-----> hook
hook   --implementation--> common
app/common/hook --compileOnly--> stub
app/hook        --compileOnly--> Xposed API (app and hook only)
```

`common` is an Android library because `ONE_UI_VERSION` uses a hidden Android
API and the module owns shared resources. It does not depend on Compose,
DataStore, Xposed, or `app`.

`app` cannot import hook implementation classes. Its `compileOnly` Xposed and
stub dependencies also supply R8 with host-provided types when shrinking the
APK; they are not packaged.

The application ID is defined once by `oneuix.applicationId` in
`gradle.properties`. Both the APK and `common.BuildConfig.MODULE_APPLICATION_ID`
use it. Library namespaces
are not application IDs. Any future application ID suffix/flavor must also be
reflected in the corresponding common BuildConfig variant.

The Xposed entry class remains `io.github.soclear.oneuix.hook.Main`.
`hook` owns `assets/xposed_init` and its consumer keep rules. The APK retains
resource package ID `0x55`, the original `RebootActivity` component name, the
`preference.json` filename, and the existing JSON field names/defaults.

### Activity Ownership Follow-Up

`RebootActivity`, its manifest declaration, and its consumer keep rule now belong
to `hook`. Its package remains `io.github.soclear.oneuix` to preserve the existing
component name; a Kotlin package does not determine Gradle module ownership.
Power-menu hooks use `RebootActivity::class.java.name` directly. `app` has no
Activity declaration or class-name keep rule for this component.

`ModuleContract` was removed. Legacy Xposed hooks still read the module APK ID
from `common.BuildConfig.MODULE_APPLICATION_ID`, not from the host Context.
The planned modern libxposed migration can replace that identity lookup and
the legacy DataStore hooks, but it does not remove the need to package Hook code:
`app` retains `runtimeOnly(project(":hook"))` while it assembles the APK.

The measurements below precede this follow-up and have not been remeasured.

## Method

- Apple M5 Pro, 18 CPU cores, 48 GiB RAM, macOS 26.6.2 arm64.
- Gradle 9.6.1, Azul Zulu JDK 25, AGP 9.4.0, Kotlin 2.4.10.
- SDK 37; Debug APK builds, not installation or IDE sync.
- Same Gradle JVM heap (`-Xmx2048m`), warmed Gradle daemon, and configuration cache.
- Offline dependency resolution; dependency downloads excluded from measurements.
- Build cache disabled to prevent restored outputs being counted as compilation.
- Same `--no-parallel` flag before and after; no simultaneous benchmark builds.
- One warm-up for each task graph, followed by three recorded samples.
- Wall-clock duration of the complete Gradle invocation, including startup,
  compilation, dexing, and packaging. Medians are used below.

Commands:

```sh
./gradlew clean :app:assembleDebug --offline --no-build-cache --no-parallel --console=plain
./gradlew :app:assembleDebug --offline --no-build-cache --no-parallel --console=plain
```

Scenarios:

- Clean: execute `clean :app:assembleDebug` for every sample. This does not delete
  the dependency cache or restart the Gradle daemon.
- No change: execute `:app:assembleDebug` without changing inputs.
- UI implementation edit: change `ui/theme/Type.kt` body line height through
  `24 -> 25 -> 26 -> 24`, building after each change.
- Hook implementation edit: add `[benchmark 1]`, then `[benchmark 2]`, then
  remove the prefix inside `hook/util/Util.kt`'s `xlog`, building after each change.

Both edit scenarios preserve public signatures. All temporary edits were restored.
Changes to shared models, public APIs, resources, or build scripts were not timed.

## Results

All times are seconds. A positive reduction means a shorter build.

| Scenario | Before samples | After samples | Before median | After median | Reduction |
| --- | --- | --- | ---: | ---: | ---: |
| Clean Debug APK | 8.960, 7.980, 7.676 | 5.589, 5.284, 5.222 | 7.980 | 5.284 | 33.8% |
| No change | 1.035, 0.998, 0.998 | 1.043, 1.010, 1.011 | 0.998 | 1.011 | -1.3% |
| UI edit | 1.910, 1.446, 1.511 | 1.724, 1.373, 1.321 | 1.511 | 1.373 | 9.2% |
| Hook edit | 1.586, 1.745, 1.590 | 1.447, 1.346, 1.323 | 1.590 | 1.346 | 15.4% |

After the split, UI edits execute only `:app:compileDebugKotlin`; Hook and common
Kotlin compilation stay `UP-TO-DATE`. Hook edits execute only
`:hook:compileDebugKotlin`; app and common stay `UP-TO-DATE`.

Clean builds are faster in this run. Incremental differences are small in
absolute terms, and no-change builds are effectively unchanged. This is a
single-machine, three-sample comparison, not a general performance guarantee.

### Environment Limitation

On both sides, the execution sandbox denied writes to
`~/Library/Application Support/kotlin/daemon`, so Kotlin reported a daemon failure
and successfully used its non-daemon fallback. The same limitation and flags were
retained for comparison. These numbers should not be treated as measurements of
normal IDE builds with a working Kotlin daemon. The initial before-split warm-up
took 63.080 seconds and was excluded.

Local raw logs, per-sample JSON, timing helpers, compatibility checks, and APK
snapshots are stored in `.trae/build-benchmark/` (git-ignored).

## Verification

- Debug and minified/resource-shrunk Release APK builds.
- Old and new preference models: identical encoded defaults and three JSON
  fixtures, including customized values, unknown keys, and old-backup round trips.
- APK contents: Xposed entry asset and entry class, both hooked DataStore entry
  classes, `RebootActivity`, resource package ID `0x55`, three menu icons, all five
  string locales, and the original arm64 native library set.
- No Xposed API, Samsung stub classes, or `android.os.SystemProperties` definition
  bundled into the APK.
- Device/LSPosed runtime behavior was not tested. Module activation, settings
  sharing, and the power menu still require a device smoke test.
