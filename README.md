# 유치원 정보 API 서버

전국 유치원·어린이집 공공데이터를 수집·제공하는 Spring Boot REST API 서버입니다.
Flutter 앱의 백엔드로 동작하며, 유치원 검색/상세조회/비교/즐겨찾기/리뷰 기능을 제공합니다.

## Tech Stack

| 분류 | 기술 |
|------|------|
| Language | Kotlin 2.0.21 / JVM 17 |
| Framework | Spring Boot 3.4.2 (Web, JPA, Security, Cache, Actuator) |
| Database | PostgreSQL (Supabase) + PostGIS (지리 공간 쿼리) |
| ORM | Spring Data JPA + Querydsl 5.1.0 |
| HTTP Client | Ktor 3.0.3 (CIO, 코루틴 기반) |
| Auth | JWT (JJWT 0.12.6, HS256) |
| Cache | Caffeine (인메모리) |
| Linting | ktlint 1.5.0 |
| Test | Kotest BehaviorSpec + MockK + SpringMockK |
| Deploy | Docker + docker-compose |

## 주요 기능

- **유치원 검색**: 위치(반경), 키워드, 설립유형, 시도/시군구 필터 + 무한스크롤 페이징
- **유치원 상세**: 교육/급식/안전/시설/교사/방과후 6개 섹션
- **지도 마커**: 반경 내 유치원 좌표 목록 (PostGIS ST_DWithin)
- **비교**: 2~4개 유치원 동시 비교
- **즐겨찾기**: deviceId 기반 (로그인 없음)
- **리뷰**: 사용자 리뷰(CRUD) + 네이버 블로그/카페 외부 리뷰 수집
- **데이터 동기화**: 공공데이터 API (e-childschoolinfo.moe.go.kr) 크롤링
- **어드민**: JWT 인증 기반 관리자 API (유치원 관리, 크롤링 트리거, 대시보드)

## 프로젝트 구조

```
src/main/kotlin/com/sotti/kindergarten/
├── controller/
│   ├── app/          # 앱용 공개 API (/api/app/**)
│   └── admin/        # 관리자 API (/api/admin/**)
├── service/          # 비즈니스 로직
├── repository/       # JPA + Querydsl + Native SQL
├── entity/           # JPA 엔티티 (BaseEntity 상속)
├── dto/
│   ├── app/          # 앱 응답 DTO
│   └── admin/        # 어드민 응답 DTO
├── client/           # Ktor HTTP 클라이언트 (공공API, 네이버 검색)
├── security/         # JWT 필터, TokenProvider
├── config/           # SecurityConfig, WebConfig, CacheConfig 등
├── exception/        # BusinessException, ErrorCode, GlobalExceptionHandler
└── util/             # 공통 유틸 (PageExtensions, ParamParser 등)
```

## API 엔드포인트

### App API (인증 불필요)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/app/kindergartens/search` | 유치원 검색 (위치/키워드/필터) |
| GET | `/api/app/kindergartens/{id}` | 유치원 상세 |
| GET | `/api/app/kindergartens/compare` | 유치원 비교 (ids 쿼리파라미터, 최대 4개) |
| GET | `/api/app/kindergartens/map-markers` | 지도 마커 |
| GET | `/api/app/kindergartens/{id}/reviews` | 외부 리뷰 목록 |
| GET | `/api/app/reviews/recent` | 최근 외부 리뷰 |
| GET | `/api/app/user-reviews/centers/{centerId}` | 사용자 리뷰 목록 |
| POST | `/api/app/user-reviews` | 사용자 리뷰 작성 |
| PUT | `/api/app/user-reviews/{reviewId}` | 사용자 리뷰 수정 |
| DELETE | `/api/app/user-reviews/{reviewId}` | 사용자 리뷰 삭제 |
| GET | `/api/app/user-reviews/recent` | 최근 사용자 리뷰 |
| GET | `/api/app/favorites` | 즐겨찾기 목록 |
| POST | `/api/app/favorites` | 즐겨찾기 추가 |
| DELETE | `/api/app/favorites/{id}` | 즐겨찾기 삭제 |
| GET | `/api/app/regions` | 시도/시군구 목록 |

### Admin API (JWT Bearer 필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/admin/auth/login` | 로그인 |
| GET | `/api/admin/kindergartens` | 유치원 목록 |
| GET | `/api/admin/kindergartens/{id}` | 유치원 상세 |
| PATCH | `/api/admin/kindergartens/{id}` | 유치원 수정 |
| POST | `/api/admin/kindergartens/batch-status` | 일괄 상태 변경 |
| GET | `/api/admin/dashboard` | 대시보드 통계 |
| POST | `/api/admin/crawl` | 크롤링 트리거 |
| GET | `/api/admin/crawl/histories` | 크롤링 이력 |
| POST | `/api/admin/reviews/sync` | 전체 리뷰 동기화 |
| POST | `/api/admin/reviews/sync/{centerId}` | 단건 리뷰 동기화 |

## 시작하기

### 사전 요구사항

- JDK 17+
- [Supabase](https://supabase.com) 계정 (PostgreSQL + PostGIS 활성화)
- [공공데이터포털](https://www.data.go.kr) API 키 (e-childschoolinfo)
- [네이버 개발자센터](https://developers.naver.com) 검색 API 키 (외부 리뷰 수집용, 선택)

### 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열고 값을 채웁니다:

```env
# Supabase Database
SUPABASE_HOST=db.xxxxxxxxxxxx.supabase.co
SUPABASE_PORT=6543
SUPABASE_DATABASE=postgres
SUPABASE_USER=postgres
SUPABASE_PASSWORD=your-database-password

# Kindergarten API (공공데이터)
KINDERGARTEN_API_KEY=your-api-key

# Docker Production (JDBC URL)
SUPABASE_URL=jdbc:postgresql://your-supabase-host:6543/postgres

# JWT Secret (최소 256비트 이상의 랜덤 문자열)
JWT_SECRET=your-random-secret-key

# Naver Search API (선택)
NAVER_CLIENT_ID=your-naver-client-id
NAVER_CLIENT_SECRET=your-naver-client-secret

# Initial Admin Account
ADMIN_INITIAL_EMAIL=admin@yourdomain.com
ADMIN_INITIAL_PASSWORD=your-secure-password
```

### Supabase PostGIS 설정

Supabase Dashboard → Database → Extensions에서 `postgis`를 활성화합니다.

### 로컬 실행

```bash
# 의존성 설치 및 빌드
./gradlew build

# 서버 실행 (포트 1025)
./gradlew bootRun
```

### Docker 실행

```bash
# 이미지 빌드
docker build -t sotti-kindergarten .

# 실행 (환경 변수는 .env에서 로드)
docker compose -f docker-compose.nas.yml up -d
```

## 개발

### 빌드 & 테스트

```bash
./gradlew build              # 전체 빌드
./gradlew test               # 테스트 실행
./gradlew ktlintFormat       # 코드 포맷팅 자동 수정
./gradlew ktlintCheck        # 린트 검사
```

### 단일 테스트 실행

```bash
./gradlew test --tests "com.sotti.kindergarten.SomeTest"
./gradlew test --tests "com.sotti.kindergarten.SomeTest.methodName"
```

### 테스트 작성 규칙

Kotest BehaviorSpec + MockK를 사용합니다:

```kotlin
class SomeServiceTest : BehaviorSpec({
    Given("context") {
        When("action") {
            Then("assertion") {
                result shouldBe expected
            }
        }
    }
})
```

- 컨트롤러 테스트: `MockMvcBuilders.standaloneSetup()` 사용 (`@WebMvcTest` 미사용)
- 서비스 테스트: MockK relaxed mock
- 프로파일: `@ActiveProfiles("local")`

## 데이터 모델 (ERD)

핵심 엔티티는 `Center`(유치원)이며, 11개의 1:1 자식 엔티티와 2개의 1:N 자식 엔티티로 구성됩니다.

```
Center (유치원)
├── CenterBuilding     - 건물 정보
├── CenterClassroom    - 교실/시설 면적
├── CenterTeacher      - 교사 현황
├── CenterLessonDay    - 연간 수업일수
├── CenterMeal         - 급식 운영
├── CenterBus          - 통학차량
├── CenterYearOfWork   - 교사 근속연수
├── CenterEnvironment  - 환경위생 (공기질/소독/수질)
├── CenterSafetyCheck  - 안전점검 (소방/가스/전기/CCTV)
├── CenterMutualAid    - 공제회 가입
├── CenterAfterSchool  - 방과후과정
├── CenterSafetyEducation[] - 안전교육 (학기별)
└── CenterInsurance[]       - 보험 가입 현황
```

전체 ERD는 [docs/ERD.md](docs/ERD.md)를 참고하세요.

## 아키텍처

### 보안

- `/api/app/**` → 인증 불필요
- `POST /api/admin/auth/login` → 인증 불필요
- `/api/admin/**` → JWT Bearer 토큰 필요
- 관리자 권한 관리 → `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- Stateless 세션 (CSRF 비활성화)

### 성능

- **N+1 방지**: `fetchAllOneToOne()` Querydsl extension으로 Center의 모든 1:1 관계 fetch join
- **지리 공간 쿼리**: PostGIS `ST_DWithin` (반경 검색), `ST_Distance` (거리 계산)
- **캐싱**: Caffeine 인메모리 캐시 (`centerDetail`, `appCenterDetail`)
- **페이징**: Native SQL에 `COUNT(*) OVER()` window function으로 COUNT 쿼리 제거

### 외부 API 연동

- **공공데이터 API**: Ktor + 코루틴, 지수 백오프 재시도, 페이지네이션 자동 처리
- **네이버 검색 API**: 블로그/카페 검색으로 외부 리뷰 수집, Semaphore로 동시성 제어

### 에러 처리

`BusinessException(ErrorCode)` → `GlobalExceptionHandler` → 일관된 `ErrorResponse` 구조

```json
{
  "status": 404,
  "code": "KINDERGARTEN_NOT_FOUND",
  "message": "Kindergarten not found"
}
```

## 초기 관리자 계정

서버 최초 실행 시 `ADMIN_INITIAL_EMAIL` / `ADMIN_INITIAL_PASSWORD` 환경 변수로 설정한 계정이 자동 생성됩니다.

## 라이선스

MIT
