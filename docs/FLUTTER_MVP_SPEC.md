# Flutter MVP 설계 문서 - 유치원 찾기 앱

## 1. 프로젝트 개요

### 목적
부모/보호자가 내 주변 유치원을 검색하고, 상세 정보를 확인하고, 비교/즐겨찾기할 수 있는 모바일 앱

### 기술 스택
| 영역 | 패키지 | 버전 | 용도 |
|------|--------|------|------|
| 상태관리 | `flutter_riverpod` | latest | 상태 관리 + API 캐싱 |
| HTTP | `dio` | latest | REST API 통신 |
| 지도 | `google_maps_flutter` | latest | 지도 표시 |
| 위치 | `geolocator` + `geocoding` | latest | 현위치 획득 |
| 기기ID | `device_info_plus` | latest | deviceId 생성 |
| 저장 | `shared_preferences` | latest | 로컬 설정 저장 |
| 라우팅 | `go_router` | latest | 선언적 라우팅 |
| 무한스크롤 | `infinite_scroll_pagination` | latest | 페이징 목록 |
| URL런처 | `url_launcher` | latest | 전화/홈페이지 연결 |

### 서버 정보
- Base URL: `http://localhost:1025` (개발), 추후 프로덕션 URL 교체
- 인증: 앱 API(`/api/app/**`)는 인증 없음
- 기기 식별: `deviceId` (UUID, 앱 최초 실행 시 생성 후 로컬 저장)

---

## 2. 디렉토리 구조

```
lib/
├── main.dart
├── app.dart                          # MaterialApp + GoRouter 설정
│
├── core/
│   ├── constants/
│   │   ├── api_constants.dart        # baseUrl, endpoints
│   │   └── app_constants.dart        # 기본값 (반경, 페이지 크기 등)
│   ├── network/
│   │   ├── dio_client.dart           # Dio 인스턴스 설정
│   │   └── api_exception.dart        # 에러 응답 모델
│   ├── utils/
│   │   ├── device_id_manager.dart    # deviceId 생성/저장
│   │   └── location_service.dart     # 위치 권한 + 현위치
│   └── theme/
│       ├── app_colors.dart
│       ├── app_text_styles.dart
│       └── app_theme.dart
│
├── models/
│   ├── kindergarten_search.dart      # 검색 결과 모델
│   ├── kindergarten_detail.dart      # 상세 모델 (섹션 포함)
│   ├── map_marker.dart               # 지도 마커 모델
│   ├── compare_item.dart             # 비교 항목 모델
│   ├── favorite.dart                 # 즐겨찾기 모델
│   ├── region.dart                   # 지역(시도/시군구) 모델
│   └── page_response.dart            # 페이징 응답 제네릭
│
├── repositories/
│   ├── kindergarten_repository.dart  # 유치원 API 호출
│   ├── favorite_repository.dart      # 즐겨찾기 API 호출
│   └── region_repository.dart        # 지역 API 호출
│
├── providers/
│   ├── kindergarten_providers.dart   # 검색/상세/비교 프로바이더
│   ├── favorite_providers.dart       # 즐겨찾기 프로바이더
│   ├── region_providers.dart         # 지역 프로바이더
│   ├── location_providers.dart       # 위치 프로바이더
│   └── device_id_provider.dart       # deviceId 프로바이더
│
├── screens/
│   ├── home/
│   │   └── home_screen.dart
│   ├── search/
│   │   ├── search_screen.dart
│   │   └── widgets/
│   │       ├── search_bar.dart
│   │       ├── filter_chips.dart
│   │       ├── region_selector.dart
│   │       └── kindergarten_card.dart
│   ├── map/
│   │   ├── map_screen.dart
│   │   └── widgets/
│   │       └── marker_bottom_sheet.dart
│   ├── detail/
│   │   ├── detail_screen.dart
│   │   └── widgets/
│   │       ├── detail_header.dart
│   │       ├── education_tab.dart
│   │       ├── meal_tab.dart
│   │       ├── safety_tab.dart
│   │       ├── facility_tab.dart
│   │       ├── teacher_tab.dart
│   │       └── after_school_tab.dart
│   ├── compare/
│   │   └── compare_screen.dart
│   ├── favorites/
│   │   └── favorites_screen.dart
│   └── settings/
│       └── settings_screen.dart
│
└── widgets/                          # 공용 위젯
    ├── kindergarten_list_tile.dart
    ├── badge_chip.dart               # 급식/통학/연장 뱃지
    ├── empty_state.dart
    └── error_state.dart
```

---

## 3. API 연동 명세

### 3.1 Base 설정

```
Base URL: {SERVER_URL}/api/app
Content-Type: application/json
```

### 3.2 에러 응답 (공통)

```json
{
  "status": 404,
  "code": "KINDERGARTEN_NOT_FOUND",
  "message": "Kindergarten not found",
  "timestamp": "2025-01-01T00:00:00"
}
```

### 3.3 페이징 응답 (공통)

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

### 3.4 유치원 검색

```
GET /api/app/kindergartens/search
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| lat | double | N | - | 위도 |
| lng | double | N | - | 경도 |
| radiusKm | double | N | 2 | 검색 반경(km) |
| type | string | N | - | 설립유형 (국공립/사립/법인 등) |
| q | string | N | - | 검색어 (이름/주소) |
| sort | string | N | distance | 정렬 (distance/name) |
| page | int | N | 0 | 페이지 번호 (0부터) |
| size | int | N | 20 | 페이지 크기 |

**응답**: `PageResponse<KindergartenSearch>`

```json
{
  "content": [
    {
      "id": "uuid",
      "name": "해피유치원",
      "establishType": "국공립",
      "address": "서울시 강남구 ...",
      "phone": "02-1234-5678",
      "lat": 37.5,
      "lng": 127.0,
      "distanceKm": 1.2,
      "capacity": 100,
      "currentEnrollment": 80,
      "totalClassCount": 5,
      "mealProvided": true,
      "busAvailable": true,
      "extendedCare": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

### 3.5 유치원 상세

```
GET /api/app/kindergartens/{id}
```

**응답**: `KindergartenDetail`

```json
{
  "id": "uuid",
  "name": "해피유치원",
  "establishType": "국공립",
  "address": "서울시 강남구 ...",
  "phone": "02-1234-5678",
  "homepage": "http://happy.kindergarten.com",
  "operatingHours": "07:00-19:00",
  "lat": 37.5,
  "lng": 127.0,
  "representativeName": "홍길동",
  "directorName": "김철수",
  "establishDate": "2010-03-01",
  "openDate": "2010-03-01",
  "education": {
    "classCountByAge": { "age3": 2, "age4": 2, "age5": 1, "mixed": 0, "special": 0 },
    "capacityByAge": { "age3": 30, "age4": 30, "age5": 20, "mixed": 0, "special": 0 },
    "enrollmentByAge": { "age3": 25, "age4": 28, "age5": 17, "mixed": 0, "special": 0 },
    "lessonDaysAge3": 240,
    "lessonDaysAge4": 240,
    "lessonDaysAge5": 240,
    "lessonDaysMixed": null,
    "belowLegalDays": "N"
  },
  "meal": {
    "mealOperationType": "직영",
    "consignmentCompany": null,
    "mealChildren": 80,
    "cookCount": 2
  },
  "safety": {
    "airQualityCheck": "적합",
    "disinfectionCheck": "적합",
    "waterQualityCheck": "적합",
    "dustMeasurement": "적합",
    "lightMeasurement": "적합",
    "fireInsuranceCheck": "가입",
    "gasCheck": "적합",
    "electricCheck": "적합",
    "playgroundCheck": "적합",
    "cctvInstalled": "Y",
    "cctvTotal": 10,
    "schoolSafetyEnrolled": "가입",
    "educationFacilityEnrolled": "가입"
  },
  "facility": {
    "archYear": 2010,
    "floorCount": 3,
    "buildingArea": 500.0,
    "totalLandArea": 800.0,
    "classroomCount": 5,
    "classroomArea": 300.0,
    "playgroundArea": 200.0,
    "busOperating": "Y",
    "operatingBusCount": 2,
    "registeredBusCount": 2
  },
  "teacher": {
    "directorCount": 1,
    "viceDirectorCount": 0,
    "masterTeacherCount": 1,
    "leadTeacherCount": 2,
    "generalTeacherCount": 5,
    "specialTeacherCount": 1,
    "healthTeacherCount": 0,
    "nutritionTeacherCount": 1,
    "staffCount": 3,
    "masterQualCount": 2,
    "grade1QualCount": 3,
    "grade2QualCount": 4,
    "assistantQualCount": 1,
    "under1Year": 1,
    "between1And2Years": 2,
    "between2And4Years": 3,
    "between4And6Years": 2,
    "over6Years": 2
  },
  "afterSchool": {
    "independentClassCount": 3,
    "afternoonClassCount": 2,
    "independentParticipants": 30,
    "afternoonParticipants": 20,
    "regularTeacherCount": 2,
    "contractTeacherCount": 1,
    "dedicatedStaffCount": 1
  },
  "sourceUpdatedAt": "2025-01-15T10:00:00"
}
```

---

### 3.6 지도 마커

```
GET /api/app/kindergartens/map-markers
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| lat | double | Y | - | 위도 |
| lng | double | Y | - | 경도 |
| radiusKm | double | N | 2 | 반경(km) |
| type | string | N | - | 설립유형 필터 |

**응답**: `List<MapMarker>`

```json
[
  {
    "id": "uuid",
    "name": "해피유치원",
    "establishType": "국공립",
    "lat": 37.5,
    "lng": 127.0
  }
]
```

---

### 3.7 유치원 비교

```
GET /api/app/kindergartens/compare
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| ids | string | Y | 콤마 구분 UUID (2~4개) |
| lat | double | N | 거리 계산용 위도 |
| lng | double | N | 거리 계산용 경도 |

**응답**: `CompareResponse`

```json
{
  "centers": [
    {
      "id": "uuid",
      "name": "해피유치원",
      "establishType": "국공립",
      "address": "서울시 강남구",
      "distanceKm": 1.2,
      "capacity": 100,
      "currentEnrollment": 80,
      "teacherCount": 10,
      "classCount": 5,
      "mealProvided": true,
      "busAvailable": true,
      "extendedCare": false,
      "buildingArea": 500.0,
      "classroomArea": 300.0,
      "cctvInstalled": true,
      "cctvTotal": 10
    }
  ]
}
```

---

### 3.8 즐겨찾기

**목록 조회**
```
GET /api/app/favorites?deviceId={deviceId}&page=0&size=20
```

```json
{
  "content": [
    {
      "id": "uuid (favorite ID)",
      "centerId": "uuid (kindergarten ID)",
      "centerName": "해피유치원",
      "createdAt": "2025-01-01T12:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

**추가**
```
POST /api/app/favorites
Content-Type: application/json

{
  "deviceId": "device-uuid",
  "centerId": "kindergarten-uuid"
}
```

응답: `201 Created` + FavoriteResponse body

**삭제**
```
DELETE /api/app/favorites/{favoriteId}?deviceId={deviceId}
```

응답: `204 No Content`

---

### 3.9 지역 목록

```
GET /api/app/regions
```

```json
{
  "regions": [
    {
      "sidoCode": "11",
      "sidoName": "서울특별시",
      "sggList": [
        { "sggCode": "11010", "sggName": "종로구" },
        { "sggCode": "11020", "sggName": "중구" }
      ]
    },
    {
      "sidoCode": "26",
      "sidoName": "부산광역시",
      "sggList": [...]
    }
  ]
}
```

---

## 4. 화면별 상세 설계

### 4.1 홈 화면

**진입 조건**: 앱 실행 시 첫 화면

**동작 흐름**:
1. 앱 실행 → 위치 권한 요청
2. 권한 허용 시 → 현위치 기반 주변 유치원 로드 (radiusKm=2, size=10)
3. 권한 거부 시 → 검색 유도 UI 표시

**UI 구성**:
| 영역 | 컴포넌트 | 동작 |
|------|---------|------|
| 상단 | 검색바 (ReadOnly) | 탭 → 검색 화면 이동 |
| 섹션1 | "내 주변 유치원" 가로 카드 리스트 | 카드 탭 → 상세 이동 |
| 섹션2 | 설립유형 필터 칩 | 탭 → 해당 유형으로 검색 화면 이동 |
| 섹션3 | "즐겨찾기" 가로 카드 리스트 | 즐겨찾기 있을 때만 표시 |

**카드 표시 항목**:
- 유치원 이름
- 설립유형 뱃지
- 거리 (x.x km)
- 정원/현원
- 아이콘 뱃지: 급식, 통학버스, 연장돌봄

---

### 4.2 검색/목록 화면

**진입 조건**: 하단 탭 또는 홈 검색바 탭

**동작 흐름**:
1. 검색어 입력 또는 필터 변경 → 디바운스 300ms → API 호출
2. 스크롤 끝 도달 → 다음 페이지 로드 (무한 스크롤)
3. 카드 탭 → 상세 화면 이동

**필터 상태**:
```
SearchFilter {
  q: String?            // 검색어
  lat: Double?          // 위도
  lng: Double?          // 경도
  radiusKm: Double      // 반경 (기본 2)
  type: String?         // 설립유형
  sidoCode: String?     // 시도 코드
  sggCode: String?      // 시군구 코드
  sort: String          // 정렬 (distance/name)
}
```

**UI 구성**:
| 영역 | 컴포넌트 | 동작 |
|------|---------|------|
| 상단 | TextField 검색바 | 디바운스 검색 |
| 필터1 | 지역 드롭다운 (시도 → 시군구) | regions API 사용 |
| 필터2 | 설립유형 ChoiceChip | 전체/국공립/사립/법인 |
| 필터3 | 정렬 DropdownButton | 거리순/이름순 |
| 목록 | ListView + InfiniteScroll | 카드 탭 → 상세 |

**빈 상태**:
- 검색 결과 없음: "검색 결과가 없습니다" + 필터 초기화 버튼
- 위치 권한 없음: "위치 권한을 허용하면 주변 유치원을 찾을 수 있어요"

---

### 4.3 지도 화면

**진입 조건**: 하단 탭

**동작 흐름**:
1. 현위치 중심으로 지도 표시 + 마커 로드
2. 지도 카메라 이동 완료(onCameraIdle) → 새 중심점 기준 마커 재조회
3. 마커 탭 → 바텀시트에 간략 정보 표시
4. 바텀시트 "상세보기" → 상세 화면 이동

**마커 아이콘**:
| 설립유형 | 마커 색상 |
|---------|----------|
| 국공립 | 파란색 |
| 사립 | 주황색 |
| 법인 | 초록색 |
| 기타 | 회색 |

**UI 구성**:
| 영역 | 컴포넌트 | 동작 |
|------|---------|------|
| 상단 오버레이 | 설립유형 필터 칩 | 마커 필터링 |
| 상단 오버레이 | 현위치 FAB | 현위치로 카메라 이동 |
| 지도 | GoogleMap | 마커 표시, 카메라 이동 |
| 바텀시트 | DraggableScrollableSheet | 마커 탭 시 표시 |

**바텀시트 내용**:
- 유치원 이름
- 설립유형 뱃지
- 주소
- "상세보기" 버튼

**주의사항**:
- 카메라 이동 시 디바운스 500ms 적용 (과도한 API 호출 방지)
- 마커 개수 많을 때 클러스터링 고려 (MVP 이후)

---

### 4.4 유치원 상세 화면

**진입 조건**: 카드/마커 탭 → push

**동작 흐름**:
1. id로 상세 API 호출
2. 즐겨찾기 여부 확인 (로컬 캐시 또는 즐겨찾기 목록 조회)
3. 하트 아이콘 탭 → 즐겨찾기 추가/삭제

**UI 구성**:

**헤더 영역**:
| 항목 | 표시 | 동작 |
|------|------|------|
| AppBar 타이틀 | 유치원 이름 | - |
| AppBar 액션 | 하트 아이콘 | 즐겨찾기 토글 |
| 설립유형 | Chip | - |
| 주소 | Text + 복사 아이콘 | 클립보드 복사 |
| 전화번호 | Text + 전화 아이콘 | url_launcher로 전화 |
| 운영시간 | Text | - |
| 홈페이지 | Text + 링크 아이콘 | url_launcher로 브라우저 |
| 원장/대표 | Text | - |
| 설립일/개원일 | Text | - |

**탭 영역** (TabBar + TabBarView):

#### 교육 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| 연령별 학급수 | classCountByAge | 테이블 (3세/4세/5세/혼합/특수) |
| 연령별 정원 | capacityByAge | 테이블 |
| 연령별 현원 | enrollmentByAge | 테이블 |
| 수업일수 | lessonDaysAge3~5, Mixed | 리스트 |
| 법정일수 미만 | belowLegalDays | 텍스트 |

#### 급식 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| 운영형태 | mealOperationType | 텍스트 (직영/위탁) |
| 위탁업체 | consignmentCompany | 텍스트 (위탁일 때만) |
| 급식 인원 | mealChildren | 숫자 |
| 조리사 수 | cookCount | 숫자 |

#### 안전 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| 공기질 | airQualityCheck | 적합/부적합 뱃지 |
| 소독 | disinfectionCheck | 적합/부적합 뱃지 |
| 수질 | waterQualityCheck | 적합/부적합 뱃지 |
| 미세먼지 | dustMeasurement | 적합/부적합 뱃지 |
| 조도 | lightMeasurement | 적합/부적합 뱃지 |
| 화재보험 | fireInsuranceCheck | 가입/미가입 |
| 가스점검 | gasCheck | 적합/부적합 뱃지 |
| 전기점검 | electricCheck | 적합/부적합 뱃지 |
| 놀이시설 | playgroundCheck | 적합/부적합 뱃지 |
| CCTV | cctvInstalled, cctvTotal | Y/N + 대수 |
| 학교안전공제 | schoolSafetyEnrolled | 가입/미가입 |
| 교육시설공제 | educationFacilityEnrolled | 가입/미가입 |

#### 시설 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| 건축연도 | archYear | 년도 |
| 층수 | floorCount | 숫자 |
| 건물면적 | buildingArea | m² |
| 대지면적 | totalLandArea | m² |
| 교실 수 | classroomCount | 숫자 |
| 교실면적 | classroomArea | m² |
| 놀이터면적 | playgroundArea | m² |
| 통학버스 운영 | busOperating | Y/N |
| 운행 대수 | operatingBusCount | 숫자 |
| 등록 대수 | registeredBusCount | 숫자 |

#### 교사 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| **직급별** | | 섹션 헤더 |
| 원장 | directorCount | 숫자 |
| 원감 | viceDirectorCount | 숫자 |
| 수석교사 | masterTeacherCount | 숫자 |
| 보직교사 | leadTeacherCount | 숫자 |
| 일반교사 | generalTeacherCount | 숫자 |
| 특수교사 | specialTeacherCount | 숫자 |
| 보건교사 | healthTeacherCount | 숫자 |
| 영양교사 | nutritionTeacherCount | 숫자 |
| 직원 | staffCount | 숫자 |
| **자격별** | | 섹션 헤더 |
| 정교사(1급) | masterQualCount | 숫자 |
| 정교사(2급) | grade1QualCount | 숫자 |
| 준교사 | grade2QualCount | 숫자 |
| 보조교사 | assistantQualCount | 숫자 |
| **경력별** | | 섹션 헤더 |
| 1년 미만 | under1Year | 숫자 |
| 1~2년 | between1And2Years | 숫자 |
| 2~4년 | between2And4Years | 숫자 |
| 4~6년 | between4And6Years | 숫자 |
| 6년 이상 | over6Years | 숫자 |

#### 방과후 탭
| 항목 | 데이터 필드 | 표시 형태 |
|------|-----------|----------|
| 독립 편성 수업 수 | independentClassCount | 숫자 |
| 오후 편성 수업 수 | afternoonClassCount | 숫자 |
| 독립 편성 참여인원 | independentParticipants | 숫자 |
| 오후 편성 참여인원 | afternoonParticipants | 숫자 |
| 정규 교사 수 | regularTeacherCount | 숫자 |
| 계약 교사 수 | contractTeacherCount | 숫자 |
| 전담 직원 수 | dedicatedStaffCount | 숫자 |

**하단**:
- 데이터 기준일: `sourceUpdatedAt` 표시
- "지도에서 보기" 버튼 → 지도 화면으로 이동 (해당 좌표 중심)

---

### 4.5 비교 화면

**진입 조건**: 즐겨찾기 화면에서 2~4개 선택 후 "비교하기"

**UI 구성**: 가로 스크롤 테이블

| 비교 항목 | 데이터 필드 | 비고 |
|----------|-----------|------|
| 유치원명 | name | 헤더 행 (고정) |
| 설립유형 | establishType | |
| 주소 | address | |
| 거리 | distanceKm | km 단위, 위치 없으면 "-" |
| 정원 | capacity | |
| 현원 | currentEnrollment | |
| 교사 수 | teacherCount | |
| 학급 수 | classCount | |
| 급식 | mealProvided | O / X 아이콘 |
| 통학버스 | busAvailable | O / X 아이콘 |
| 연장돌봄 | extendedCare | O / X 아이콘 |
| 건물면적 | buildingArea | m² |
| 교실면적 | classroomArea | m² |
| CCTV | cctvInstalled, cctvTotal | Y(10대) 형태 |

**레이아웃**:
- 첫 번째 열(항목명) 고정
- 나머지 열 가로 스크롤
- 각 유치원 이름 탭 → 상세 이동

---

### 4.6 즐겨찾기 화면

**진입 조건**: 하단 탭

**동작 흐름**:
1. deviceId로 즐겨찾기 목록 조회
2. 스와이프 좌 → 삭제 확인 다이얼로그
3. 체크박스로 2~4개 선택 → 하단 "비교하기" 버튼 활성화
4. 카드 탭 → 상세 이동

**UI 구성**:
| 영역 | 컴포넌트 | 동작 |
|------|---------|------|
| 상단 | 타이틀 + 개수 | "즐겨찾기 (3)" |
| 목록 | CheckboxListTile + Dismissible | 선택/스와이프 삭제 |
| 하단 | ElevatedButton | "선택 비교하기 (2/4)" |

**빈 상태**: "즐겨찾기한 유치원이 없어요\n검색에서 하트를 눌러 추가해보세요"

---

### 4.7 설정 화면

**진입 조건**: 하단 탭

**UI 구성**:
| 항목 | 컴포넌트 | 동작 |
|------|---------|------|
| 기본 검색 반경 | SegmentedButton | 1km / 2km / 5km / 10km |
| 앱 버전 | ListTile | 표시만 |
| 오픈소스 라이선스 | ListTile | showLicensePage() |

---

## 5. 라우팅

```dart
GoRouter(
  routes: [
    StatefulShellRoute.indexedStack(
      branches: [
        // 탭 1: 홈
        StatefulShellBranch(routes: [
          GoRoute(path: '/', builder: HomeScreen),
        ]),
        // 탭 2: 검색
        StatefulShellBranch(routes: [
          GoRoute(path: '/search', builder: SearchScreen),
        ]),
        // 탭 3: 지도
        StatefulShellBranch(routes: [
          GoRoute(path: '/map', builder: MapScreen),
        ]),
        // 탭 4: 즐겨찾기
        StatefulShellBranch(routes: [
          GoRoute(path: '/favorites', builder: FavoritesScreen),
        ]),
        // 탭 5: 설정
        StatefulShellBranch(routes: [
          GoRoute(path: '/settings', builder: SettingsScreen),
        ]),
      ],
    ),
    // Push 화면
    GoRoute(path: '/detail/:id', builder: DetailScreen),
    GoRoute(path: '/compare', builder: CompareScreen),
  ],
)
```

---

## 6. 상태 관리 설계 (Riverpod)

### 주요 Provider

```
// 위치
locationProvider          → AsyncValue<Position?>       // 현위치
locationPermissionProvider → AsyncValue<bool>            // 권한 상태

// 기기
deviceIdProvider          → String                      // deviceId

// 지역
regionsProvider           → AsyncValue<RegionList>       // 지역 목록 (캐시)

// 검색
searchFilterProvider      → StateProvider<SearchFilter>  // 검색 필터 상태
searchResultProvider      → 페이징 처리 (infinite_scroll_pagination과 연동)

// 상세
kindergartenDetailProvider(id) → AsyncValue<KindergartenDetail>  // Family provider

// 지도
mapMarkersProvider(lat, lng, radiusKm, type) → AsyncValue<List<MapMarker>>

// 즐겨찾기
favoritesProvider         → AsyncValue<List<Favorite>>   // 즐겨찾기 목록
isFavoriteProvider(centerId) → bool                      // 특정 유치원 즐겨찾기 여부

// 비교
compareIdsProvider        → StateProvider<List<String>>  // 선택된 비교 ID들
compareResultProvider     → AsyncValue<CompareResponse>  // 비교 결과
```

---

## 7. 개발 순서 (권장)

| 순서 | 작업 | 예상 산출물 |
|------|------|-----------|
| 1 | 프로젝트 초기화 + core 설정 | Dio, 테마, 라우터, deviceId |
| 2 | 모델 클래스 전체 작성 | models/ 전체 |
| 3 | Repository 전체 작성 | repositories/ 전체 |
| 4 | 검색 화면 + 목록 카드 | search/, widgets/ |
| 5 | 상세 화면 (6개 탭) | detail/ |
| 6 | 지도 화면 | map/ |
| 7 | 즐겨찾기 기능 | favorites/, Provider |
| 8 | 비교 화면 | compare/ |
| 9 | 홈 화면 (조합) | home/ |
| 10 | 설정 화면 | settings/ |
| 11 | 에러/빈 상태 처리 | 공용 위젯 |
| 12 | 테스트 + 폴리싱 | - |
