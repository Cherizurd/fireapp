# ElevenReader for Fire TV

A small Fire TV / Android TV app that opens [elevenreader.io](https://elevenreader.io) full screen,
with an on-screen pointer you drive with the Fire TV remote.

## Remote controls

| Button | Action |
| --- | --- |
| D-pad arrows | Move the pointer (hold to speed up). Push against a screen edge to scroll. |
| Center / Select | Click (hold for a long press) |
| Back | Go back a page; press twice on the first page to exit |
| Menu (☰) | Toggle pointer mode / plain D-pad mode |
| Play/Pause | Play or pause the current audio |
| Rewind / Fast-forward (or Previous / Next) | Skip back / forward 15 seconds |

Audio keeps playing when the screensaver comes on. The media buttons work through the site's own player
controls (`app/src/main/assets/media-bridge.js`); if nothing is loaded yet, start a book on screen once.

## Building

Every push to `main` builds the APK with GitHub Actions. The APK is uploaded as a workflow
artifact and also committed to the `apk` branch as `ElevenReaderTV.apk`.

Local build (needs the Android SDK): `./gradlew assembleRelease`

## Installing on the Fire Stick

1. Fire TV: **Settings → My Fire TV → Developer Options → Install unknown apps** → enable **Downloader**.
   (If Developer Options is hidden: **Settings → My Fire TV → About**, click the device name 7 times.)
2. Put the APK somewhere the Fire Stick can download it (see below), open **Downloader**, enter the URL, and install.
3. The app shows up under **Apps** as **ElevenReader**.

Serving the APK from a computer on the same Wi-Fi:

```sh
cd folder-with-the-apk
python3 -m http.server 8000
```

Then in Downloader enter `http://<computer-ip>:8000/ElevenReaderTV.apk`.

## Signing in

The app presents itself as desktop Chrome so Google sign-in usually works. If Google still refuses
("this browser may not be secure"), sign in with email and password instead.
