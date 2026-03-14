# Beauty Platform

Beauty Platform Backend Service

## Project Settings

- Language: Kotlin
- Framework: Spring Boot 3.5.11
- Java: 21

## Build Configuration

This project uses Gradle Kotlin DSL.

- Project name: `beauty-platform`
- Build script: `build.gradle.kts`
- Settings file: `settings.gradle.kts`
- Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties`

Java 21 is configured through the Gradle toolchain.

## Main Dependencies

- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Security
- Spring Boot Starter Validation
- Jackson Kotlin Module
- Flyway
- Kotlin Reflect
- MySQL Connector/J
- Spring OAuth2 Resource Server

## Package Structure

This project uses top-level feature/domain packages such as `auth`, `user`, and `common`.

- `common` is a cross-cutting area and may be split by responsibility, such as `common.api` and `common.security`.
- Domain packages stay flat by default and are split into nested role-based packages only when the domain grows enough to justify it.
- `auth` is currently split into `controller`, `service`, `dto`, and `exception`; `user` remains flat for now.

See [docs/package-structure.md](docs/package-structure.md) for the packaging guideline.

## Local Run

Local development defaults to the `local` profile and expects MySQL on `localhost:3306`.

Required environment variables:

- `JWT_SECRET`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `ADMIN_NAME`

Optional local DB overrides:

- `MYSQL_USER` (default: `root`)
- `MYSQL_PASSWORD` (default: empty)
- `JWT_ISSUER` (default: `http://localhost:8080`)
- `JWT_ACCESS_TOKEN_TTL` (default: `60m`)

Run:

```bash
./gradlew bootRun
```

## Test

Tests run against MySQL via Testcontainers and do not use the local `3306` instance.

```bash
./gradlew test
```
