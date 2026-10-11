# Android TV over ADB, beside the remote

What the developer connection is for, what works on which TVs, and where each claim comes from.
Written 2026-10-10 from AOSP source, other people's dumps and reports, and tests against fakes.
Nothing here was tried on a real TV. The family's TV is an LG, and nobody touches it for tests.
Confidence is marked (high), (medium) or (low).

## What it is

Android TV and Google TV are driven by Google's Android TV Remote protocol v2 (`AndroidTvBackend`,
`RemoteSession`). It sends keys, text and app links, and that's all. It can't list the TV's inputs
or apps, can't switch inputs on sets that ignore the HDMI keys (TCL), and can't touch the picture.

The developer connection is network ADB to the same TV, set up per TV in Settings, under the TV
in use. It sits beside the remote and never replaces it:

- Keys, typing and app links stay on the remote protocol.
- ADB adds the TV's own input list and input switching, its own app list, and the backlight on
  the two makers' sets where a shell can reach it.
- Its trouble never touches the remote's connection, never shows the TV as offline, and never
  holds up a key. `AndroidTvDevLinkTest` checks this against a real TLS remote session with
  adbd refusing, and with adbd hanging.

Code: `AdbLink` keeps the connection, `AndroidTvAdb` reads the TV over it, `AdbCommands` has the
commands and parsers, `AdbPorts` asks mDNS where adbd moved to, `DevLinkSheet` sets it up.

## Turning it on

| TV | Developer options | Toggle | Port 5555 |
|---|---|---|---|
| Google TV (Sony 2021+, TCL Google TV, Hisense Google TV, Chromecast with Google TV, Google TV Streamer) | Settings › System › About › Android TV OS build, seven times; then Settings › System › Developer options (high) | USB debugging | See below |
| Older Android TV (Sony 2019–20, TCL, Hisense, Xiaomi on 9/10/11) | Settings › Device Preferences › About › Build, seven times; then Device Preferences › Developer options (high) | USB debugging | Yes (high for TCL, medium for the rest) |
| NVIDIA Shield | Device Preferences › About › Build | Network debugging, which shows IP:5555 (high) | Yes |
| Philips | Settings › All settings › Android settings › About; the build item may be translated, e.g. "Compilation version" (medium) | USB debugging | Yes (medium). Home Assistant says Philips sets take the first connection only over Wi-Fi, and the prompt may hide behind video |
| Xiaomi Mi Box | As older Android TV | USB debugging | Mixed: the original Mi Box turns its USB port to device mode and needs `adb tcpip 5555` after every boot (low–medium) |

The TV then asks "Allow USB debugging?" for this phone. Ticking "Always allow from this computer"
keeps the phone's key, so it asks once. The app never connects unasked: it first checks whether the
TV already trusts the key without offering it (`AdbAuthProbe`), and only setting up from the sheet
lets the TV ask.

### Why some TVs can't, yet

adbd listens on plain TCP 5555 when a TCP port property is set, or when the TV has no USB gadget
for it to listen on (`daemon/main.cpp`, same from Android 9 to main) (high). That's why "USB
debugging" alone has opened 5555 on most TVs.

Android TV 14 changed this on several devices. After the 12→14 update, the Chromecast with Google
TV, the onn 4K and the Sony Bravia 8 lost 5555 and only take Android 11's Wireless debugging
(Home Assistant issue 148359 and forum threads) (high for CCwGTV, medium for the others). The
Google TV Streamer is mixed (medium). TCL's 2024 Google TV 14 sets are untested; assume Wireless
debugging only (low).

Wireless debugging pairs with a six-digit code over TLS (SPAKE2), then connects over TLS on a port
that changes every time it's turned on. dadb 2.0.0, which the app uses, speaks neither. Its
`AdbConnection` handles CNXN and AUTH and nothing else. So the app supports the plain-5555 path,
and tells the user when a TV only has the other:

- A plain CNXN to a TLS port gets a 24-byte STLS packet back (`53 54 4C 53`, version 0x01000000)
  (high, from `adb.cpp`). `AdbAuthProbe` turns that into `AdbTlsRequiredException`.
- adbd announces `_adb-tls-connect._tcp` over mDNS while Wireless debugging is on. When 5555 is
  closed and only that is announced, the app says Wireless debugging is the only thing on.
- Home Assistant users get round it by pairing once from a computer, then running
  `adb -s <ip>:<port> tcpip 5555`. Plain 5555 then works until the TV restarts (high).
- Wireless debugging also refuses Ethernet: it needs a Wi-Fi BSSID, and turns itself off when the
  Wi-Fi network changes (`AdbDebuggingManager`) (high).

A later step could add Kadb (Apache-2.0, minSdk 23, Conscrypt), which pairs and connects over TLS
(medium; not evaluated). Keys trusted over 5555 also work over TLS when the client makes its TLS
certificate from the same RSA key (high from adbd's source, untested).

### Keys expire

Since Android 11, a key allowed with "Always allow" is dropped if it hasn't connected for
`adb_allowed_connection_time`, a week by default (high, `Settings.java`). A TV whose remote hasn't
been opened for a week forgets the phone. The link then shows "The TV forgot this phone" and asks
again only when told to. Setting that value to 0 on the TV stops the expiry; that's the owner's
call, so the app doesn't do it.

One key serves the whole phone (`AdbKeys`, in the app's files, shared with Fire TV).

## How the link behaves

- **Lazy.** It starts 1.5 s after the remote connects, so the remote's own connecting goes first,
  and only for a TV it's set up for. It doesn't knock while the remote can't reach the TV either.
- **Kept alive.** While the remote is on screen it runs a cheap `echo` every 45–50 s, so a TV that
  slept or changed networks is noticed then, not by the next thing that needs it.
- **Outlasts blips.** It lets go 30 s after the remote does, so the remote dropping and coming
  back doesn't cost it. A single failure retries quietly as still up; a second says it's down.
- **Backoff.** 2, 4, 8 … 64 s, then every 2 minutes, with jitter. Something that needs the link
  (switching an input, the backlight) skips the wait and waits at most 5 s for it.
- **Time-boxed.** Every call has its own limit (6 s; 10 s for the app list). A hung adbd costs
  that call and the connection, which is rebuilt; DadbShell closes the socket under a read that
  won't end.
- **Port moved.** When the saved port turns it away, it asks mDNS once per run of failures for
  `_adb._tcp` (plain) and `_adb-tls-connect._tcp` (TLS) at the TV's address, and follows a moved
  plain port.
- **Lists cached.** The phone keeps the last good app and input lists per TV (`KnownApps`,
  in `tv-adb`), so they're there from the first frame and don't flicker as the link comes and
  goes. The backlight shows only while the link is up.

## Inputs

### Listing them: `dumpsys tv_input`

The format is the same from Android 9 to 15 (high, `TvInputManagerService.dump()` and
`TvInputHardwareManager.dump()`):

```
User Ids (Current user: 0):
UserState (0):
  inputMap: inputId -> TvInputState
    <id>: info: TvInputInfo{id=<id>, pkg=<pkg>, service=<class>}; state: <0|1|2>
TvInputHardwareManager Info:
  mHardwareList:
    TvInputHardwareInfo {id=5, type=9, audio_type=…, audio_addr=, hdmi_port=1, cable_connection_status=0}
  mHardwareInputIdMap: deviceId -> inputId
    5: com.mediatek.tvinput/.hdmi.HDMIInputService/HW5
```

What the app makes of it (`parseTvInputs`):

- Only the current user's block: Google TV profiles add users 10 and up.
- `TvInputInfo.toString` has no type or label in any version. The type and HDMI port come from
  joining `mHardwareInputIdMap` with the hardware list. Only devices in that map count: a MediaTek
  Philips lists spare hardware (14–17, ports 1–4 again) that has no input.
- Ids: AOSP makes a hardware input `<component>/HW<device>`, an HDMI-CEC device behind a port
  `<component>/HDMI<4 hex physical><2 hex logical>`, and an app's own input just `<component>`.
  CEC devices and app inputs are left out.
- A TV that goes around the hardware manager leaves only ids. An HDMI one says so in its service's
  name (`Hdmi2InputService`); the rest are called "Input 1, 2…" in the order of their hardware.
  The HW numbers aren't ports: TCL's Realtek sets pack source ids into them (`HW1413744128` is
  0x54440600).
- Names: the TV's own renames from `settings get secure tv_input_custom_labels` (pairs of
  URI-encoded id and label, `id,label:id,label`), else "HDMI n", "AV", "Component"…, else the
  service's name for type 1 (a soundbar's ARC, AUX, SPDIF). Default labels can't be read from a
  shell (they need PackageManager) (high).
- Hidden inputs (`tv_input_hidden_inputs`, URI-encoded ids joined by `:`) are left out.
- Plugged in: only state 2 (DISCONNECTED) counts as unplugged. MediaTek and Amlogic sets report 1
  (connected, standby) for idle ports (medium).
- The first tuner becomes "TV", last in the list: people want "back to TV", and the passthrough URI
  opens the tuner too (a Philips owner did exactly that).
- None found (a streamer; a TV whose inputs go around Android's TV framework) keeps the remote's
  HDMI keys.

The parser tests use three real dumps from the Home Assistant forum (a MediaTek Philips, an
Amlogic JBL soundbar, an XGIMI projector), in `app/src/test/resources/androidtv`.

Input ids seen in the wild (from the same thread and python-androidtv issues):

| Maker | Ids | Confidence |
|---|---|---|
| MediaTek reference (Philips 2019–21) | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5…HW8` = HDMI 1–4, tuner `.tuner.TunerInputService/HW0` | high |
| Philips 2023 | `com.mediatek.tis/.HdmiInputService/HW2…HW5` | high |
| Amlogic (TCL S434, Mi TV, JBL) | `com.droidlogic.tvinput/.services.Hdmi{1,2,3}InputService/HW{5,6,7}` | high |
| TCL, newer MediaTek | `com.tcl.tvinput/.TvPassThroughService/HW15…HW18` = HDMI 1–4 | high |
| TCL, Realtek | `com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744128` (HDMI 1), tuner `.TDTVInputService/HW1413742848` | medium |
| Sony | `com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2…HW5` = HDMI 1–4 | high |
| Hisense | `com.vt.source.external/.hdmi.HdmiTvInputService/HW2…HW5`; older `com.hisense.tv.hitvinput/.hdmi.HdmiTvInputService/HW4` | high |
| Shield, Chromecast, Google TV Streamer | No inputs of their own | high |

### Switching to one

The URI the TV's own input list opens (`TvContract.buildChannelUriForPassthroughInput`):

```
am start -n <handler> -a android.intent.action.VIEW -d 'content://android.media.tv/passthrough/<Uri.encode(id)>'
```

- The id must be one encoded path segment (`/` as `%2F`). TvProvider only knows the type of
  `passthrough/*` with one segment, and without the type nothing takes the intent: "unable to
  resolve Intent" (high). Every "keep the %2F" report in the Home Assistant thread is this.
- The handler is asked first, by the type TvProvider gives:
  `cmd package query-activities --components -a android.intent.action.VIEW -d content://android.media.tv/passthrough/x -t vnd.android.cursor.item/channel`.
  A TV with two live TV apps (its maker's and Google's Live Channels) would otherwise put up a
  chooser. The maker's goes before Google's.
- Seen handlers: Philips `org.droidtv.playtv/.PlayTvActivity`, Sony `com.sony.dtv.tvx/.MainActivity`,
  TCL `com.tcl.tv`, AOSP `com.android.tv/.MainActivity` (high to medium).
- `am start` exits 0 either way; its output says whether it started (`Error:` lines don't).
- With the link out of reach, the same URI goes as an app link over the remote protocol, which
  the TV resolves the same way; a TCL C7K owner switched inputs like that (medium). Only when the
  last ask found a single handler, so it can't put up a chooser.
- `input keyevent KEYCODE_TV_INPUT_HDMI_1` isn't handled by AOSP's PhoneWindowManager, only by a
  foreground app or a maker's key handler; it failed on a Philips 70PUS9005 and TCL ignores it
  (medium). Hence the URI.
- Which input is on now (the main session's `inputId` in the same dump; `mResourceClientProfile`
  on 11+) isn't read yet.

## Apps

`cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER`
lists one `package/activity` per line. `--components` has been there since Android 7.0, so every
Android TV 9+ has it (high, `PackageManagerShellCommand` history).

No shell command gives an app's label (high); names come from a table (python-androidtv's
constants and Play listings), else from the package. Google TV's home screens, setup, screensaver,
assistant and cast receiver are left out. Launching stays on the remote protocol
(`market://launch?id=`).

## Picture and sound

Nothing works across makers (high, after a long look):

- `settings put system screen_brightness` and `cmd display set-brightness` drive the display
  framework, not a TV panel's backlight, which goes through the maker's own picture code (low,
  inference; no report either way).
- Android 16's MediaQualityManager has no shell command and no shipping device (high).
- Sound output (speakers, ARC, optical, Bluetooth) has no shell path on any maker's set. The only
  output setting a shell changes is `encoded_surround_output` (high). HDMI-CEC settings moved out
  of Settings.Global in Android 13; `cmd hdmi_control` (12+) may do nothing on sets whose CEC runs
  in the maker's middleware.

What makers' sets have, and what the app does with it:

| Maker | Way | Seen working | App |
|---|---|---|---|
| Xiaomi, other MediaTek reference software | `settings get/put global picture_backlight` 0–100; a service in the TV's settings app watches and applies it live. Told apart by `tv_picture_video_adaptive_luma_control`, which only that software writes | Yes, Xiaomi (the "Picture Settings for Android TV" app) (medium). Other makers on that software untested | Backlight |
| TCL | `service call tcl_tv_display 101` reads (`Parcel(00000000 00000032)` is 50), `… 100 i32 0 i32 <0–100> i32 0` sets (transactions from `com.tcl.tv.display.jar`) | Yes, 55S450G and 43C645 (medium). Another model's SELinux denied the shell | Backlight, only once a level reads back |
| Sony | No ADB path. REST: `POST /sony/video setPictureQualitySettings` and `/sony/audio setSoundSettings` (outputTerminal), with a pre-shared key set on the TV | Documented for BRAVIA Pro, used by pybravia (medium) | Nothing yet |
| Philips | JointSPACE v6 `menuitems/settings/…`, node ids differ per set | pylips uses it for Ambilight brightness (low–medium) | Nothing yet (JointSPACE already pairs for Ambilight) |
| Hisense | Nothing found | | Nothing |

MediaTek's `picture_mode` takes numbers (7, 3, 2, 9, 0 were reported as default, bright, sport,
movie, user) but that comes from one app's source for Xiaomi sets and couldn't be checked, so the
app doesn't offer picture mode. Writing a settings key a TV doesn't watch does nothing and reads
back as written, which would show a level the TV isn't at; hence the luma key check.

## Open

- Wireless debugging (Android TV 14 Google devices): needs a TLS client and SPAKE2 pairing, e.g.
  Kadb.
- Sony picture and sound over its REST API; Philips over JointSPACE.
- The input in use, from the main session in `dumpsys tv_input`.
- TCL Realtek sets that go around the hardware manager: their AV and HDMI sockets share one
  service and show as "Input 1, 2…". The packed source ids (0x…0600 HDMI 1, 0x…0200 AV) could name
  them, from one owner's list (low).
- Everything here wants a check on a real set, which this phone hasn't had.

## Sources

- AOSP, android.googlesource.com: frameworks/base `services/core/java/com/android/server/tv/TvInputManagerService.java`,
  `TvInputHardwareManager.java`, `media/java/android/media/tv/TvInputInfo.java`, `TvContract.java`,
  `services/core/java/com/android/server/pm/PackageManagerShellCommand.java`,
  `services/core/java/com/android/server/am/ActivityManagerShellCommand.java`,
  `services/core/java/com/android/server/adb/AdbDebuggingManager.java`, `core/java/android/provider/Settings.java`
  (tags android-9.0.0_r61 to android-15.0.0_r36); packages/modules/adb `daemon/main.cpp`, `adb.cpp`,
  `adb_mdns.h`, `daemon/mdns.cpp`; packages/providers/TvProvider `TvProvider.java`;
  packages/apps/TvSettings `development_prefs.xml`, `InputOptionsFragment.java`;
  hardware/interfaces `tv/input/1.0/types.hal`.
- Home Assistant: "Android TV HDMI" thread, https://community.home-assistant.io/t/android-tv-hdmi/234411
  (the three dumps, and the per-maker commands); core issue 148359 (Android 14 and Wireless
  debugging); https://www.home-assistant.io/integrations/androidtv/.
- python-androidtv, https://github.com/JeffLIrion/python-androidtv (constants, issues 202 and 227).
- TCL: https://gist.github.com/det-peralta/eab8c1ffbc0263c9dcd9359ccbdedd45,
  https://github.com/mojothemonkey2/TCL_Guide.
- MediaTek keys: https://github.com/alexrcq/picture-settings-for-android-tv (`MtkGlobalKeys.kt`).
- Sony REST: https://pro-bravia.sony.net/remote-display-control/rest-api/reference/.
- Shield: https://blog.ktz.me/nvidia-shield-adb-projectivy-performance-refresh/.
- Philips: https://blog.codetitans.pl/post/howto-enable-android-developer-options-on-philips-tv/.
- Google TV Streamer: https://allaboutchromecast.com/using-adb-on-google-tv-streamer/.
- dadb 2.0.0 sources (no STLS); Kadb, https://github.com/flyfishxu/Kadb.
