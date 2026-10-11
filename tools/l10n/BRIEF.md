# Translating TennaRC

TennaRC is a phone app that works as a TV remote (LG, Samsung, Roku, Android TV, Fire TV): a
D-pad and touchpad, an app grid, volume, a keyboard for typing on the TV, a search box that sends
links and searches to the TV, and settings. The English is in `app/src/main/res/values/strings.xml`;
the section comments there say where each group of strings shows up.

## The file

- Write `app/src/main/res/values-<qualifier>/strings.xml`, the qualifier from `tools/l10n/locales.txt`
  (e.g. `values-de`, `values-pt-rBR`, `values-b+sr+Latn`). UTF-8, starting with
  `<?xml version="1.0" encoding="utf-8"?>` and one `<resources>` element.
- Every `<string>` and `<plurals>` from the English file, same `name`, in the same order. Leave out
  the ones marked `translatable="false"`, and don't copy comments.
- `python3 tools/l10n/side.py <qualifier>` shows the English and the translation side by side, by section.
- `python3 tools/l10n/check.py <qualifier>` must print `ok <qualifier>`. It lists what's wrong.
  `python3 tools/l10n/check.py --missing <qualifier>` prints the English entries still to do.

## Must hold, or the app won't build or will crash

- Placeholders stay exactly as they are: `%1$s`, `%2$s`, `%1$d`. Move them where the grammar
  wants them, but keep every one, unchanged. Never add one.
- Apostrophes are written `\'` and double quotes `\"`. A string must not start with `@` or `?`
  (write `\@`, `\?`). `&` is `&amp;`, `<` is `&lt;`.
- Keep `…` (one character) where the English has it.
- `<plurals>`: give the quantities the language needs (CLDR: `zero`, `one`, `two`, `few`, `many`,
  `other`); always `other`. Languages without plural forms (Japanese, Chinese, Korean, Thai,
  Vietnamese, Indonesian…) have just `other`.

## Style

- These are labels on small buttons and one-line notes. Keep them as short as the English, or
  close: a German word twice as long won't fit a button. Prefer the words the language's own
  Android and smart-TV menus use (what Android calls "Settings", "Wi-Fi", "Pair", "Forget").
- Plain and friendly, no exclamation marks, no "please". Buttons are verbs ("Turn on", "Pair").
  Use the informal "you" where the language has one and phone apps normally use it (de: du,
  fr: vous is fine, es: tú, pt-BR: você, ru: вы).
- No full stop at the end unless the English has one.
- Notices and hints are whole sentences, with the articles and word order the language normally
  has, not clipped telegram style ("Der Fernseher hat nicht reagiert", not "TV nicht reagiert").
- One word for the TV throughout, the everyday one (German Fernseher, in compounds TV-), and one
  form of "you": don't mix du and Sie.
- Translate the meaning in this app, not the dictionary's first sense: "Listening…" is the
  microphone listening (Zuhören, not Abhören); "Keep screen on" keeps the phone's screen lit;
  "Power" is the on/off button.

## Leave these as they are

TennaRC, LG, webOS, Roku, Samsung, Android TV, Google TV, Fire TV, Philips, Sony, TCL, YouTube,
Netflix, Prime Video, Disney+, HBO Max, Spotify, Plex, Apple TV, HDMI, Ethernet, ADB, DLNA,
Miracast, AirPlay, Ambilight, Bluetooth, PIN, IP, MAC. Units ("2.4 GHz", "min" may be the language's
own abbreviation for minutes). Wi-Fi is what the language's Android settings call it (German WLAN,
most others Wi-Fi).

## What some short ones mean

- "Pair" / "Accept on the TV": connecting the phone to the TV the first time; the TV shows a prompt.
- "Forget": remove a saved TV and its pairing from the phone (as in Android's "Forget network").
- "In use": the TV the remote is controlling now.
- "Labs": experimental features in settings.
- "Demo TV": a pretend TV to try the app without one.
- "List" and "Guide" (more_channel_list, more_guide): the remote's Channel List and programme-guide
  (EPG) buttons. Use what remotes in that language print on those buttons.
- "Exit", "Menu", "Info", "Stop", "Subtitles", "Audio", "Inputs", "CH": remote-control buttons.
- "Input": a TV input (HDMI 1, PC…). "Channel": a broadcast TV channel.
- "Mirror": show the phone's screen on the TV. "Gallery": photos and videos on the phone.
- "Deck": the app grid at the top of the remote. "Touchpad", "Scroll strip": the pad that moves
  the TV's pointer and the strip at its edge that scrolls.
- "Screen off": turn off the TV's picture but keep sound. "Sleep timer": turn the TV off later.
- "Backlight", "Picture", "Sound", "Vivid", "Cinema", "Game", "Sports", "Eco": TV picture and sound
  modes; use the names TV menus in that language use.
- "Theme": Auto (dark after sunset), System, Light, Dark.
- "Hide", "Rename" (hold_hide, hold_rename): the menu that opens when you hold an app, channel or
  input on the remote. "Rename input" titles the box for an input's new name; "Use the TV's name"
  puts back the name the TV gives it. "Hidden" (settings_hidden) lists what's been hidden, each
  with "Show" to bring it back. "%1$s hidden" is the note after hiding one.
