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
