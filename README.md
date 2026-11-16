# Bible Corner – KJT (Android)

The Android app of [knowingjesus.today](https://knowingjesus.today): Bible Corner in its own window, with
the Bible, listening (also with the screen off), the Verse of the day, sharing verses as pictures,
notifications, home-screen shortcuts and a widget. Package `today.knowingjesus.biblecorner`, Android 7.0 or newer.

- **main**: the app as it is given out (3.2). `source/` holds the manifest, `res/` (icons, words in 9 languages),
  `java/` (the app's own code), `js/` (the files kept inside the app) and the build scripts.
- **native**: the native screens (3.0/3.1), kept aside until each one is tested and does everything the app does.

The signing key is **not** in this repository and must never be added: the app can only be updated with the
same key, so it stays with the owner.
