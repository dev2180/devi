# Devi Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Android app. Pressing Volume Up→Down→Up shows the owner's Devi pictures full screen over the lock screen, unaltered on black, and a manage screen lets the owner add pictures, remove them and choose the main one.

**Architecture:** The app uses the platform SDK only, with zero androidx dependencies.
- An `AccessibilityService` filters volume key events. It feeds a pure `ComboDetector` and a pure `VolumeKeyPolicy`, and launches `ViewerActivity` over the keyguard.
- `ImageStore` (plain Kotlin and `java.io`) owns the copied files, their order and the main picture.
- `FitCheck` (pure) produces the add-time notes.
- `ManageActivity` (the launcher activity) uses a platform `GridView`.

**Tech Stack:**
- Kotlin 2.0.21
- Android Gradle Plugin 8.13.2, Gradle 8.13 (wrapper)
- compileSdk/targetSdk 36, minSdk 31
- JUnit 4.13.2 for JVM unit tests
- Java 22 on PATH runs Gradle. `jvmTarget` is 17.

All of these are already in the local Gradle cache or SDK.

**Spec:** `docs/superpowers/specs/2026-11-10-devi-overlay-design.md` (read it alongside this plan).

**Version control:** the `devi` folder is not a git repository, and the owner hasn't asked for one. There are no commit steps. Do not run `git init` unless asked.

## Global Constraints

- Application id `com.devi.overlay`, app label `Devi`. The project root is the `devi` folder: `settings.gradle.kts` at the root, module `app/`.
- There are no `INTERNET`, storage or notification permissions. The only extra permission is `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Picture bytes are copied byte-for-byte and never re-encoded. Display uses `ImageView.ScaleType.FIT_CENTER` on black, never a crop scale type.
- The viewer draws nothing over the picture: no text, no page dots, no toasts. System bars are hidden (immersive).
- Combo: Up, Down, Up key-downs, each ≤ 600 ms after the previous one. Key repeats (`repeatCount > 0`) are ignored.
- Viewer: portrait only, `FLAG_KEEP_SCREEN_ON`, auto-close after 10 minutes, close on `ACTION_SCREEN_OFF`, excluded from recents, `noHistory`.
- Fit notes use a reference screen of 1440 × 3216, the low-resolution threshold is width < 1080 px, and the small-display threshold is coverage < 0.5.
- Settings: SharedPreferences file `settings`, key `shortcut_enabled`, default `true`.
- Build commands are run from the `devi` folder with `./gradlew` (Git Bash).

## Review Focus

1. **The phone rings, or an alarm fires, while it's locked.** The volume buttons must still silence the ringtone or reach the alarm app. The policy only consumes keys when the audio mode is `MODE_NORMAL` and the active window is the system UI lock screen or our viewer. Tests are in Task 1.
2. **Combo pressed with no pictures saved.** Nothing happens and nothing crashes. `ImageStore.main()` returns `null`, and both the service and the viewer bail out. Tests are in Task 3.
3. **A picture can't be decoded** (an unsupported HEIC, or a damaged or partial copy). On add, it is rejected with a message and leaves no file behind. In the viewer, the slot shows plain black and swiping still works, with no crash. The store test is in Task 3, and the viewer is checked manually in Task 7.
4. **Long-press, overlapping or slow sequences.** A held key fires nothing. Up Down Up Down Up fires once at the first completion, then needs a fresh Up Down Up. A gap > 600 ms resets. Tests are in Task 1.
5. **Main picture removed, or the last picture removed.** The first remaining picture becomes main. With none left, main is `null` and the order is empty. Tests are in Task 3.

---

### Task 1: Project scaffold, ComboDetector, VolumeKeyPolicy

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (application only, label `Devi`, no activities yet), `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `app/src/main/java/com/devi/overlay/ComboDetector.kt`
- Create: `app/src/main/java/com/devi/overlay/VolumeKeyPolicy.kt`
- Test: `app/src/test/java/com/devi/overlay/ComboDetectorTest.kt`, `app/src/test/java/com/devi/overlay/VolumeKeyPolicyTest.kt`

**Interfaces:**
- Produces:
  - `enum class VolumeKey { UP, DOWN }`
  - `class ComboDetector(private val maxGapMs: Long = 600)` with:
    - `fun onKeyDown(key: VolumeKey, timeMs: Long, isRepeat: Boolean): Boolean`. Returns true exactly when Up, Down, Up completes.
    - `fun reset()`
  - `object VolumeKeyPolicy { fun shouldConsume(keyguardLocked: Boolean, musicActive: Boolean, audioModeNormal: Boolean, activePackage: String?): Boolean }`. Returns true only when all of these hold:
    - `keyguardLocked`
    - `!musicActive`
    - `audioModeNormal`
    - `activePackage` is `"com.android.systemui"` or `"com.devi.overlay"`

- [ ] **Step 1: Generate the wrapper.** Write the four Gradle files:
  - Plugins: `com.android.application` 8.13.2 and `org.jetbrains.kotlin.android` 2.0.21.
  - Repositories: `google()` and `mavenCentral()`.
  - Android: namespace `com.devi.overlay`, compileSdk 36, minSdk 31, targetSdk 36.
  - Test dependency: `testImplementation("junit:junit:4.13.2")`.
  - Kotlin `jvmTarget = "17"`, with Java compile options set to 17.

  Then run `"$HOME/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle" wrapper --gradle-version 8.13`.
  Expected: `gradlew` and `gradle/wrapper/gradle-wrapper.properties` exist, with `distributionUrl` ending in `gradle-8.13-bin.zip`.

- [ ] **Step 2: Write failing tests**

```kotlin
// ComboDetectorTest
@Test fun upDownUpWithinWindowFires() { val d = ComboDetector()
  assertFalse(d.onKeyDown(UP, 0, false)); assertFalse(d.onKeyDown(DOWN, 300, false)); assertTrue(d.onKeyDown(UP, 600, false)) }
@Test fun gapOver600Resets() { val d = ComboDetector()
  d.onKeyDown(UP, 0, false); d.onKeyDown(DOWN, 601, false); assertFalse(d.onKeyDown(UP, 900, false)) }
@Test fun wrongKeyResetsButUpRestarts() { val d = ComboDetector()
  d.onKeyDown(UP, 0, false); d.onKeyDown(UP, 100, false) // second UP starts a fresh sequence
  d.onKeyDown(DOWN, 200, false); assertTrue(d.onKeyDown(UP, 300, false)) }
@Test fun downFirstNeverFires() { val d = ComboDetector()
  d.onKeyDown(DOWN, 0, false); d.onKeyDown(UP, 100, false); assertFalse(d.onKeyDown(DOWN, 200, false)) }
@Test fun repeatsIgnored() { val d = ComboDetector()
  d.onKeyDown(UP, 0, false); assertFalse(d.onKeyDown(UP, 50, true)); d.onKeyDown(DOWN, 100, false); assertTrue(d.onKeyDown(UP, 200, false)) }
@Test fun firesOnceThenNeedsFreshSequence() { val d = ComboDetector()
  d.onKeyDown(UP,0,false); d.onKeyDown(DOWN,100,false); assertTrue(d.onKeyDown(UP,200,false))
  assertFalse(d.onKeyDown(DOWN,300,false)); assertFalse(d.onKeyDown(UP,400,false)) }
// VolumeKeyPolicyTest
@Test fun consumesOnSilentLockScreen() = assertTrue(VolumeKeyPolicy.shouldConsume(true, false, true, "com.android.systemui"))
@Test fun consumesOverOwnViewer() = assertTrue(VolumeKeyPolicy.shouldConsume(true, false, true, "com.devi.overlay"))
@Test fun passesWhenUnlocked() = assertFalse(VolumeKeyPolicy.shouldConsume(false, false, true, "com.android.systemui"))
@Test fun passesWhenMusicPlaying() = assertFalse(VolumeKeyPolicy.shouldConsume(true, true, true, "com.android.systemui"))
@Test fun passesWhenRingingOrInCall() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, false, "com.android.systemui"))
@Test fun passesWhenAlarmAppOnTop() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, "com.coloros.alarmclock"))
@Test fun passesWhenPackageUnknown() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, null))
```

- [ ] **Step 3: Run tests to see them fail.** Run `./gradlew :app:testDebugUnitTest`. Expected: compilation fails (unresolved `ComboDetector`).
- [ ] **Step 4: Implement both files** with the signatures above. In the detector, `firesOnceThenNeedsFreshSequence` means that after it fires, the state resets to empty.
- [ ] **Step 5: Run the tests and the build.** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`, all 13 tests pass.

### Task 2: FitCheck

**Files:**
- Create: `app/src/main/java/com/devi/overlay/FitCheck.kt`
- Test: `app/src/test/java/com/devi/overlay/FitCheckTest.kt`

**Interfaces:**
- Produces:
  - `enum class FitNote { LOW_RESOLUTION, SMALL_DISPLAY }`
  - `object FitCheck { fun evaluate(imageW: Int, imageH: Int, screenW: Int = 1440, screenH: Int = 3216): Set<FitNote> }`
  - `fun message(note: FitNote): String`, with this copy:
    - LOW_RESOLUTION: `"This picture is small for your screen, so it may look a little soft. If you have the original, add that instead."`
    - SMALL_DISPLAY: `"Because of its shape, this picture will fill less than half of your screen. It is still shown whole, never cropped."`
- Rules: `scale = min(sw/iw, sh/ih)`.
  - LOW_RESOLUTION applies when `imageW < 1080 && scale > 1`.
  - SMALL_DISPLAY applies when `(iw*scale)*(ih*scale)/(sw*sh) < 0.5`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun ownersMainPictureIsLowResOnly() = assertEquals(setOf(LOW_RESOLUTION), FitCheck.evaluate(514, 913))
@Test fun fullResPortraitHasNoNotes() = assertEquals(emptySet<FitNote>(), FitCheck.evaluate(1440, 3216))
@Test fun bigPhoneShapedPhotoHasNoNotes() = assertEquals(emptySet<FitNote>(), FitCheck.evaluate(3000, 4000))
@Test fun widePanoramaIsSmallDisplay() = assertEquals(setOf(SMALL_DISPLAY), FitCheck.evaluate(6000, 2000))
@Test fun tinyWideImageGetsBoth() = assertEquals(setOf(LOW_RESOLUTION, SMALL_DISPLAY), FitCheck.evaluate(800, 400))
@Test fun zeroSizeIsSafe() = assertEquals(emptySet<FitNote>(), FitCheck.evaluate(0, 0)) // guard: no divide-by-zero
```

- [ ] **Step 2: Run the tests to see them fail.** Run `./gradlew :app:testDebugUnitTest --tests '*FitCheckTest'`. Expected: compile failure.
- [ ] **Step 3: Implement `FitCheck.evaluate` and `message`** as specified.
- [ ] **Step 4: Run the tests.** Same command. Expected: 6 pass.

### Task 3: ImageStore

**Files:**
- Create: `app/src/main/java/com/devi/overlay/ImageStore.kt`
- Test: `app/src/test/java/com/devi/overlay/ImageStoreTest.kt` (uses `TemporaryFolder`)

**Interfaces:**
- Produces: `class ImageStore(private val dir: File)`. Files live in `dir`, the order is kept in `dir/order.txt` (one id per line) and the main id is kept in `dir/main.txt`. Methods:
  - `fun ids(): List<String>`: insertion order.
  - `fun main(): String?`
  - `fun add(input: InputStream, extension: String): String`
    - Copies the bytes to a temp file, then renames it to `<uuid>.<ext>`.
    - On any exception, it deletes the temp file and rethrows.
    - It sets main if none is set.
  - `fun remove(id: String)`: if `id` was main, the first remaining picture becomes main, or none.
  - `fun setMain(id: String)`: ignores unknown ids.
  - `fun file(id: String): File`
  - `fun mainIndex(): Int`: the index of main in `ids()`, or 0.
- `companion object { fun forContext(ctx: android.content.Context) = ImageStore(File(ctx.filesDir, "images").apply { mkdirs() }) }`. Keep the Android import confined to that one function, so the JVM tests don't touch it.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun emptyStoreHasNoMain() { val s = store(); assertNull(s.main()); assertEquals(emptyList<String>(), s.ids()); assertEquals(0, s.mainIndex()) }
@Test fun firstAddedBecomesMain() { val s = store(); val a = s.add(bytes(1,2,3), "jpg"); s.add(bytes(4), "png"); assertEquals(a, s.main()) }
@Test fun bytesCopiedExactly() { val s = store(); val a = s.add(bytes(9,8,7,6), "webp"); assertArrayEquals(byteArrayOf(9,8,7,6), s.file(a).readBytes()) }
@Test fun setMainAndIndex() { val s = store(); s.add(bytes(1),"jpg"); val b = s.add(bytes(2),"jpg"); s.setMain(b); assertEquals(b, s.main()); assertEquals(1, s.mainIndex()) }
@Test fun removingMainPromotesFirstRemaining() { val s = store(); val a = s.add(bytes(1),"jpg"); val b = s.add(bytes(2),"jpg"); s.remove(a); assertEquals(b, s.main()); assertFalse(s.file(a).exists()) }
@Test fun removingLastClearsMain() { val s = store(); val a = s.add(bytes(1),"jpg"); s.remove(a); assertNull(s.main()); assertTrue(s.ids().isEmpty()) }
@Test fun failedCopyLeavesNothing() { val s = store(); assertThrows(IOException::class.java) { s.add(throwingStream(), "jpg") }
  assertTrue(s.ids().isEmpty()); assertTrue(dir.listFiles()!!.none { it.extension == "jpg" || it.extension == "tmp" }) }
@Test fun persistsAcrossInstances() { val s = store(); val a = s.add(bytes(1),"jpg"); assertEquals(listOf(a), ImageStore(dir).ids()); assertEquals(a, ImageStore(dir).main()) }
```
- [ ] **Step 2: Run the tests to see them fail.** Run `./gradlew :app:testDebugUnitTest --tests '*ImageStoreTest'`. Expected: compile failure.
- [ ] **Step 3: Implement `ImageStore`.**
- [ ] **Step 4: Run all unit tests.** Run `./gradlew :app:testDebugUnitTest`. Expected: all pass.

### Task 4: ImageLoading helper

**Files:**
- Create: `app/src/main/java/com/devi/overlay/ImageLoading.kt`

**Interfaces:**
- Produces: `object ImageLoading` with:
  - `fun decodeForDisplay(file: File, maxW: Int, maxH: Int): Bitmap?`
    - Uses `ImageDecoder.createSource(file)`, which applies EXIF orientation automatically.
    - In the header callback, it sets `setTargetSampleSize` to the largest power of two that keeps both dimensions ≥ the screen size.
    - Returns `null` on any exception.
  - `fun decodeThumb(file: File, sizePx: Int): Bitmap?`
  - `fun readSize(input: InputStream): Pair<Int, Int>?`
    - Uses `BitmapFactory.Options.inJustDecodeBounds`.
    - Returns `null` if the image is undecodable. For HEIC, it falls back to an `ImageDecoder` header read.

This is display-only. It never writes files. It is verified through Tasks 5 and 7, since it has no JVM test (it uses Android graphics).

- [ ] **Step 1: Implement it.** Then run `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.

### Task 5: ViewerActivity

**Files:**
- Create: `app/src/main/java/com/devi/overlay/ViewerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`. Add the activity with:
  - `android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen"`
  - `android:windowBackground` black, through a `res/values/themes.xml` style `Theme.Devi.Viewer` with parent `@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen`. Set `windowBackground` to `@android:color/black` and `windowLayoutInDisplayCutoutMode` to `shortEdges`.
  - `screenOrientation="portrait"`, `excludeFromRecents="true"`, `noHistory="true"`, `launchMode="singleInstance"`, `taskAffinity=""`, `showWhenLocked="true"`, `turnScreenOn="true"`, `exported="false"`.

**Interfaces:**
- Consumes:
  - `ImageStore.forContext`, `ids()`, `mainIndex()`, `file(id)`
  - `ImageLoading.decodeForDisplay`
- Produces:
  - `companion object { @Volatile var isShowing = false; fun intent(ctx: Context): Intent }`
  - `isShowing` is true between `onResume` and `onPause`.

**Behaviour:**
- Content is a `FrameLayout` (black) holding one `ImageView` with `FIT_CENTER`.
- System bars are hidden through `window.insetsController`, using `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- `FLAG_KEEP_SCREEN_ON` is set.
- If the store is empty, call `finish()` in `onCreate`.
- Start at `mainIndex()`. Decode off the main thread (single-thread executor). If decoding gives `null`, show black.
- A `GestureDetector` handles input:
  - a horizontal fling where `|vx| > |vy|` moves to the next/previous picture, without wrapping
  - `onSingleTapConfirmed` calls `finish()`
  - a fling with `vy > 0 && |vy| > |vx|` (downward) calls `finish()`
- A simple slide animation is the builder's choice.
- Closing:
  - `Handler(mainLooper).postDelayed(::finish, 10 * 60 * 1000L)` runs in `onCreate`, and the callback is removed in `onDestroy`.
  - A receiver for `Intent.ACTION_SCREEN_OFF` is registered in `onCreate` with `RECEIVER_NOT_EXPORTED` and unregistered in `onDestroy`. It calls `finish()`.
  - `onBackPressed`/back callback calls `finish()`.
- Do not call `requestDismissKeyguard`.

- [ ] **Step 1: Implement it and declare it in the manifest.** Run `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`. The behaviour is verified manually in Task 7.

### Task 6: DeviAccessibilityService

**Files:**
- Create: `app/src/main/java/com/devi/overlay/DeviAccessibilityService.kt`
- Create: `app/src/main/res/xml/accessibility_config.xml` with:
  - `android:accessibilityEventTypes="typeWindowStateChanged"`
  - `android:accessibilityFlags="flagRequestFilterKeyEvents"`
  - `android:canRequestFilterKeyEvents="true"`
  - `android:description="@string/a11y_description"`

  The description text is `"Lets Devi open your pictures when you press Volume Up, Down, Up — even on the lock screen. It reads no screen content."`
- Modify: `AndroidManifest.xml`. Add the service with `permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, the `android.accessibilityservice.AccessibilityService` intent filter, and meta-data `android.accessibilityservice` → `@xml/accessibility_config`. Also add `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>`.

**Interfaces:**
- Consumes:
  - `ComboDetector`, `VolumeKey`, `VolumeKeyPolicy.shouldConsume`
  - `ViewerActivity.isShowing`, `ViewerActivity.intent`
  - `ImageStore.forContext(...).main()`
  - SharedPreferences `settings` / `shortcut_enabled`

**Behaviour:**
- `onAccessibilityEvent` records `event.packageName` as `activePackage` for `TYPE_WINDOW_STATE_CHANGED`.
- `onKeyEvent` handles only `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN`:
  1. It computes `consume`, which is `false` when `shortcut_enabled` is off. Otherwise it is `VolumeKeyPolicy.shouldConsume(keyguardManager.isKeyguardLocked, audioManager.isMusicActive, audioManager.mode == AudioManager.MODE_NORMAL, activePackage)`.
  2. On `ACTION_DOWN` with the shortcut enabled, it feeds the detector with `event.eventTime` and `event.repeatCount > 0`.
  3. When the combo fires, `!ViewerActivity.isShowing` holds and `main() != null`, it calls `startActivity(ViewerActivity.intent(this).addFlags(FLAG_ACTIVITY_NEW_TASK))`.
  4. It returns `consume` for both DOWN and UP events of that key.
- All other keys return `false`.

- [ ] **Step 1: Implement it and declare it in the manifest.** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`, all tests pass.

### Task 7: ManageActivity, then the APK and install guide

**Files:**
- Create: `app/src/main/java/com/devi/overlay/ManageActivity.kt` and `app/src/main/res/layout/activity_manage.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon), with a simple vector foreground (the builder's design: a plain deep-red background with a small gold dot is enough) and a background
- Modify: `AndroidManifest.xml`. Add `ManageActivity` as the `MAIN`/`LAUNCHER` activity, with theme `@android:style/Theme.DeviceDefault.DayNight` and `exported="true"`, and set the app `icon`.
- Create: `INSTALL.md` in the `devi` folder

**Interfaces:**
- Consumes:
  - all of `ImageStore`, plus `FitCheck.evaluate`/`message`
  - `ImageLoading.decodeThumb`/`readSize`
  - `ViewerActivity.intent`
  - the settings prefs

**Screen, top to bottom (plain and calm):**
1. **Setup card.** It is shown only while a check fails, and is re-evaluated in `onResume`.
   - **Accessibility row:** "Turn on Devi in Accessibility" opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. The check is whether `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contains `com.devi.overlay/com.devi.overlay.DeviAccessibilityService`.
   - **Battery row:** "Allow Devi to run without battery limits" fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:com.devi.overlay`. The check is `PowerManager.isIgnoringBatteryOptimizations`.
   - Under the battery row is a small text button, "Open app settings (for realme auto-launch)", which opens `ACTION_APPLICATION_DETAILS_SETTINGS`.
2. **Row:** a `Switch` labelled "Volume Up, Down, Up opens Devi" (bound to `shortcut_enabled`), plus a **Preview** button that opens `ViewerActivity.intent` and is disabled when the store is empty.
3. **Grid.** A `GridView` with 3 columns of square thumbnails (decoded off the main thread). The main picture shows a ★ overlay.
   - Tap → `setMain`.
   - Long-press → an `AlertDialog` asking "Remove this picture?", then `remove`.
   - Empty state text: "Add a picture of Devi to begin."
4. **Add button.**
   - On API 33+, or when `MediaStore.getPickImagesMaxLimit()` exists through the SDK extension, it uses `MediaStore.ACTION_PICK_IMAGES` with `EXTRA_PICK_IMAGES_MAX`. Otherwise it falls back to `ACTION_OPEN_DOCUMENT` with `type="image/*"` and `EXTRA_ALLOW_MULTIPLE`.
   - For each returned URI, it reads the size through `contentResolver.openInputStream` + `readSize`. If that is `null`, it shows the dialog "This picture can't be opened, so it wasn't added." and skips it.
   - Otherwise it calls `store.add(stream, extension from MIME type: jpeg→jpg, png, webp, heic/heif→heic, else "img")` and collects `FitCheck.evaluate(w, h)` notes.
   - After the batch, if any notes were found, it shows one `AlertDialog` listing each note's `message` once.

- [ ] **Step 1: Implement the activity, layout, icon and manifest entries.**
- [ ] **Step 2: Run the build.** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`, all tests pass. Then copy `app/build/outputs/apk/debug/app-debug.apk` to `devi/Devi.apk`.
- [ ] **Step 3: Write `INSTALL.md`** in plain words for the owner:
  1. Copy `Devi.apk` to the phone (USB, or send it to yourself).
  2. Open it and allow "Install unknown apps" for the app you opened it with.
  3. Open Devi and add the pictures. Tap the one that should be main.
  4. Complete the two setup rows. On realme, in App info, also turn on Auto launch if it is offered, and set Battery usage to allow background activity.
  5. Test it: lock the phone, press power, then press Volume Up, Down, Up.
  6. Troubleshooting: if the combo stops working after some days, re-check that Accessibility is still on (realme can turn it off after updates).

  Also include the optional USB route: enable USB debugging and run `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r Devi.apk`.
- [ ] **Step 4: Manual phone checklist.** Report it to the owner and do not claim it as passed until they confirm.
  - (a) Locked + screen on + combo → the main picture appears with no clock, bars or volume slider.
  - (b) Swipe → next picture. Tap → back to the still-locked lock screen.
  - (c) Unlocked in another app + combo → the viewer appears, and closing it returns to the app.
  - (d) Music playing on the lock screen → the volume still changes.
  - (e) A call ringing → the volume key silences the ringer.
  - (f) Power off while viewing → the viewer is gone on the next wake.
  - (g) Toggle off → the combo does nothing.
