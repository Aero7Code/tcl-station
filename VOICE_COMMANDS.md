# 100 Station voice phrases

These are **100 ways to invoke 15 bounded actions**, not 100 separately trained skills or device integrations. Enable **Hands-free** explicitly; it keeps the tablet microphone active while its foreground service runs, even with the screen off. Say “Hey Station” alone, wait for “I'm listening,” and give commands during the following **45 seconds**. Multiple different commands can be used before the window expires; saying “Hey Station” again resets the window. “Hey Station” plus a command in one utterance also works. **Speak** accepts a phrase without a wake call. Repeating the same phrase within five seconds is suppressed. The catalog matches final local Vosk transcripts, not raw audio; no parser test guarantees that Vosk heard a phrase correctly. After a wake, an unhandled final transcript is shown briefly on Station's screen to help diagnose what Vosk heard; it is not saved or put into the notification. Station saves no audio or transcripts.

**Limits:** the camera is a live **on-tablet** preview, not a remote security feed; no footage is saved. A set-alarm or set-timer request opens Android Clock for review rather than silently scheduling it. Play sends an Android media key even if audio is paused, so a paused player may resume, but playback is not guaranteed; pause/skip require active playback. Open music merely launches YouTube Music. With Station in the background, wake and time get spoken answers, weather attempts to open the Weather panel (with a tap-to-open notification if Android blocks the launch), and media keys can be sent to Android's active player; other actions ask you to open Station. A missing app or media session can prevent the requested action.

## Weather — Every recognized weather phrase expands Station's Weather panel; tap again for current details and the live rest-of-week forecast. The matcher also accepts common forecast, rain, snow, and temperature questions; it cannot understand every possible wording. Android can block automatic opening from the background, so a notification offers a tap-to-open fallback.
- “current weather”
- “weather right now”
- “whats the weather”
- “hows the weather”
- “tell me the weather”
- “show me the weather”
- “weather outside”
- “whats it like outside”
- “give me the weather”
- “check the weather”
- “whats todays weather”
- “what is the weather like today”
- “show todays weather”
- “weather today”

## Time — Denver time.
- “what time is it”
- “whats the time”
- “current time”
- “time right now”
- “tell me the time”
- “denver time”

## Front camera — Live on-tablet preview, foreground only.
- “open front camera”
- “show front camera”
- “front camera”
- “open the front camera”
- “show me the front camera”
- “switch to front camera”
- “front camera view”

## Back camera — Live on-tablet preview, foreground only.
- “open camera”
- “open back camera”
- “show back camera”
- “back camera”
- “turn on camera”
- “switch to back camera”
- “show camera”

## Close camera — Release the preview.
- “close camera”
- “hide camera”
- “exit camera”
- “close the camera”
- “turn off camera”

## Set timer — Opens Android Clock with the duration, subject to review. Numeric durations up to two hours also work, e.g. “set timer for 30 seconds.”
- “set a one minute timer”
- “set a two minute timer”
- “set a three minute timer”
- “set a five minute timer”
- “set a ten minute timer”
- “set a fifteen minute timer”
- “set a twenty minute timer”
- “set a thirty minute timer”

## Timers — Open the Android Clock timer screen.
- “open timers”
- “show timers”
- “my timers”
- “timer list”

## Set alarm — Opens Android Clock for review, not a silent alarm. Also accepts a specified 12-hour time such as “set alarm for 7:30 AM” or “wake me up at seven thirty pm”; no date or recurrence is inferred.
- “set alarm for six am”
- “set alarm for seven am”
- “set alarm for eight am”
- “set alarm for nine am”
- “set alarm for six pm”
- “set alarm for seven pm”
- “set alarm for eight pm”
- “set alarm for nine pm”

## Alarms — Open Android Clock's alarm screen.
- “open alarms”
- “show alarms”
- “my alarms”
- “alarm list”
- “set alarms”
- “show my alarms”

## Open music — Open YouTube Music in an app or browser; no autoplay guarantee.
- “open music”
- “show music”
- “launch music”
- “open youtube music”
- “launch youtube music”

## Play or resume — Send a Play key to Android; a paused player may resume, but if nothing starts choose a track in Music.
- “play music”
- “resume music”
- “play song”
- “resume song”
- “start music”
- “continue music”

## Pause — Send a Pause key to Android's active media session.
- “pause music”
- “pause song”
- “pause the music”
- “pause playback”
- “stop music”
- “stop the song”

## Next — Send a Next key to Android's active media session.
- “skip song”
- “next song”
- “skip this song”
- “play next song”
- “next track”
- “skip track”
- “skip the song”

## Station home — Return to page one.
- “go home”
- “show home”
- “station home”
- “return home”
- “back to home”
- “take me home”

## Stop hands-free — Turn off the persistent listener; Speak remains usable.
- “stop listening”
- “end listening”
- “turn off voice listening”
- “stop listening now”
- “stop the voice listener”

“Help,” “what can you do,” and a bounded timer grammar also work outside the 100 curated phrases. An unknown phrase never authorizes arbitrary tablet control.
