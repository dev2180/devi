# Devi v3: Design (chants, how-to animations, restart moment, new look, new logo)

Date: 2026-11-10. This builds on `2026-11-10-devi-overlay-design.md` and its updates. Everything there
still holds unless changed here. AOD is out of scope for now (owner's decision).

## Intent
Devi already works: the volume combination and "power, then a volume button" open her over the lock
screen. The owner now wants:
- the app to look beautiful (designed with Hallmark's principles);
- each shortcut to show a small animation of how to press it;
- chants that rise softly and leave with a ghunghroo;
- her to greet them after a restart.

## Goals

### 1. How-to animations
- A small animated phone sits beside each shortcut option. It is a side view: power on the right,
  volume on the left (GT2 Pro).
- A thumb presses the selected combination at real speed, then Devi fades in on the tiny screen. It
  loops gently.
- For power-then-volume: the thumb presses power, the screen lights up, then a volume press, then Devi.
- When the selection changes, the animation changes with it.
- When an option is off, its animation rests, dimmed.

### 2. Chants
- **Library:** an "Add chants" picker for audio files (mp3, m4a, ogg, wav). Files are copied byte-for-byte
  into app storage. Long-press removes one.
- **Playing from the app:** tap a chant to play it.
- **Fade-in:** every chant starts quiet and rises to full over about 10 s. Full means the phone's current
  media volume; Devi never raises the system volume.
- **Pairing:** each picture can be paired with one chant or "none". When a paired picture opens through a
  shortcut, its chant starts with the same fade-in. Swiping to a picture with a different chant
  crossfades to it.
- **Closing the viewer:** the chant fades out over about 1.5 s and a soft ghunghroo jingle plays (about
  1 s). It plays only if a chant was playing.
- **Leaving the app:** a chant started in the app keeps playing in the background, with a media
  notification showing the title, play/pause and stop. Stop fades out and plays the ghunghroo.
- **Volume keys while a chant plays:** never consumed. The owner can turn the chant up or down. This
  is a new input to `VolumeKeyPolicy`.
- **Silent mode:** it doesn't stop chants, because they use media volume. (My call.)

### 3. Restart moment (option, default off)
After the phone restarts, Devi opens on its own at the first unlock, with the main picture's chant if
one is paired. Pictures are locked until the first unlock, so it can't be earlier.

### 4. New look
The manage screen is redesigned, and the viewer itself is unchanged: black, the picture whole, nothing
drawn over it.

### 5. New logo
The new logo replaces the triangle-and-dot icon.

## Chants sourcing (from research)
- No chant audio is bundled in the app.
- Downloading from YouTube is against YouTube's terms. Isha's free-downloads licence excludes Sounds
  of Isha chants.
- The owner adds files they obtain themselves, for example MP3s bought on ishalife.sadhguru.org.
- The Chants screen has a "Find official chants" link. It opens the official pages in the browser,
  so Devi still needs no INTERNET permission.

## Non-goals
- AOD.
- A custom boot animation (needs root).
- Editing audio.
- Streaming or downloading chants.
- Lyrics.

## Anti-goals
- The viewer gains anything drawn over the picture.
- A chant starts loud.
- A chant plays during a call, ringing or an alarm. Audio focus must yield, and a chant pauses when
  focus is lost.
- A design that looks like a generic template.

## Calls I made (owner can override)

**Platform.** Stays native Kotlin with platform Views and zero added libraries.
- The animations are custom-drawn `View`s on Canvas, with `ValueAnimator` and transform/alpha only.
- Playback uses `MediaPlayer` in a foreground service, `foregroundServiceType="mediaPlayback"`, plus
  `MediaSession` and `Notification.MediaStyle`.

**New permissions.** These replace the v1 "no notifications" anti-goal, because the owner asked for
notification controls:
- `POST_NOTIFICATIONS`, asked at the first chant play;
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

There is still no internet permission.

**Restart detection.** It needs no boot permission. When the accessibility service connects within
3 minutes of boot (`SystemClock.elapsedRealtime()`), and the option is on, it opens the viewer once per
boot.

**Ghunghroo sound.**
- It is synthesized offline into a bundled `res/raw/ghunghroo.ogg`: about 12 layered short metallic
  partials with jittered timing and a decay of about 1 s. It is generated once with a script during
  the build work, so there are no copyright issues.
- The owner may replace it with their own recording later.

**Fades.** Fade-in is about 10 s and fade-out about 1.5 s. Both are equal-power curves set through
`MediaPlayer.setVolume` on a timer.

**Direction (Hallmark, adapted to Android).** Hallmark is written for web pages. Its rules carry over
in spirit:
- every colour and font is a token (`colors.xml` and theme attributes);
- one idea per screen;
- no tracked-caps labels;
- no emoji as icons;
- reduced-motion respected (`Settings.Global.ANIMATOR_DURATION_SCALE == 0` stills the animations);
- no invented content.

**Tone.** Devotional, warm and quiet: "temple at dusk".
- **Palette:** a near-black ground (#0E0A08), a deep vermilion signal (#B3261E-ish), turmeric gold
  accents (#D9A441) and a warm ivory text colour (#F3E9D8).
- **Type:** a display serif with Devanagari and Latin, "Tiro Devanagari Hindi" (OFL), and a body face,
  "Mukta" (OFL). Both are bundled from Google Fonts into `res/font`.

**Manage screen.** Sections in order: Her pictures → Chants → Shortcuts (each option with its
animation) → Setup checklist (only while incomplete).

**Logo (owner's choice).**
- It is the Linga Bhairavi yantra, redrawn as clean vector lines from the emblem in the owner's own
  photo: a square with four gates, a circle, a downward triangle and a small filled inner triangle.
- Colours: red (#B5121B) on saffron (#F2B536).
- It is an adaptive icon with a monochrome layer.
- It is for the owner's personal use. If the app is ever published or widely distributed, ask Isha
  Foundation first, because the yantra is their sacred symbol.

## Left to the builder
- Exact layout and spacing of the manage screen.
- The shapes of the thumb and phone illustration.
- Animation timing curves.
- The ghunghroo synthesis parameters.
- Thumbnail sizes.
- Unit tests for the pure logic: fade curves, the pairing lookup, the restart-once rule, and the
  policy input "chant playing → never consume".

## Process note
The owner asked to save tokens (/rescue-tokens). The builder check was done by me as a self-read, not
by a separate reviewer.

## Final decisions (2026-11-10, approved by the owner with the mockups in `design/mockup/`)

These replace anything above that conflicts with them.

- **Visual system:** Hallmark theme Kiln in the "reduction" colourway, as in `design/mockup/page.css`.
  - Crimson silk ground `oklch(21% 0.06 22)`.
  - Gold accent `oklch(84% 0.12 72)`, used only for the live chant, the primary control, "on"
    switches and the main picture.
  - Cream ink.
  - Fonts: Boska 500 for display (Fontshare, ITF Free Font License) and Geist 400/500 for body
    (OFL), both bundled.
  - 28 dp radius on pictures, 16 dp on wells, pills on controls.
- **Logo:** Isha's own Devi-eyes mark, extracted from `lingabhairavi-1.svg` by colour, with its
  geometry unchanged and only recoloured. It follows the phone theme:
  - day: the original red on ivory;
  - night: gold on night maroon;
  - Android 13+ themed icons: the monochrome eyes;
  - crimson: a choice in the app (activity-alias icon switcher).
- **No ghunghroo and no flute.** Closing the viewer fades the chant out over about 1.5 s.
- **Chants:** the five official channel videos and the Stuti were downloaded locally into
  `devi/chants/`, for the owner's personal use. They are never bundled in the APK. They are copied to
  the phone's Music/Devi folder and added through "Add chants".
- **How-to demos:** these follow the mockup.
  - The phone has a metal rim, glass, a camera hole and side buttons.
  - A shaded thumb with a nail presses each button. The button sinks 2 dp and flashes gold, a gold
    ripple spreads, and chips light in step.
  - The screen then wakes and she fades in. The loop is 6 s; reduced motion shows a still frame.
  - The power demo is power from the right, then volume from the left.
- **Interruptions (owner, later message):** if a call comes in, or a notification arrives while the
  phone is locked, the viewer closes at once and any chant fades out.
  - Calls are detected by the audio mode leaving `MODE_NORMAL` (ringtone or in call), or by an
    in-call window appearing.
  - Notifications are detected through the accessibility service's
    `TYPE_NOTIFICATION_STATE_CHANGED` events, so no new permission is needed.
