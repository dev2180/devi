# Devi Overlay: Design

Date: 2026-11-10
Target phone: realme GT2 Pro (1440 × 3216 screen, realme UI / Android 12–14)

## Intent

The owner wants the first thing they see when they pick up their phone in the
morning, and the last thing at night, to be a picture of Devi. They want the raw
photo as it is.

A wallpaper doesn't work for this. The clock, notifications and icons clutter
it, and wallpapers crop or stretch the image. Instead, a button combination
calls up the picture, shown alone on black, and it disappears completely when
dismissed.

It is a personal devotional tool for one person on one phone. It will not go
on the Play Store.

## Goals

1. Pressing **Volume Up → Volume Down → Volume Up** quickly opens a full-screen
   viewer showing the picture marked as **main**.
2. This works **while the phone is locked**. The viewer appears over the lock
   screen without unlocking, and closing it returns to the still-locked lock
   screen.
3. Each picture is shown **whole and unaltered**: aspect ratio preserved, never
   cropped, trimmed or stretched, on a plain black background, with nothing
   else on screen (no status bar, navigation bar, clock or controls).
4. The viewer is a small gallery. Swipe left or right through all saved
   pictures. Every time it opens, it starts on the main picture.
5. A tap or a downward swipe closes the viewer, and it is gone (not left in
   recent apps).
6. A manage screen, opened from the app icon, lets the owner:
   - add pictures from the phone's gallery (copied byte-for-byte, never
     recompressed)
   - choose which picture is main
   - remove pictures
   - turn the button shortcut on or off
   - get walked through the phone settings the app needs
7. If a picture can't be shown well on this screen, the app **says so** when
   it is added, rather than silently altering it (see "Fit warning").

## Non-goals

- Working while the screen is fully off. Android does not pass volume presses
  to apps while the screen is off, and the owner has accepted this. The flow is:
  press power once to light the screen, then the combo.
- Using the power button as part of the combination (Android doesn't allow it).
- Working before the first unlock after a reboot (my call, see below).
- Cloud sync, accounts, sharing, editing, filters or zoom.
- Distribution to anyone else.

## Anti-goals (a failure even if it technically works)

- Any cropping, stretching, re-encoding or quality loss of the owner's
  pictures.
- Anything drawn over the picture: text, buttons, clock, page dots, toasts.
- The combo firing by accident in normal use, or realme silently killing the
  button listener so the combo stops working.
- Having to unlock the phone to see her.
- Ads, internet access, analytics or notifications.

## Decided with the owner

- **Platform:** a native Android app for their realme GT2 Pro.
- **Trigger:** Volume Up → Down → Up, pressed quickly.
- **Volume slider (decided after the builder check):** to keep the system volume
  panel from being drawn over the picture, the service **consumes** volume
  key events (returns `true`, so no volume change and no panel) whenever
  **the keyguard is showing and no media is playing**
  (`KeyguardManager.isKeyguardLocked` and `AudioManager.isMusicActive() == false`).
  In every other state it passes them through (returns `false`): unlocked, or
  while music or video is playing. Then volume works normally, the panel may
  briefly show over the picture, and the volume ends one step higher. Accepted
  cost: on a silent lock screen, the volume buttons don't change the ringer
  volume. The combo detector sees every press either way.
- **How long the viewer stays (decided after the builder check):** the screen
  stays lit while the viewer is open (`FLAG_KEEP_SCREEN_ON`). The viewer closes
  itself after **10 minutes** as a safety net. If the screen is turned off
  with the power button while the viewer is open, the viewer closes.
- **Locked phone:** must work over the lock screen. Pressing power first to
  wake the screen is fine.
- **Display:** the whole picture on black, scaled to fit the screen while
  keeping its shape. For the main picture (514 × 913, wider than the screen's
  ratio), this means full width with a black band top and bottom. The owner
  accepted this.
- **Gallery:** several pictures, swipeable. It always opens on the main one.
- **Close:** a tap or swipe down, and it is gone.
- **Manage:** an Add button, the ability to choose the main picture, and
  removing pictures.
- **Fit warning:** tell the owner if a picture doesn't fit, instead of
  altering it.

## Calls I made (the owner can override)

- **Build approach:** Kotlin with Android Gradle, built on the owner's Windows
  PC (the Android SDK is already installed at `%LOCALAPPDATA%\Android\Sdk`).
  The output is a single APK the owner installs by sideloading. The project
  lives in this `devi` folder.
- **How the buttons are detected:** an Android **AccessibilityService** with
  key-event filtering. It is the only supported way for a normal (non-root)
  app to see volume presses system-wide, including on the lock screen.
  Accessibility services are also allowed to start an activity from the
  background.
- **Combo timing:** three volume key-down events in the order Up, Down, Up, each
  within about 600 ms of the previous one. A wrong key resets the sequence.
  Long-press repeats are ignored. The builder may tune these timings during
  testing.
- **Showing over the lock screen:** the viewer activity uses `setShowWhenLocked(true)`
  and `setTurnScreenOn(true)`, without dismissing the keyguard. It runs in
  immersive full-screen mode, is excluded from recents, and has no history,
  so closing it reveals the lock screen untouched.
- **Where the combo works:** everywhere the screen is on: the lock screen,
  the home screen and inside other apps. The owner said "it should work when it
  is locked *also*". When unlocked, closing the viewer returns to whatever was
  on screen before. The tight timing window is what guards against accidental
  firing.
- **Orientation:** the viewer is locked to portrait. The fit rules assume
  portrait.
- **Combo while the viewer is open:** does nothing (no second viewer).
- **No pictures saved:** the combo does nothing.
- **Gallery order:** the order in which pictures were added. The viewer opens at
  the main picture's position, and swiping does not wrap around.
- **Default main:** the first picture added becomes main. If the main picture
  is removed, the first remaining picture becomes main.
- **Storage:** pictures are copied byte-for-byte from the picker's content URI
  into app-private storage. A small JSON file or SharedPreferences holds the
  order and the main ID. Decoding respects EXIF orientation. Large images are
  decoded at a sample size that fits the screen, purely for display; the stored
  file is never changed. Supported formats: JPEG, PNG, WebP, HEIC.
- **Picker:** the Android Photo Picker, allowing multiple selection, so no
  storage permission is needed.
- **Manage screen layout:** a grid of thumbnails with an **Add** button. Tap a
  picture to make it main (shown with a star). Long-press to remove it, with a
  confirmation. A switch turns the shortcut on or off. A **Preview** button opens
  the viewer exactly as the combo would.
- **Fit warning:** pictures are never cropped, so shape alone never stops one
  from fitting. When a picture is added, the app shows a short note in two cases:
  - **Low resolution:** the picture is narrower than 1080 px while it would be
    scaled up to fill the 1440 px width. The note says it may look soft and
    suggests adding the original. This applies to the main picture as pasted
    (514 px wide).
  - **Small display:** the picture's shape leaves it filling less than about
    half the screen (a very wide panorama, for example).

  The picture is still added in both cases. The note only informs.
- **Setup checklist:** the manage screen shows a checklist card until both items
  are done, each with a button that opens the right settings page:
  1. Accessibility → enable "Devi" (the service).
  2. Battery → set "Devi" to *No restrictions* (unrestricted / ignore battery
     optimisations), plus realme's auto-launch toggle if present.

  realme UI is known to kill background services otherwise.
- **After a reboot:** the service and pictures are available after the first
  unlock. Making it work before the first unlock (direct boot) adds complexity
  for little benefit.
- **Permissions:** no internet, no storage, no notifications.
- **Install:** debug-signed (or a local self-signed) APK. The owner gets
  step-by-step instructions to copy it to the phone, allow "install unknown
  apps", install it, and complete the setup checklist.

## Left to the builder

- minSdk/targetSdk (must cover Android 12–14 and match an installed SDK
  platform), Gradle and AGP versions, and dependencies (keep them minimal: a
  pager and an image decoder are enough).
- Swipe and tap gesture details, as long as horizontal swipes move between
  pictures and a tap or a mostly-vertical downward swipe closes the viewer.
- Thumbnail generation and the visual styling of the manage screen (plain and
  calm).
- Test strategy. The combo-sequence detector and the fit-warning rules should
  be pure functions with unit tests. Lock-screen behaviour needs a manual check
  on the phone.
- The app name and icon (working name: "Devi").

## Update (2026-11-10, after first phone test — approved by the owner in chat)

- **Choosable volume combination:** Up Down Up (default), Down Up Down, Up Up Down,
  Down Down Up, Up Down Up Down, or Off. The detector matches the most recent presses (so
  overlapping attempts count), each ≤ 600 ms apart. The v1 on/off switch migrates
  (off → "Off").
- **Power trigger (optional switch, default off):** Android does not let apps read the
  power key, so Devi watches screen off → on within 800 ms. That is two presses from a
  lit screen and three from a dark one. It is ignored when the audio mode is not normal
  (calls, where the proximity sensor flicks the screen). The owner turns off realme's
  double-press-for-camera; the manage screen explains this and links to Settings.
- **Closing:** a tap no longer closes the viewer (avoids accidental closes). The
  system back swipe and swipe down close it. A sideways swipe still changes pictures.
- **Build:** Gradle output moved to `~/.devi-build` because OneDrive sync locked the
  in-folder build directory.

## Update 2 (2026-11-10) — power trigger replaced (approved by the owner in chat)

The phone is an Indian model (region IN). Indian rules require that three quick power presses
trigger Emergency SOS, and realme locks that setting so it can't be disabled. The
"screen off → on within 800 ms" trigger, which needed three presses from a dark screen, is
**removed**. It is replaced by **"power, then a volume button"**: the screen wakes, then
Volume Up or Down is pressed within 1.5 s. Rules:
- it fires at most once per wake;
- it is ignored during calls (audio mode not normal);
- a volume key that arrives before the late SCREEN_ON broadcast counts as within the window.

The power trigger works whether the volume combination is on or Off. The camera-double-press
advice is dropped.
