# Moment

Moment is a deterministic, offline Android film prop for this production. It recreates only the shots the film needs: mixed video and image-carousel browsing, search results, chats, timed incoming messages and notifications, voice-message playback, and a staged uninstall that ends on a black screen.

It does not connect to Instagram, require a real account, or use a backend. Use only production-owned or properly licensed names, images, audio, and video.

The installed app is named **Moment** and uses a dark interface. The Android package name and the existing VS Code task names still use the older `com.tyler.scenegram` / `Scenegram:` development identifiers; those are not shown as the app name on camera.

## Codex project context — read this first

This section is the engineering handoff for future Codex chats. Treat it as the current source of truth before planning or editing. Confirm the relevant implementation before making changes, preserve unrelated user work, and update this section whenever behavior or architecture materially changes.

### Product intent and decisions that must be preserved

- Moment is a purpose-built, offline film prop—not a social network, Instagram client, learning exercise, or production backend.
- Speed, deterministic playback, easy on-device programming, and a convincing filmed result matter more than general-purpose architecture.
- The visible app name is **Moment**. Keep the existing application ID and Kotlin package, `com.tyler.scenegram`, unless the user explicitly requests a migration; retaining it allows debug APK updates to preserve device data.
- The app is portrait-only and globally dark. The current palette intentionally uses a warm near-black background (`#151219`), dark card surfaces (`#17151C`), lavender accent (`#B99CFF`), and selected containers close to `#4A4458`. Do not replace it with dynamic/system colors.
- The filmed bottom navigation has exactly four icon-only destinations: Reels, Search, Inbox, and Me. There is no Feed tab and no text under the icons.
- Reels is one vertical pager mixing placeholder/imported videos with the image posts and horizontal carousels formerly associated with Feed.
- Search has exactly two programmable collections: the default collection for a blank query and a second collection for every nonblank query. When a result is opened, the search field and query remain visible and editable.
- Stories live at the top of Inbox. Me opens **My Account**. The old settings icon/page must not return.
- Handles are derived from display names with `momentHandle`: lowercase words joined by dots and prefixed with `@` (`Alex Morgan` → `@alex.morgan`).
- Director controls are hidden from filmed navigation and open after five quick taps on the **Moment** title on Reels.
- The ordinary chat UI must never expose Director information. In particular, it must not show actor-line guides, prefilled actor dialogue, reply countdowns, “waiting for…” labels, scene names, or other staging cues.
- Chat-scene steps contain only an incoming reply, its type, and the delay after the app user sends any nonblank message. There is deliberately no actor-line-guide field. Do not reintroduce it.
- The staged uninstall is deliberately fake. It displays an in-app confirmation and then a full black screen; it must not invoke Android's actual package uninstaller.
- One earlier request arrived truncated as `change the…`. Nothing was inferred or implemented for that unknown item. Ask the user rather than guessing if it becomes relevant.

### Current implementation status (verified September 1, 2026)

The following flows have been implemented and exercised on the A51-like emulator and the connected Galaxy A51 test phone:

- mixed vertical videos and horizontally swipeable carousels;
- imported phone images copied into private storage and rendered after app restarts and APK updates;
- blank-query and nonblank-query Search collections, including retained editable query text in the opened-post view;
- Inbox stories and persistent custom chats with profile metadata and ordered initial history;
- reusable saved/deletable chat scenes with optional delayed initial incoming messages;
- app-user-send-triggered text or playable voice replies with programmed delays;
- Android message notifications routed to the correct chat;
- voice playback using Android's on-device text-to-speech engine and the programmed visible duration;
- fake uninstall confirmation, persistent blackout, and lower-left three-second recovery gesture;
- clean filmed chat behavior with no actor hints or reply-timing indicators;
- the production icon artwork, extracted from the supplied Moment SVG mockups into individually named Android vector resources for navigation, search, post actions, sending, and playback.

The latest full verification command completed successfully with 11 JVM unit tests, zero failures, and Android lint passing. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Technology and build configuration

- Native Android app written in Kotlin with Jetpack Compose and Material 3.
- Android Gradle Plugin 9.3.1, Kotlin 2.4.10, Compose BOM 2026.08.00, coroutines 1.11.0.
- Java/JVM 17, `compileSdk = 37`, `targetSdk = 37`, `minSdk = 26`.
- No server, Firebase project, login, database service, or network permission is used.
- Media selected through Android's Storage Access Framework is copied into app-private storage; the app does not keep depending on the original picker URI.
- Imported videos use an Android `VideoView`; imported images are sampled, EXIF-oriented, and rendered with Compose. Voice messages use Android `TextToSpeech` rather than imported audio files.

### Code map

| File | Responsibility |
| --- | --- |
| `app/src/main/java/com/tyler/scenegram/ui/SceneGramApp.kt` | Root Material theme, screen routing, icon-only navigation, Inbox/chat/account UI, uninstall confirmation, and blackout/recovery UI. |
| `app/src/main/java/com/tyler/scenegram/ui/MomentPosts.kt` | Unified Reels/Search presentation, mixed built-in and custom posts, vertical/horizontal pagers, media rendering, thumbnails, avatars, count formatting, and the visual palette accent. |
| `app/src/main/java/com/tyler/scenegram/ui/DirectorScreen.kt` | Scrollable on-device post/chat/chat-scene editor, media pickers, saved-item summaries, scene start/delete controls, and notification tools. |
| `app/src/main/java/com/tyler/scenegram/director/AppViewModel.kt` | App state, navigation, hidden-title gesture, runtime chat messages, scene sequencing, delay jobs, TTS playback, persistence coordination, notifications, and blackout state. |
| `app/src/main/java/com/tyler/scenegram/director/MomentContent.kt` | Persistent domain models and invariants for posts, chats, message kinds, and reply-only scene steps; also contains handle generation and default chats. |
| `app/src/main/java/com/tyler/scenegram/director/MomentContentStore.kt` | Versioned JSON encoding/decoding in SharedPreferences plus safe import/deletion of private media copies. Invalid individual JSON entries are skipped; old scene JSON may contain an ignored `actorLine` field. |
| `app/src/main/java/com/tyler/scenegram/notifications/NotificationController.kt` | High-importance **Moment messages** notification channel, message notifications, and chat-ID deep links. |
| `app/src/main/java/com/tyler/scenegram/model/PlaceholderContent.kt` | Built-in placeholder profiles, videos, image posts/carousels, and the legacy rehearsal content. |
| `app/src/main/java/com/tyler/scenegram/MainActivity.kt` | Compose host and notification-intent forwarding. |
| `app/src/main/res/drawable/ic_moment_*.xml` | Individually named, scalable Moment icon vectors used by Compose instead of placeholder text glyphs. |
| `app/src/test/java/com/tyler/scenegram/director/MomentContentTest.kt` | Domain invariants for handles, post media, ordered scene replies, and voice durations. |
| `.vscode/tasks.json` | Shared build, test, install, launch, emulator, notification, screenshot, and logcat tasks. |
| `scripts/*.ps1` | A51 AVD creation/start, screenshot capture, and deterministic emulator smoke test. |

There are older internal names such as `SceneGramApp`, `SceneGramApplication`, `Theme.SceneGram`, `FeedPost`, and `Scenegram:` task labels. They are implementation identifiers only. Do not perform a risky package-wide rename merely for cosmetic consistency.

### Runtime and persistence model

- Director content is stored as schema-versioned JSON under SharedPreferences file `moment_director_content`, key `content_v1`.
- Imported media is copied to the app-private `files/moment-media` directory. Deleting a custom post deletes its copied post media/avatar; deleting a custom chat deletes its copied avatar and all scenes assigned to it.
- Blackout state is stored separately in SharedPreferences file `scenegram_director`, key `blackout`, so the black screen survives ordinary activity/app restarts.
- Posts, chats, chat definitions, scenes, and imported media persist through normal restarts and `adb install -r` APK updates.
- Runtime messages delivered while playing a scene are intentionally in memory. Restarting the process restores a chat's programmed initial history, not the completed take.
- Timed cues use `viewModelScope` coroutine jobs. They require the process to remain alive and do not survive force-stop, actual uninstall, or Android process death. Keep Moment open/foregrounded for a take.
- Starting a saved scene cancels existing cue/TTS work, clears active notifications, resets the chosen conversation to its programmed initial history, optionally schedules the initial incoming message, and arms reply step zero.
- Each nonblank app-user send advances exactly one armed step. Sends from a different chat cannot advance the active scene. While a reply is pending, another send does not consume another step.
- A reply can be text or voice. Voice `text` is the phrase TTS speaks; `durationSeconds` controls the displayed/progress duration. The notification preview says `Voice message · 0:SS` rather than exposing the spoken text.
- Starting/deleting/cancelling a scene updates Director status only. The filmed chat surface contains no scene status or timer UI.

### Safe workflow for future changes

1. Read this handoff and the relevant files before editing; do not assume the old Feed/actor-guide design still exists.
2. Preserve the package/application ID and persistent JSON compatibility unless the user explicitly authorizes a migration.
3. Treat media and SharedPreferences already present on the emulator/phone as user data. Do not clear app data as a routine test step.
4. Use `adb install -r` or the provided install tasks to preserve programmed content.
5. After model changes, verify old stored JSON still loads. Optional fields need defaults, and obsolete fields should be ignored safely.
6. For chat-scene changes, test both text and voice replies, the no-hint filmed chat, notification routing, cancellation, and repeated scene starts.
7. For visual changes, inspect a real emulator screenshot; Compose previews alone do not reproduce the A51-like density.
8. Finish with `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`.

### Known boundaries and likely future work

- Placeholder names, profile pictures, images, and videos are expected to receive a separate visual-content pass.
- Search intentionally maps all nonblank queries to one alternate programmed set; it is not a real text-search engine.
- Voice messages are synthesized from programmed text. Importing prerecorded audio is not currently implemented.
- There is no Director content export/import, backup, cloud sync, or cross-device transfer. Clearing storage or genuinely uninstalling destroys programmed content.
- The emulator approximates A51 geometry and Android version but cannot reproduce Samsung One UI, AMOLED characteristics, the camera cutout, notification shade, keyboard, or Exynos/Mali behavior.
- Pending delayed cues are not durable background alarms. A future request for process-death-safe scheduling would require a deliberate design change such as WorkManager or AlarmManager.

## App layout

The bottom bar contains four icon-only tabs:

- **Reels** — a vertical feed that mixes looping video posts with image posts. Swipe vertically to change posts; swipe horizontally inside a multi-image post to change carousel images.
- **Search** — shows one programmed set while the search field is empty and another set whenever any search term is entered. Imported posts can be assigned to either set. Opening a result keeps the search field and its current text visible and editable.
- **Inbox** — shows story circles at the top and the saved chats below. Tapping a notification opens its associated conversation.
- **Me** — opens **My Account** and contains the staged **Uninstall app** control.

User handles are derived automatically from the profile name: the name is lowercased, word separators become dots, and `@` is added. For example, `Alex Morgan` becomes `@alex.morgan`.

## Opening the hidden Director controls

1. Open the **Reels** tab so the title says **Moment**.
2. Tap **Moment** five times quickly. Do not pause for more than about 1.5 seconds between taps.
3. The **Director controls** screen opens. Use its back arrow to return to the app.

The Director screen is intentionally hidden from the bottom navigation so it will not appear in filmed app use.

## Adding posts from the phone

In **Director controls**, scroll to **Add a post**:

1. Choose the destination:
   - **Reels** adds the post to the mixed vertical Reels feed.
   - **Search: default** adds it to the no-query Search grid.
   - **Search: after typing** adds it to the results shown for any non-empty query.
2. Choose either **Choose pictures** or **Choose video**. Selecting multiple pictures creates one horizontally swipeable carousel; a video selection creates one looping video post.
3. Enter the profile name. The `@handle` preview is generated automatically.
4. Optionally choose a profile picture and enter a caption.
5. Enter the visible numbers of likes and comments.
6. Tap **Import and save post**.

Moment copies selected files into its private app storage, so the source file does not need to remain in the same phone folder after the import. Saved posts are listed in the Director screen and have a **Delete** action.

## Adding chats

In **Director controls**, scroll to **Add a chat**:

1. Enter the fake person's profile name and optionally choose a profile picture.
2. Under **Initial chat history**, choose **Fake person** or **App user**, then choose **Text** or **Voice message**. For a voice message, enter the words the device should speak and its visible duration. Tap **Add history message**.
3. Repeat step 2 in the order the messages should appear when the conversation opens. Individual draft history rows can be deleted before the chat is saved.
4. Tap **Save chat**.

The new chat appears in the Inbox and can be selected when creating a scene. Saved chats are listed with a delete action. Moment always keeps at least one chat available; deleting a chat also deletes scenes assigned to it.

## Programming and playing a chat scene

In **Director controls**, scroll to **Create a chat scene**:

1. Enter a scene name and select the chat in which it should run.
2. If the scene should begin with an unsolicited message, enable **Start with a delayed incoming notification**, enter that message, select text or voice, and set its delay. Voice messages also have a visible playback duration.
3. Add the reply flow one step at a time:
   - **Incoming reply** is the fake person's scripted response. It can be text or a playable voice message; voice content is spoken by Android's on-device text-to-speech engine.
   - **Reply delay** is the number of seconds between the app user tapping Send and that response arriving.
4. Tap **Add reply step**, then repeat for every send/reply exchange.
5. Tap **Save chat scene**.

Saved scenes remain listed in the Director screen with **Start** and **Delete** controls. Pressing **Start** opens the selected chat and resets it to that chat's saved initial history.

During playback:

- If an initial incoming message was enabled, Moment waits for its delay, inserts the message, and posts an Android notification.
- The composer remains completely normal and empty. Any non-empty message the app user sends advances the current scene step.
- After the programmed delay, the scripted text or playable voice reply appears and an Android notification is posted.
- Each subsequent message sent advances the next reply step. Nothing in the normal chat screen reveals the script or the remaining delay.

The Director screen also includes quick preparation buttons, a built-in text-and-voice rehearsal cue, cue status, cancellation, notification permission, and a test-notification control.

## Persistence and continuity

Imported posts, chats, chat scenes, and their copied media persist across normal app restarts and debug APK updates. They are stored only on that Android device or emulator; there is currently no cloud sync or export function.

**Clear App Data**, Android's real **Clear storage**, or actually uninstalling Moment deletes the saved Director content and imported copies. Use a clean-data reset only when that is the intended continuity reset. A normal reinstall/update from the provided tasks preserves the data.

## Development setup in VS Code

This checkout is configured for:

- JDK 17 at `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- Android SDK at `C:\Users\Tyler\AppData\Local\Android\Sdk`
- the repository's Gradle wrapper, `gradlew.bat`

`local.properties` points Gradle to the local Android SDK and is intentionally ignored by Git because it is machine-specific. A separate global Gradle installation is not required.

Open the repository root in VS Code. The Java and Kotlin extensions may already be installed; VS Code can use the checked-in tasks without reinstalling them. Choose **Terminal > Run Task** or **Tasks: Run Task** from the Command Palette.

| VS Code task | Purpose |
| --- | --- |
| `Scenegram: Build Debug` | Builds the debug APK. |
| `Scenegram: Unit Test` | Runs the debug JVM unit tests. |
| `Scenegram: Lint Debug` | Runs Android lint for the debug variant. |
| `Scenegram: Install Debug` | Builds and installs on the single connected Android target. |
| `Scenegram: Launch` | Opens `com.tyler.scenegram/.MainActivity`. |
| `Scenegram: Install + Launch` | Installs and then launches on a connected phone. |
| `Scenegram: Clear App Data` | Deletes Moment's data on the connected target. |
| `Scenegram: Grant Notification Permission` | Grants Android 13+ notification permission through ADB. |
| `Scenegram: Logcat` | Shows logs from the running app process. |
| `Scenegram Emulator: Build + Install + Launch` | Starts the A51 emulator, builds, installs, grants notifications, and launches Moment. |
| `Scenegram Emulator: Cold Boot A51` | Starts the A51 profile without its quick-boot snapshot. |
| `Scenegram Emulator: Clear App Data` | Deletes Moment's emulator data. |
| `Scenegram Emulator: Capture Screenshot` | Saves the emulator display under `captures/` for viewing in VS Code. |
| `Scenegram Emulator: Stop` | Shuts down the emulator cleanly. |

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

For a full command-line verification in the VS Code terminal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Testing without the phone: Galaxy A51 emulator

Run **Terminal > Run Task > Scenegram Emulator: Build + Install + Launch**. The first boot is slower; later starts use a quick-boot snapshot. The emulator is an interactive window beside VS Code. To inspect a still frame inside VS Code, run **Scenegram Emulator: Capture Screenshot**.

The checked-in virtual-device profile approximates the LTE Galaxy A51 (`SM-A515` family):

- Android 13 / API 33, the A51's final official major Android version
- 1080 × 2400 portrait display at 420 logical DPI
- 6.5-inch, 20:9 screen geometry
- 4 GB RAM, four virtual CPU cores, and 8 GB of emulator data storage
- 60 Hz display behavior

This is a practical layout and interaction approximation, not Samsung firmware. Google's emulator uses stock Android and does not reproduce One UI 5.1, Samsung's notification shade or keyboard, the camera cutout, AMOLED rendering, or Exynos/Mali performance. Verify those details on the filming handset before the shoot.

## One-time physical-phone setup

The following steps require the filming phone and cannot be completed by the app:

1. In **About phone**, tap **Build number** seven times to enable Developer options.
2. Enable **USB debugging** in Developer options.
3. Connect the phone with a data-capable USB cable. Install Samsung's Windows USB driver if ADB does not detect it.
4. Unlock the phone and approve **Allow USB debugging**. Choose **Always allow from this computer** for a stable filming workflow.
5. In the VS Code terminal, run `adb devices`. The phone must appear as `device`, not `unauthorized` or `offline`. Disconnect other Android targets before using the non-emulator install tasks.
6. Run `Scenegram: Install + Launch`.
7. On Android 13, accept Moment's notification permission prompt or run `Scenegram: Grant Notification Permission`.

Clearing app data removes the notification grant, so grant it again after a reset. Before a notification take, turn up notification volume, disable Do Not Disturb, and confirm the **Moment messages** notification channel is enabled in Android Settings.

Moment's cues use local Android notifications. They look and behave like ordinary notifications for filming but remain offline and deterministic: no Firebase account or internet connection is required. Force-stopping or genuinely uninstalling the app cancels active cues.

## Recommended rehearsal workflow

1. Build, install, and launch Moment on the emulator or filming phone.
2. Grant notifications and use **Test notification** in Director controls.
3. Import the required media, build the chats, and save each chat scene.
4. Start a saved scene and rehearse every app-user send and programmed delay once.
5. Use **Cancel pending cues** before restarting a take that was interrupted.
6. For subsequent takes, start the saved scene again. Do not clear app data unless you intend to erase all programmed content.

For reliable notification shots, keep Moment running or foregrounded before the cue, ensure notification sound is enabled, and confirm the device is not in Do Not Disturb mode.

## Staged uninstall and black-screen recovery

For the shot, open **Me > Uninstall app**. Moment presents its own **Uninstall Moment?** confirmation. Tapping **Uninstall** does not remove the Android package; it switches to a persistent, full-screen black frame and hides the system bars.

Do not use Android's real uninstall action during the take. A genuinely uninstalled app cannot draw or hold the black frame, and its imported production content would be deleted.

To recover without a computer, press and hold anywhere in the lower-left part of the black screen for three seconds. Moment returns to Reels. Clearing app data also recovers the app, but it additionally erases all saved posts, chats, scenes, and imported media.
