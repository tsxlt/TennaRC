# TennaRC

Simple universal TV remote with advanced search. Android 8 and up; works with LG (webOS),
Samsung, Roku, Android TV and Google TV, and Fire TV, on the same Wi-Fi as the phone.

- D-pad and touchpad, volume (the phone's buttons too), media keys, and the TV's own buttons.
- Your apps, inputs and channels at the top of the remote, in the order you use them. Hold one to
  hide it, or to rename an input.
- Search for a show or film and open it in the app that has it; send links, or share them from
  any app. Videos and photos from the phone play on the TV.
- Type on the TV from the phone's keyboard.
- Picture, sound, sleep timer and screen off, where the TV allows them.
- A widget, a Quick Settings tile and controls in the notification shade.
- Finds TVs on the network, pairs only when you tap Pair, and reconnects on its own.
- Light and dark, or dark after sunset.

Site and privacy policy: [tenna.tsx.lt](https://tenna.tsx.lt). Problems go to
[tenna.tsx.lt/report](https://tenna.tsx.lt/report); crash reports are sent there only when you tap Send.

Download the APK from [Releases](https://github.com/tsxlt/TennaRC/releases/latest).

## Build

Everything comes from the flake (JDK 21, Android SDK 37, adb):

```bash
nix develop -c ./gradlew assembleDebug
```

Tests, including the Robolectric screenshots written to `app/build/shots/`:

```bash
nix develop -c ./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```

CI tests every push and pull request. Pushes build the signed APK and app bundle (`release`
artifact); a `v*` tag also publishes them as a release, with the tag's message as its notes.

## Install over Wi-Fi

Pair once with Wireless debugging. The helper shows a QR code, finds the phone over mDNS and
connects (nixpkgs' adb has no mDNS support of its own):

```bash
python -m venv .venv && .venv/bin/pip install bscpylgtv zeroconf
nix develop -c .venv/bin/python tools/adb-qr-pair.py
```

Then:

```bash
nix develop -c ./gradlew installDebug
```

On Xiaomi phones, turn on "Install via USB" in Developer options first.

## Translations

Strings live in `app/src/main/res/values*/strings.xml`. `tools/l10n/BRIEF.md` is the brief for
translators and `tools/l10n/check.py` checks a translation.

## From a computer

`tools/tv` wraps bscpylgtv to drive an LG TV from the shell: `TV_IP=<address> tools/tv open
youtube.com`, `tools/tv off`, `tools/tv volume 12`, or any bscpylgtv command such as
`tools/tv button HOME`.

## License

GNU General Public License v3.0 only; see [LICENSE](LICENSE). Anyone who ships TennaRC, changed
or not, must publish the full source under the same license.

The license doesn't cover the TennaRC name or icon; a fork needs its own.
