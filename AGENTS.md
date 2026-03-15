# Repository Guidelines

## Project Structure & Module Organization
`src/main/kotlin/com/beautyplatform` contains the Spring Boot application code. Organize code by feature or domain, not by technical layer: current top-level packages include `auth`, `product`, `category`, `seller`, `user`, and shared code in `common/api` and `common/security`. Keep new domains flat at first, then introduce `controller`, `service`, `dto`, or `exception` subpackages only when the domain grows.

`src/main/resources` holds application config (`application.yaml`, `application-local.yaml`) and Flyway SQL migrations under `db/migration`. Kotlin-based migration code lives in `src/main/kotlin/db/migration`. Tests live in `src/test/kotlin` and usually mirror the runtime package they exercise.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

- `./gradlew bootRun` starts the app with the default `local` profile.
- `./gradlew test` runs JUnit 5 integration tests with Spring Boot, MockMvc, and Testcontainers.
- `./gradlew build` compiles, runs tests, and produces the application artifact.
- `./gradlew clean test` is the safest rerun after schema or container-related changes.

Local development expects MySQL on `localhost:3306`.

## Coding Style & Naming Conventions
This project uses Kotlin 1.9, Spring Boot 3.5, and Java 21. Follow Kotlin official style with 4-space indentation and IntelliJ's default formatter; no separate lint task is configured in the repo. Use `PascalCase` for classes, `camelCase` for functions and properties, and lowercase package names.

Prefer constructor injection, immutable `val` properties, and domain-focused package placement. Examples: DTOs under `product/dto` and security code under `common/security`.

In DTO request classes, insert a blank line before the next field when the current field carries validation annotations such as `@field:NotBlank`, `@field:Size`, or `@field:Valid`. For example, if `name` has validation annotations, leave an empty line before declaring the next field such as `price`.

## Testing Guidelines
Favor integration tests for API and persistence changes. Existing tests use names such as `AuthIntegrationTest` and `ProductManagementIntegrationTest`; keep that pattern for end-to-end flows. Place test-only wiring in clearly named helpers such as `SecurityTestProbeConfiguration` or `ProductImageStorageTestConfiguration`.

No coverage gate is configured, so new behavior should ship with matching tests and a passing `./gradlew test`.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commits with scopes, for example `feat(product): add catalog management workflows` and `fix(config): configure product image buckets by environment`. Keep commits focused on one logical change.

Pull requests should summarize behavior changes, call out config or migration impact, link related issues, and include test evidence such as `./gradlew test`. Never commit real secrets; use environment variables like `JWT_SECRET`, `ADMIN_EMAIL`, and `PRODUCT_IMAGES_BUCKET`.
