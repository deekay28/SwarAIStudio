# Privacy and data handling

**On the phone**
- Lyrics, projects, presets and audio are stored in app-private storage. No analytics, ads or crash reporters.
- Android backup and device transfer are disabled, so data and keys are never copied to Google Drive.
- Credentials you type (server token, DeepInfra key, companion key) are encrypted with an AES-256-GCM key held in the
  Android Keystore. They are never logged or exported.
- Imported files are checked: lyrics must be UTF-8 text ≤ 512 KB; audio must have a recognised audio signature.
- Exports go only where you choose in the system file picker. The app requests no storage, microphone or location
  permission; only INTERNET (plus WorkManager's network-state permissions).

**When you press Generate (and only then)**
- Sent: title is *not* sent; the style description, lyrics, language code, duration, BPM/key, number of candidates.
- Destination is shown on the confirmation dialog: DeepInfra, your SwarAI server (then its provider), or your PC.
- HTTPS is enforced for everything except private-network addresses (for your own PC).

**SwarAI backend**
- Logs job ids, provider and lyric *length*, never lyric text.
- Keeps generated audio in a temp directory only until the app downloads it (the app then deletes it) or for 1 hour.
- Jobs are visible only to the token that created them.

**Third parties**: DeepInfra and fal.ai process requests under their own privacy policies and terms.
