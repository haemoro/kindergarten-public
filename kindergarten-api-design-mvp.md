# 유치원 정보 서비스 - API 디자인 문서

> **범위**: 어드민 API + 앱(Flutter) API 설계  
> **전제**: 수집 API는 구현 완료, Supabase DB에 데이터 적재된 상태  
> **스택**: Spring Boot + Kotlin + JPA + Kotest + Ktor / Supabase(PostgreSQL) / 네이버 지도 API

---

## 1. API 역할 분리

```
┌──────────────────────────────────────────────────────┐
│                    Spring Boot API                    │
├──────────────┬───────────────────┬───────────────────┤
│  /api/collect │  /api/admin       │  /api/app         │
│  (수집)       │  (어드민)          │  (Flutter 앱)     │
│  ─────────── │  ─────────────── │  ─────────────── │
│  • 공공데이터  │  • 유치원 관리     │  • 유치원 검색     │
│    수집/동기화 │  • 데이터 검수     │  • 상세 조회       │
│  • Geocoding  │  • 수집 이력 조회  │  • 비교            │
│              │  • 수동 수집 트리거 │  • 주변 시설       │
│  (구현 완료)  │                   │                   │
└──────────────┴───────────────────┴───────────────────┘
```

### 역할 정의

| 구분 | Prefix | 인증 | 역할 |
|------|--------|------|------|
| 수집 API | `/api/collect` | 내부용 (API Key) | 공공데이터 수집, 이미 구현됨 |
| 어드민 API | `/api/admin` | 어드민 인증 (JWT) | 데이터 관리, 검수, 수집 제어 |
| 앱 API | `/api/app` | 앱 사용자 (비로그인 허용) | 검색, 조회, 비교 |

### URL 컨벤션

```
# 어드민
GET    /api/admin/kindergartens              # 목록 (페이징, 필터)
GET    /api/admin/kindergartens/{id}         # 상세
PATCH  /api/admin/kindergartens/{id}         # 수정
GET    /api/admin/crawl-histories            # 수집 이력

# 앱
GET    /api/app/kindergartens/search         # 검색
GET    /api/app/kindergartens/{id}           # 상세
GET    /api/app/kindergartens/compare        # 비교
GET    /api/app/kindergartens/{id}/nearby    # 주변 시설
```

---

## 2. 데이터 구조

> 기존 수집 데이터 구조가 아래와 맞다면 그대로 사용.  
> 컬럼 추가/변경이 필요한 부분만 ALTER로 처리.

### 2.1 kindergarten (유치원 기본)

```sql
CREATE TABLE kindergarten (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 공공데이터 원본
    official_id     VARCHAR(20) UNIQUE NOT NULL,    -- 유치원알리미 코드
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20),                     -- 공립/사립/국립
    address         VARCHAR(300),
    address_detail  VARCHAR(200),
    sido            VARCHAR(20),
    sigungu         VARCHAR(20),
    phone           VARCHAR(20),
    homepage_url    VARCHAR(500),
    establish_date  DATE,

    -- 좌표 (네이버 Geocoding)
    latitude        DECIMAL(10, 7),
    longitude       DECIMAL(10, 7),

    -- 어드민 관리 필드
    is_verified     BOOLEAN DEFAULT FALSE,           -- 어드민 검수 완료 여부
    is_active       BOOLEAN DEFAULT TRUE,            -- 노출 여부
    admin_memo      TEXT,                            -- 어드민 메모

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_kindergarten_sido ON kindergarten(sido, sigungu);
CREATE INDEX idx_kindergarten_name ON kindergarten(name);
CREATE INDEX idx_kindergarten_location ON kindergarten(latitude, longitude);
```

### 2.2 kindergarten_detail (상세 정보)

```sql
CREATE TABLE kindergarten_detail (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kindergarten_id     UUID NOT NULL REFERENCES kindergarten(id),

    -- 학급/인원
    class_count         INT,
    student_count       INT,
    capacity            INT,
    teacher_count       INT,
    staff_count         INT,
    student_teacher_ratio DECIMAL(5,2),

    -- 운영
    operation_hours     VARCHAR(100),
    afterschool_yn      BOOLEAN DEFAULT FALSE,
    bus_yn              BOOLEAN DEFAULT FALSE,

    -- 급식
    meal_type           VARCHAR(50),
    nutritionist_yn     BOOLEAN DEFAULT FALSE,

    -- 안전
    safety_check_date   DATE,
    safety_check_result VARCHAR(50),
    cctv_count          INT,

    -- 비용 (월 기준, 원)
    monthly_fee         INT,
    bus_fee             INT,
    meal_fee            INT,
    snack_fee           INT,
    special_activity_fee INT,

    -- 메타
    data_year           INT,
    collected_at        TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(kindergarten_id)
);
```

### 2.3 crawl_history (수집 이력 - 기존 테이블)

```sql
CREATE TABLE crawl_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source          VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    error_message   TEXT,
    item_count      INT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
```

---

## 3. 어드민 API

### 3.1 유치원 목록 조회

```
GET /api/admin/kindergartens
```

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | Int | 0 | 페이지 번호 |
| size | Int | 20 | 페이지 크기 |
| keyword | String? | null | 이름/주소 검색 |
| sido | String? | null | 시도 필터 |
| sigungu | String? | null | 시군구 필터 |
| type | String? | null | 공립/사립/국립 |
| isVerified | Boolean? | null | 검수 상태 필터 |
| isActive | Boolean? | null | 노출 상태 필터 |
| sortBy | String | "name" | 정렬 (name / updatedAt / studentCount) |
| sortDirection | String | "asc" | 정렬 방향 (asc / desc) |

**Response 200**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "officialId": "11110001",
      "name": "해맑은유치원",
      "type": "사립",
      "sido": "서울특별시",
      "sigungu": "강남구",
      "address": "서울시 강남구 역삼동 123-45",
      "phone": "02-1234-5678",
      "studentCount": 85,
      "teacherCount": 10,
      "isVerified": true,
      "isActive": true,
      "updatedAt": "2025-02-07T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 8432,
  "totalPages": 422
}
```

### 3.2 유치원 상세 조회

```
GET /api/admin/kindergartens/{id}
```

**Response 200**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "officialId": "11110001",
  "name": "해맑은유치원",
  "type": "사립",
  "address": "서울시 강남구 역삼동 123-45",
  "addressDetail": "해맑은빌딩 1층",
  "sido": "서울특별시",
  "sigungu": "강남구",
  "phone": "02-1234-5678",
  "homepageUrl": "https://haemakeun.kr",
  "establishDate": "2010-03-01",
  "latitude": 37.5012,
  "longitude": 127.0396,

  "detail": {
    "classCount": 5,
    "studentCount": 85,
    "capacity": 100,
    "teacherCount": 10,
    "staffCount": 5,
    "studentTeacherRatio": 8.5,
    "operationHours": "08:00 ~ 19:00",
    "afterschoolYn": true,
    "busYn": true,
    "mealType": "자체급식",
    "nutritionistYn": true,
    "safetyCheckDate": "2025-06-15",
    "safetyCheckResult": "적합",
    "cctvCount": 12,
    "monthlyFee": 250000,
    "busFee": 50000,
    "mealFee": 80000,
    "snackFee": 20000,
    "specialActivityFee": 50000,
    "dataYear": 2025
  },

  "isVerified": true,
  "isActive": true,
  "adminMemo": "2025년 신축 이전 확인 필요",
  "createdAt": "2025-01-15T03:00:00Z",
  "updatedAt": "2025-02-07T10:00:00Z"
}
```

### 3.3 유치원 정보 수정 (어드민)

> 수집 데이터 외에 어드민이 직접 관리하는 필드 수정

```
PATCH /api/admin/kindergartens/{id}
```

**Request Body**

```json
{
  "isVerified": true,
  "isActive": true,
  "adminMemo": "2025년 신축 이전 확인 완료"
}
```

**Response 200**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "isVerified": true,
  "isActive": true,
  "adminMemo": "2025년 신축 이전 확인 완료",
  "updatedAt": "2025-02-07T11:00:00Z"
}
```

### 3.4 유치원 일괄 상태 변경

```
PATCH /api/admin/kindergartens/batch-status
```

**Request Body**

```json
{
  "ids": [
    "550e8400-e29b-41d4-a716-446655440000",
    "660e8400-e29b-41d4-a716-446655440001"
  ],
  "isActive": false
}
```

**Response 200**

```json
{
  "updatedCount": 2
}
```

### 3.5 수집 이력 조회

```
GET /api/admin/crawl-histories
```

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | Int | 0 | |
| size | Int | 20 | |
| status | String? | null | SUCCESS / FAIL |
| source | String? | null | 수집 소스 필터 |

**Response 200**

```json
{
  "content": [
    {
      "id": "...",
      "source": "PUBLIC_API_BASIC",
      "status": "SUCCESS",
      "itemCount": 8432,
      "startedAt": "2025-02-01T03:00:00Z",
      "finishedAt": "2025-02-01T03:45:00Z",
      "errorMessage": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 24,
  "totalPages": 2
}
```

### 3.6 수동 수집 트리거

> 스케줄러 외에 어드민이 수동으로 수집 실행

```
POST /api/admin/crawl/trigger
```

**Request Body**

```json
{
  "source": "PUBLIC_API_BASIC",
  "sidoCode": "11"
}
```

**Response 202 (Accepted)**

```json
{
  "message": "수집이 시작되었습니다.",
  "crawlHistoryId": "770e8400-e29b-41d4-a716-446655440002"
}
```

### 3.7 대시보드 통계

```
GET /api/admin/dashboard/stats
```

**Response 200**

```json
{
  "totalKindergartens": 8432,
  "verifiedCount": 7200,
  "unverifiedCount": 1232,
  "activeCount": 8100,
  "inactiveCount": 332,
  "bySido": [
    { "sido": "서울특별시", "count": 1234 },
    { "sido": "경기도", "count": 2345 }
  ],
  "byType": [
    { "type": "사립", "count": 6543 },
    { "type": "공립", "count": 2800 },
    { "type": "국립", "count": 200 }
  ],
  "lastCrawl": {
    "source": "PUBLIC_API_BASIC",
    "status": "SUCCESS",
    "finishedAt": "2025-02-01T03:45:00Z",
    "itemCount": 8432
  }
}
```

---

## 4. 앱(Flutter) API

### 4.1 유치원 검색

```
GET /api/app/kindergartens/search
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| keyword | String? | N | null | 이름/주소 검색 |
| lat | Double? | N | null | 현재 위도 |
| lng | Double? | N | null | 현재 경도 |
| radius | Int | N | 3000 | 반경 (미터), lat/lng 필수 |
| sido | String? | N | null | 시도 필터 |
| sigungu | String? | N | null | 시군구 필터 |
| type | String? | N | null | 공립/사립 |
| sortBy | String | N | "distance" | distance / fee / ratio |
| page | Int | N | 0 | |
| size | Int | N | 20 | |

> `lat` + `lng` 제공 시 → 반경 기반 검색  
> `keyword` 제공 시 → 이름/주소 텍스트 검색  
> 둘 다 제공 시 → 반경 내에서 키워드 필터

**Response 200**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "해맑은유치원",
      "type": "사립",
      "address": "서울시 강남구 역삼동 123-45",
      "latitude": 37.5012,
      "longitude": 127.0396,
      "distance": 450,
      "studentTeacherRatio": 8.5,
      "totalMonthlyFee": 400000,
      "safetyCheckResult": "적합",
      "mealType": "자체급식"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

**앱 리스트 아이템에 필요한 정보만 내려줌 (상세는 별도 API)**

### 4.2 유치원 상세 조회

```
GET /api/app/kindergartens/{id}
```

**Response 200**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "해맑은유치원",
  "type": "사립",
  "address": "서울시 강남구 역삼동 123-45",
  "phone": "02-1234-5678",
  "homepageUrl": "https://haemakeun.kr",
  "latitude": 37.5012,
  "longitude": 127.0396,

  "education": {
    "classCount": 5,
    "studentCount": 85,
    "capacity": 100,
    "teacherCount": 10,
    "studentTeacherRatio": 8.5,
    "operationHours": "08:00 ~ 19:00",
    "afterschoolYn": true,
    "busYn": true
  },

  "meal": {
    "mealType": "자체급식",
    "nutritionistYn": true,
    "mealFee": 80000,
    "snackFee": 20000
  },

  "safety": {
    "safetyCheckDate": "2025-06-15",
    "safetyCheckResult": "적합",
    "cctvCount": 12
  },

  "fees": {
    "monthlyFee": 250000,
    "busFee": 50000,
    "mealFee": 80000,
    "snackFee": 20000,
    "specialActivityFee": 50000,
    "totalMonthlyFee": 450000
  },

  "externalLinks": {
    "naverMap": "https://map.naver.com/v5/search/해맑은유치원 서울시 강남구",
    "publicInfo": "https://e-childschoolinfo.moe.go.kr/..."
  }
}
```

**앱 상세 화면에서 섹션별로 나눠 보여주기 쉽도록 그룹핑**

### 4.3 유치원 비교

```
GET /api/app/kindergartens/compare?ids={id1},{id2},{id3}
```

> 최대 3개 비교

**Response 200**

```json
{
  "items": [
    {
      "id": "550e8400-...",
      "name": "해맑은유치원",
      "type": "사립",
      "address": "서울시 강남구 역삼동 123-45",
      "studentCount": 85,
      "capacity": 100,
      "teacherCount": 10,
      "studentTeacherRatio": 8.5,
      "totalMonthlyFee": 450000,
      "mealType": "자체급식",
      "nutritionistYn": true,
      "cctvCount": 12,
      "safetyCheckResult": "적합",
      "busYn": true,
      "afterschoolYn": true
    },
    {
      "id": "660e8400-...",
      "name": "푸른숲유치원",
      "type": "공립",
      "address": "서울시 강남구 삼성동 456-78",
      "studentCount": 60,
      "capacity": 80,
      "teacherCount": 8,
      "studentTeacherRatio": 7.5,
      "totalMonthlyFee": 180000,
      "mealType": "위탁급식",
      "nutritionistYn": false,
      "cctvCount": 8,
      "safetyCheckResult": "적합",
      "busYn": false,
      "afterschoolYn": true
    }
  ],
  "comparisonSummary": {
    "lowestFee": "푸른숲유치원",
    "bestRatio": "푸른숲유치원",
    "mostCctv": "해맑은유치원"
  }
}
```

### 4.4 주변 시설 (네이버 지역검색 API)

```
GET /api/app/kindergartens/{id}/nearby
```

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| categories | String | "hospital,park,pharmacy" | 쉼표 구분 카테고리 |
| radius | Int | 500 | 반경 (미터) |

**Response 200**

```json
{
  "kindergartenId": "550e8400-...",
  "kindergartenName": "해맑은유치원",
  "categories": {
    "hospital": [
      {
        "name": "강남소아과",
        "address": "서울시 강남구 역삼동 200",
        "distance": 180,
        "phone": "02-555-1234",
        "category": "소아과"
      }
    ],
    "park": [
      {
        "name": "역삼공원",
        "address": "서울시 강남구 역삼동 300",
        "distance": 350
      }
    ],
    "pharmacy": [
      {
        "name": "온누리약국",
        "address": "서울시 강남구 역삼동 150",
        "distance": 120,
        "phone": "02-555-5678"
      }
    ]
  }
}
```

### 4.5 시도/시군구 목록 (필터용)

```
GET /api/app/regions
```

**Response 200**

```json
{
  "regions": [
    {
      "sido": "서울특별시",
      "sigunguList": ["강남구", "강동구", "강북구", "강서구", "..."]
    },
    {
      "sido": "경기도",
      "sigunguList": ["수원시", "성남시", "고양시", "..."]
    }
  ]
}
```

### 4.6 지도 마커용 (경량)

> 지도에 마커만 찍을 때 사용. 최소 데이터만 내려줌.

```
GET /api/app/kindergartens/map-markers
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| lat | Double | Y | 중심 위도 |
| lng | Double | Y | 중심 경도 |
| radius | Int | N | 반경 (기본 3000m) |
| type | String? | N | 필터 |

**Response 200**

```json
{
  "markers": [
    {
      "id": "550e8400-...",
      "name": "해맑은유치원",
      "type": "사립",
      "latitude": 37.5012,
      "longitude": 127.0396
    },
    {
      "id": "660e8400-...",
      "name": "푸른숲유치원",
      "type": "공립",
      "latitude": 37.5034,
      "longitude": 127.0412
    }
  ],
  "count": 2
}
```

---

## 5. 공통 사항

### 5.1 에러 응답 형식

```json
{
  "code": "KINDERGARTEN_NOT_FOUND",
  "message": "유치원 정보를 찾을 수 없습니다.",
  "status": 404
}
```

**에러 코드 목록**

| 코드 | HTTP | 설명 |
|------|------|------|
| KINDERGARTEN_NOT_FOUND | 404 | 유치원 없음 |
| INVALID_PARAMETER | 400 | 파라미터 오류 |
| COMPARE_LIMIT_EXCEEDED | 400 | 비교 최대 3개 초과 |
| COMPARE_MINIMUM_REQUIRED | 400 | 비교 최소 2개 필요 |
| CRAWL_ALREADY_RUNNING | 409 | 수집 이미 진행 중 |
| NAVER_API_ERROR | 502 | 네이버 API 호출 실패 |
| UNAUTHORIZED | 401 | 인증 필요 |
| FORBIDDEN | 403 | 권한 없음 |

### 5.2 페이징 응답 형식

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

### 5.3 인증

| 대상 | 방식 | 설명 |
|------|------|------|
| 수집 API | X-API-Key 헤더 | 내부 호출용, 기존 유지 |
| 어드민 API | Bearer JWT | 어드민 로그인 후 발급 |
| 앱 API | 없음 (MVP) | 비로그인 허용, 추후 확장 |

### 5.4 앱 API vs 어드민 API 응답 차이

같은 유치원 데이터라도 역할에 따라 응답이 다름:

| 필드 | 앱 API | 어드민 API |
|------|--------|-----------|
| isVerified | ✗ (노출 안 함) | ✓ |
| isActive | ✗ (active만 조회) | ✓ |
| adminMemo | ✗ | ✓ |
| officialId | ✗ | ✓ |
| createdAt | ✗ | ✓ |

앱 API는 `isActive = true` 인 데이터만 조회됨 (쿼리 레벨에서 필터).

---

## 6. 프로젝트 구조 (패키지)

> 기존 프로젝트 구조에 맞춰 조정. 아래는 참고용 기본 구조.

```
src/main/kotlin/com/example/kinder/
│
├── domain/
│   └── kindergarten/
│       ├── entity/
│       │   ├── Kindergarten.kt
│       │   ├── KindergartenDetail.kt
│       │   └── CrawlHistory.kt
│       ├── repository/
│       │   ├── KindergartenRepository.kt
│       │   ├── KindergartenDetailRepository.kt
│       │   └── CrawlHistoryRepository.kt
│       ├── service/
│       │   ├── KindergartenQueryService.kt      # 조회 전용
│       │   ├── KindergartenCommandService.kt     # 수정 (어드민)
│       │   └── KindergartenCompareService.kt     # 비교 로직
│       └── enums/
│           ├── KindergartenType.kt               # 공립/사립/국립
│           ├── MealType.kt
│           └── SafetyCheckResult.kt
│
├── api/
│   ├── admin/
│   │   ├── AdminKindergartenController.kt
│   │   ├── AdminCrawlController.kt
│   │   ├── AdminDashboardController.kt
│   │   └── dto/
│   │       ├── AdminKindergartenListResponse.kt
│   │       ├── AdminKindergartenDetailResponse.kt
│   │       ├── AdminKindergartenUpdateRequest.kt
│   │       ├── AdminBatchStatusRequest.kt
│   │       ├── CrawlHistoryResponse.kt
│   │       ├── CrawlTriggerRequest.kt
│   │       └── DashboardStatsResponse.kt
│   │
│   ├── app/
│   │   ├── AppKindergartenController.kt
│   │   └── dto/
│   │       ├── KindergartenSearchRequest.kt
│   │       ├── KindergartenSearchResponse.kt
│   │       ├── KindergartenDetailResponse.kt
│   │       ├── KindergartenCompareResponse.kt
│   │       ├── NearbyFacilitiesResponse.kt
│   │       ├── MapMarkerResponse.kt
│   │       └── RegionResponse.kt
│   │
│   └── common/
│       ├── ApiResponse.kt                        # 공통 응답 래퍼
│       ├── ErrorResponse.kt
│       ├── ErrorCode.kt
│       └── PageResponse.kt
│
├── infra/
│   ├── naver/
│   │   ├── NaverMapClient.kt                    # 네이버 지도 API 클라이언트
│   │   └── dto/
│   │       ├── NaverGeocodeResponse.kt
│   │       └── NaverLocalSearchResponse.kt
│   └── config/
│       ├── NaverApiConfig.kt
│       └── SecurityConfig.kt
│
└── collector/                                    # 기존 수집 모듈 (구현 완료)
    ├── PublicDataCollector.kt
    └── GeocodingCollector.kt
```

---

## 7. MVP 구현 순서

### Step 1: 데이터 구조 확정 (1일)
- [ ] 기존 테이블과 위 스키마 비교, 필요 시 ALTER
- [ ] `is_verified`, `is_active`, `admin_memo` 컬럼 추가 (없는 경우)
- [ ] 수집된 데이터로 쿼리 테스트

### Step 2: 앱 API 개발 (3~4일)
- [ ] `GET /api/app/kindergartens/search` (키워드 + 위치 기반)
- [ ] `GET /api/app/kindergartens/{id}` (상세 조회)
- [ ] `GET /api/app/kindergartens/compare` (비교)
- [ ] `GET /api/app/kindergartens/map-markers` (지도 마커)
- [ ] `GET /api/app/regions` (시도/시군구 목록)
- [ ] `GET /api/app/kindergartens/{id}/nearby` (네이버 지역검색)
- [ ] Kotest 테스트 작성

### Step 3: 어드민 API 개발 (2~3일)
- [ ] `GET /api/admin/kindergartens` (목록 + 필터)
- [ ] `GET /api/admin/kindergartens/{id}` (상세)
- [ ] `PATCH /api/admin/kindergartens/{id}` (수정)
- [ ] `PATCH /api/admin/kindergartens/batch-status` (일괄 변경)
- [ ] `GET /api/admin/crawl-histories` (수집 이력)
- [ ] `POST /api/admin/crawl/trigger` (수동 수집)
- [ ] `GET /api/admin/dashboard/stats` (대시보드)
- [ ] Kotest 테스트 작성

### Step 4: 공통 처리 (1일)
- [ ] ErrorCode + GlobalExceptionHandler
- [ ] 어드민 인증 (JWT)
- [ ] 앱 API는 `isActive = true` 필터 자동 적용

### 총 MVP: 약 7~8일

---

## 8. 이후 확장 계획 (참고)

> 이 문서에서는 다루지 않음. 별도 설계서로 작성 예정.

- **어드민 프론트**: 웹 기반 관리자 화면 (React / Next.js 등)
- **Flutter 앱**: 지도 검색, 상세, 비교 화면
- **사용자 기능**: 회원가입, 리뷰, 즐겨찾기
- **알림**: 안전점검 결과 변경 알림 등
