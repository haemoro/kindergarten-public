# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kindergarten is a Spring Boot 3.4 application written in Kotlin 2.0 for managing kindergarten (어린이집/유치원) data. It uses Supabase (PostgreSQL + PostGIS) as its database and Ktor HTTP client for external API calls.

## Build & Development Commands

```bash
./gradlew build              # Build
./gradlew bootRun            # Run (port 1025)
./gradlew test               # Run all tests
./gradlew test --tests "com.sotti.kindergarten.SomeTest"          # Single test class
./gradlew test --tests "com.sotti.kindergarten.SomeTest.methodName"  # Single test method
./gradlew ktlintCheck        # Lint check
./gradlew ktlintFormat       # Auto-fix lint issues
```

## Tech Stack

- **Language**: Kotlin 2.0.21, JVM 17
- **Framework**: Spring Boot 3.4.2 (Web, JPA, Validation, Security)
- **Database**: PostgreSQL via Supabase, PostGIS for geospatial queries, Hibernate `ddl-auto: update`
- **HTTP Client**: Ktor 3.0.3 (CIO engine, Jackson serialization)
- **Query**: Querydsl 5.1.0 for type-safe JPA queries (kapt processor)
- **Auth**: JWT via JJWT 0.12.6 (HS256)
- **Linting**: ktlint 1.5.0 (max line length: 140 chars)
- **Testing**: Kotest BehaviorSpec + MockK + SpringMockK

## Architecture

Base package: `com.sotti.kindergarten`

```
controller/
├── admin/     # JWT-authenticated admin endpoints (/api/admin/**)
└── app/       # Public app endpoints (/api/app/**)
dto/
├── admin/     # Admin-specific DTOs
└── app/       # App-specific DTOs
entity/        # JPA entities (all extend BaseEntity with UUID id, createdAt, updatedAt)
repository/    # JPA repositories + Querydsl custom implementations
service/       # Business logic, manual DTO mapping via extension functions
security/      # JWT filter, token provider, admin user details
client/        # Ktor HTTP clients for external APIs (async with coroutines)
config/        # SecurityConfig, WebConfig (CORS), JPA config
exception/     # BusinessException + ErrorCode enum, GlobalExceptionHandler
```

### Domain Model

Core entity is `Center` (kindergarten) with many One-to-One child entities (CenterBuilding, CenterClassroom, CenterTeacher, etc.) using `CascadeType.ALL` + `orphanRemoval = true`. Centers have a PostGIS `Point` location field for geospatial radius queries (`ST_DWithin`, `ST_Distance`).

### Security / API Routes

- `/api/app/**`, `/api/v1/**` → permitAll (public)
- `POST /api/admin/auth/login` → permitAll
- `/api/admin/**` → requires JWT Bearer token
- Admin management endpoints → `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- Stateless session (CSRF disabled)

### Key Patterns

- **N+1 prevention**: Custom `fetchAllOneToOne()` Querydsl extension with fetch joins for Center's many OneToOne relations
- **External API client**: Ktor with coroutines, retry with exponential backoff, pagination via `fetchAllPages()`
- **No mapper libraries**: Manual DTO conversion in service layer
- **Exception handling**: `BusinessException(ErrorCode)` → `GlobalExceptionHandler` → consistent `ErrorResponse`

## Testing Conventions

Tests use **Kotest BehaviorSpec** (Given/When/Then) with **MockK**:

```kotlin
class SomeServiceTest : BehaviorSpec({
    Given("context") {
        When("action") {
            Then("assertion") { result shouldBe expected }
        }
    }
})
```

- **Controller tests**: `MockMvcBuilders.standaloneSetup()` without `@WebMvcTest` (avoids Spring Security bean conflicts)
- **Service tests**: Pure unit tests with MockK relaxed mocks
- Tests use `@ActiveProfiles("local")`

## Client App

Flutter 앱 (`kindergarten-flutter` 별도 레포)가 이 서버의 `/api/app/**` 엔드포인트를 사용하는 클라이언트입니다. 앱 API는 인증 없이 동작하며, 기기 식별은 `deviceId` (UUID)로 처리합니다. 상세 설계는 `docs/FLUTTER_MVP_SPEC.md` 참조.

**앱 화면 → API 매핑**:
- 홈/검색: `GET /api/app/kindergartens/search` (위치/키워드/설립유형 필터, 무한스크롤)
- 상세 (6탭 - 교육/급식/안전/시설/교사/방과후): `GET /api/app/kindergartens/{id}`
- 지도: `GET /api/app/kindergartens/map-markers` (반경 내 마커)
- 비교 (2~4개): `GET /api/app/kindergartens/compare`
- 즐겨찾기: `GET/POST/DELETE /api/app/favorites` (deviceId 기반)
- 지역 필터: `GET /api/app/regions`

## Environment Setup

Copy `.env.example` to `.env` and fill in Supabase credentials:
`SUPABASE_HOST`, `SUPABASE_PORT`, `SUPABASE_DATABASE`, `SUPABASE_USER`, `SUPABASE_PASSWORD`

## Lint Rules

ktlint 1.5.0 with **strict 140-character line limit** (not auto-correctable — must fix manually). Always run `./gradlew ktlintFormat` after writing new Kotlin files, then check remaining errors with `ktlintCheck`. Multiline expressions must start on a new line; chained method calls need newline before `.`.
