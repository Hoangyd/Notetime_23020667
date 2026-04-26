# Notes App (Room + Compose)

Android notes application built with Kotlin, Room (SQLite), ViewModel + coroutines, and Jetpack Compose.

## Features

- Create, read, update, and delete notes
- Reactive note list backed by Room Flow observers
- Search by title/content/category
- Category filtering
- Pin and archive actions
- JSON export/import through Android document picker
- Database migration example from schema version 1 to 2
- Persistence tests for DAO behavior and migration

## Tech Stack

- Room (SQLite) for persistence
- ViewModel + Kotlin Flow/StateFlow for observable UI state
- Jetpack Compose for UI
- Navigation Compose for routing

## Project Structure

- `app/src/main/java/com/example/notes/data`: Entity, DAO, repository, database, migration
- `app/src/main/java/com/example/notes/ui`: ViewModel, navigation, Compose screens, theme
- `app/src/androidTest/java/com/example/notes/data`: Persistence and migration tests

## Run

1. Open the project in Android Studio (or VS Code with Android tooling).
2. Ensure Android SDK and build tools are installed.
3. Build and run on an emulator/device.

## Test

- Instrumented persistence tests:
  - `./gradlew connectedAndroidTest`

## Migration

- Database version is `2` in `NotesDatabase`.
- `MIGRATION_1_2` adds:
  - `category` TEXT NOT NULL DEFAULT `''`
  - `isPinned` INTEGER NOT NULL DEFAULT `0`
  - `isArchived` INTEGER NOT NULL DEFAULT `0`
