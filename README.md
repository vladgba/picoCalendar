# picoCalendar

A tiny Android calendar with no libraries: only the Android framework and the Kotlin standard library.
The release APK is about 50 KB.

## Features
- A month grid drawn directly on a `Canvas` (`MonthView`). It follows your locale's first day of the week and highlights weekends and today.
- Swipe left or right, or use the ‹ › arrows, to change month. Tap the title to jump back to today.
- Notes per day. Tap **+** to add one, tap a note to edit it, long-press to delete. Days with notes show a dot.
- Notes are stored in `files/notes.tsv`, written atomically via `android.util.AtomicFile`.
- A home-screen widget (`CalendarWidget`), resizable from 3×2 cells and up. Tap a day to open the app on it. ‹ › flip the widget's month, and tapping the title returns to the current month. It refreshes at midnight, on time, zone or locale changes, and after you edit notes. It is built from plain `RemoteViews` with no list adapter or service.
- Light and dark themes, edge-to-edge on every API level. minSdk 21, targetSdk 36.

## Build
```
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # minified + resource-shrunk (unsigned)
```
Requires the Android SDK (platform 36) and JDK 17 or newer, for example Android Studio's bundled JBR.

## Structure
```
app/src/main/java/x/vladgba/calendar/
  MainActivity.kt   UI built in code, navigation, note dialogs
  MonthView.kt      custom View: grid, tap, fling
  EventStore.kt     TSV note storage
  CalendarWidget.kt home-screen widget (AppWidgetProvider)
```
