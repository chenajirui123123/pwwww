# Wardrobe account backend (member A)

Java 17+, Maven 3.6.3+, Spring Boot 3.3.0, JDBC. This is a real persistent account service, not a hard-coded login demonstration.

## Start with the included database

On Windows, double-click `START_SERVER.cmd`, or open PowerShell in this directory and run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-server.ps1
```

If a packaged JAR is included, it starts directly. Otherwise the script builds and tests it first. It finds Java via `JAVA_HOME`/PATH and Maven via PATH; it also recognizes `D:/jdk-17.0.2` and `D:/apache-maven-3.9.11/bin/mvn.cmd` on the current PC. On another PC, pass `-JavaHome 'C:/path/to/jdk' -MavenPath 'C:/path/to/mvn.cmd'`. Add `-Rebuild` after source changes. The script uses the included empty Maven settings to avoid a machine-specific broken mirror; replace that settings file only if your network requires an approved mirror.

From this `server` directory:

```powershell
mvn test
mvn package
java -jar target/wardrobe-account-server-1.0.0.jar
```

The server listens on port 8080. The default database is H2 **file storage**, created at `server/data/wardrobe.mv.db` when launched from this directory. Accounts survive a restart. Always launch from the same directory. Only the account and session tables are implemented here; B-E must add their own business modules to this same backend/database.

There are no seeded accounts. Register from the app first. Passwords are BCrypt hashes; the database stores only SHA-256 digests of randomly generated session tokens. Sessions expire after 24 hours by default. Logout revokes the current session in the database.

For the Android emulator, the computer's API address is `http://10.0.2.2:8080/`. For a physical phone, use the computer's LAN IP address, with both devices on the same trusted Wi-Fi network, and allow the backend through the local firewall if needed. Do not use `localhost` on the phone to reach the computer. Plain HTTP is intended only for this local classroom setup; use HTTPS before deployment.

## Use the shared MySQL database instead

The starter H2 database is for immediately running and checking A's module. If the group uses MySQL, switch **the entire backend** to the MySQL profile. H2 and MySQL are alternatives, never two simultaneous databases for the project. This profile does not copy any earlier H2 accounts.

Create a `wardrobe` database and a dedicated user in MySQL using your database administrator. Then set the connection details and start:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/wardrobe?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC'
$env:DB_USER='wardrobe'
$env:DB_PASSWORD='your-local-database-password'
java -jar target/wardrobe-account-server-1.0.0.jar --spring.profiles.active=mysql
```

Do not commit database passwords. `schema.sql` creates missing tables without deleting existing data. `CREATE TABLE IF NOT EXISTS` is not a migration mechanism: if the existing group schema differs, reconcile columns/constraints before startup and use migrations as the project grows. Never drop shared tables to fix a startup error.

Optional settings: `PORT`, `SESSION_TTL` (ISO duration such as `PT24H`). To change the default H2 file location set `DB_URL` explicitly.

## API contract

All bodies are JSON. Protected endpoints require `Authorization: Bearer <token>`. Errors return `{ "message": "..." }` with an appropriate 400/401/409/500 HTTP status.

| Method | Path | Request | Success |
|---|---|---|---|
| POST | `/api/auth/register` | `accountName`, `password`, `displayName` | 201 `{ "user": { "id", "accountName", "displayName" } }` |
| POST | `/api/auth/login` | `accountName`, `password` | 200 `{ "token": "...", "user": { ... } }` |
| GET | `/api/me` | No body | 200 `{ "user": { ... } }` |
| PATCH | `/api/me` | `displayName` | 200 `{ "user": { ... } }` |
| POST | `/api/auth/logout` | No body | 204, empty body |

Account names: 3-32 ASCII letters, digits or underscores, trimmed and lowercased at both registration and login. Passwords: 8-72 UTF-8 bytes, never trimmed. Display names: 1-40 Java UTF-16 characters after trimming. Profile updates cannot change the account name or user ID.

## MVC and integration

- `AccountController`: HTTP input and result mapping (Controller).
- `AccountService`: validation, identity and account/session operations (Model/application service).
- `AccountRepository`: parameterized database access (Model/persistence).
- The Android XML pages and rendering code are the View; Android controllers coordinate their actions.

MVC is a responsibility separation, not just three folder names. Compare this implementation with the teacher's Lecture 14 before presenting it as the final course interpretation.

B-E should inject `AccountService`, call `requireUser(authorization)`, and scope all queries to the returned `user.id()`. Never trust a user ID supplied by the phone. Public registration/login deliberately do not require a session. This starter has no administrator, camera, packing list, external notification or 30-day deletion feature.

## Checks

`AccountApiTest` exercises actual HTTP handlers and a real in-memory test database: registration persistence, normalization/duplicate rejection, hash storage, login, profile owner isolation, revocation and expiry, forged sessions, validation and malformed input. The test database is separate from the persistent development database and does not modify it.

MySQL compatibility must also be checked against the group's running MySQL instance before the final classroom demonstration. This starter does not implement password recovery, account deletion, throttling or production deployment hardening.
