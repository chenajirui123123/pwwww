# Wardrobe Manager

A standalone course-lab implementation of member A's account module. This Java/XML client and Spring Boot backend are separate from the Kotlin/Compose application at the repository root. Open this directory's `android/` project to run the course module.

## Current implementation

Member A's module includes registration, login, profile editing, session management, logout and shared navigation. Accounts are persisted by a Spring Boot backend. The Android client uses Java and XML layouts, with separate view, controller and model responsibilities.

The clothing, outfit/calendar, wear/care and statistics entries are integration placeholders for members B–E. Their business functions are not implemented in this module.

## Repository structure

| Directory | Contents |
|---|---|
| `android/` | Android Studio project, Java source and XML layouts |
| `server/` | Spring Boot backend, database schema and API tests |

Open **`android/`**, rather than the repository root, in Android Studio. See `使用说明.md` for the Chinese setup guide and `团队接入说明.md` for module integration.

## Start the backend from source

Requirements: JDK 17 or later and Maven 3.6.3 or later. From `server/`:

```powershell
mvn package
java -jar target/wardrobe-account-server-1.0.0.jar
```

On Windows, `server/START_SERVER.cmd` also builds the backend when no packaged JAR is available, then starts it. The first build downloads dependencies.

The default server port is 8080. The default database is an H2 file under `server/data/` when started from the server directory. There are no seeded accounts; register through the app. The optional MySQL profile is documented in `server/README.md`. H2 and MySQL are alternatives: the backend uses one database per run.

## Run the Android app

1. Open `android/` in Android Studio.
2. Install Android SDK API 37 and use the IDE's bundled JDK for Gradle. The project uses AGP 9.5.1 and Gradle 9.6.0.
3. Wait for Gradle sync and select the `app` run configuration.
4. Start an emulator and run the app.
5. Leave the backend running. The default emulator server address is `http://10.0.2.2:8080`.
6. On Android 17, allow local network access when requested for the local development server.

Local HTTP is enabled only for debug builds. Release builds require a configured HTTPS endpoint. Each developer configures their own SDK and database credentials; local settings and account data are excluded from Git.

## Team responsibilities

| Member | Module |
|---|---|
| A | Accounts, shared framework, navigation and integration coordination |
| B | Clothing records, image upload, search and storage locations |
| C | Outfits and calendar plans |
| D | Actual wear records and garment care |
| E | Usage statistics |

Each member works on a feature branch and submits a pull request. All modules share one backend and database. Protected operations must derive the current owner from the server-validated session.

## Verification

The account module has passed three Android validation tests, ten backend integration tests and an Android API 37 emulator flow covering registration, login, profile persistence, module navigation and logout. Android Lint reports zero errors with non-blocking warnings.

MySQL deployment and physical-device testing remain to be checked. Compare the MVC responsibilities with Lecture 14 before the classroom demonstration.
