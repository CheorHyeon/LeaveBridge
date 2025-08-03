# LeaveBridge
연차·공가·반차를 엑셀 대신 웹으로 관리하고, Google Calendar 와 버튼 한 번으로 일정을 동기화할 수 있는 사내 휴가 관리 시스템
- 근무 형태 반영  : 일반직·파견직의 상이한 근무시간(09–18 vs 08–17)·대체휴일 적용 여부를 한 시스템에서 처리
- Google Calendar 연동 : 파견직 이벤트는 자동으로 개인 Google Calendar 에 등록·수정·삭제
- 휴일 자동 보정  : 공휴일·기념일 등록 시 해당 구간의 연차 시간을 자동 재계산

## 개발 정보

### 스택
| Layer    | Tech                                                                                     |
|----------|------------------------------------------------------------------------------------------|
| Back-end | Java 21, Spring Boot 3.3.9, Spring Security, JPA, Query DSL                                  |
| DB       | MySQL 8                                                                         |
| Front    | Thymeleaf, FullCalendar.js, Bootstrap                                                                |
| DevOps   | AWS EC2, ELB, AWS RDS (Aurora MySQL 8)    |

### AWS Architecture

<img width="1013" height="379" alt="image" src="https://github.com/user-attachments/assets/ce6c8b5d-0d8a-4f14-8a9a-5e7e330eb004" />



## Table of Contents
- [사전 작업](#setup)
- [주요 API 설명](#api)
- [제약 사항 · 향후 과제](#constraints)


---------------

## 사전 작업 <a id="setup"></a>

### 설치 & 토큰 발행
- 세팅에 필요한 Google Calendar Access Token 발급 절차는 블로그에 단계별로 정리돼 있습니다.
[Google Calendar API 발행](https://velog.io/@puar12/Google-Calendar-API-Spring-Boot-%EC%97%B0%EB%8F%99%ED%95%98%EA%B8%B0)

### yml 파일 수정
```yml
google:
  calendar-id: "personal calendar Id"   # Google Calendar Id
data:
  secret-key : "personal Open API Key"  # Open API Key 
```

- DB 접속 정보 변경
- `application-secret.yml.template` 파일의 확장자를 `.template` 제거
  - 다룰 Calendar 설정에서 `Calendar Id` 추출하여 입력
  - Open API - [한국천문연구원_특일 정보](https://www.data.go.kr/data/15012690/openapi.do) API 신청하여 API Key 입력

-----------------------------

## 주요 API 설명 <a id="api"></a>

### 일정 목록 조회 (`GET /api/v1/calendar/events/{year}/{month}`)

- 입력 월에 걸쳐 있는 모든 이벤트 반환 (예: 6/30 – 7/2 포함)
  - 월말 이전에 시작 & 해당 월 초 이후에 끝나는 범위 지정
<img alt="image" src="https://github.com/user-attachments/assets/946a4556-b602-4c0b-88c7-63deff77d2dc" />

### 일정 상세 조회(`GET /api/v1/calendar/events/{eventId}`)

- 해당 일정에 대한 상세 조회 : 일정 제목, 일정 상세, 설명, 하루종일여부, 수정 가능한지 여부 등
  - 관리자 or 작성자일 경우 true 반환
  - true일 경우에만 Modal에 `수정`, `삭제` 버튼 노출됨
 
### 일정 등록(`POST /api/v1/calendar/events`)

#### (관리자) 기념일 또는 공휴일 등록

- 휴일 포함 O : 해당 기간 연차 ‑> 자동 재계산 (파견직은 공휴일만 영향)
  - 연차가 0일이 되면 이벤트는 삭제
- 휴일 포함 X : 기존 일정 영향 없음

##### 기존 일정

<img alt="image" src="https://github.com/user-attachments/assets/bacd5786-5ef4-4097-855f-cfc2f45ed122" />

##### 관리자 공휴일 등록 이후 연차 사용일 조정

<img alt="image" src="https://github.com/user-attachments/assets/1832e32a-7110-4c9b-956b-66b3979ce877" />

- 만약 0일이 되는 일정이 된다면 그 연차 일정은 삭제 처리한다.

#### (직원) 연차 등록

| 구분      | 오전 반차 시작    | 오후 반차 시작    | 근무 종료 시간 |
| ------- | ----------- | ----------- | ---------------|
| **파견직** | **08 : 00** | **13 : 00** | **17 : 00**|
| **일반직** | **09 : 00** | **14 : 00** | **18 : 00** |

- 전일 연차는 두 그룹 모두 근무 시작, 종료 시간 전체를 차감하여 별도 시간 지정 필요 없음
- 파견직의 경우 일정 등록 시 구글 캘린더에도 등록된다.
- 공결 연차 타입의 경우 사유를 반드시 적어야 한다.

##### 휴일 포함한 연차 등록 가능

- 중간에 휴일이 포함된 연차 등록이 가능
- 실제 사용 연차에서는 해당 휴일 일수만큼은 연차 차감 일수에 포함되지 않는다.
  - 단, 기념일의 경우 **파견직**에는 적용되지 않는다.
  - 아래 이미지는 시간 기준으로 나타냈지만, 특정 일자도 포함된다.
<img alt="image" src="https://github.com/user-attachments/assets/a23add46-f525-48ba-9d99-cd2771a2ce1e" />

> 예시 1 ) 8월 13일 ~ 8월 15일 등록 -> 공휴일인 8월 15일은 제외한 3일이 아닌 2일 연차로 계산된다. <br>
> 예시 2 ) 8월 18일 ~ 8월 22일 등록 -> 기념일 8월 20일 -> 일반직의 경우 4일 연차 사용, 파견직의 경우 5일 연차 사용으로 계산된다.

### 일정 수정(Patch `/api/v1/events/{eventId}`)

- (관리자) 공휴일, 기념일 일정은 수정할 수 없다.
  - 수정하고 싶다면 일정 삭제하고 재생성 필요
- (관리자) 파견직 Google 일정은 미삭제 (직접 삭제 요청)
- 일정 수정 시 휴일 포함되어 실제 사용일 수가 0일이 된다면 수정이 불가능하다.
  - 수정이 필요하다면 삭제하고 재생성 필요
- 파견직의 경우 구글 캘린더의 일정도 수정된다.

### 일정 삭제(DELETE `/api/v1/events/{eventId}`)

- (관리자) 휴일 삭제 시 해당 휴일 기간에 영향 받았던 연차 재계산
- (관리자) 일정 삭제 시 구글 캘린더 일정은 미삭제 (직접 삭제 요청)
- 파견직의 경우 일정 삭제 시 구글 캘린더 일정도 같이 삭제된다.

#### (기존) 공휴일 포함된 일정일 때

<img alt="image" src="https://github.com/user-attachments/assets/659da95e-24be-401e-9b48-8007b7b85216" />

#### (삭제 후) 공휴일 삭제되었을 때

<img alt="image" src="https://github.com/user-attachments/assets/48585a56-62af-4b58-a34e-6bca9906c440" />

- 삭제된 연차만큼 추가된다.

### 연차 사용 현황 조회 (GET `/api/v1/members/{memberId}/used-leaves`)

- 이전에 사용자 목록은 `/api/v1/members` 로 조회 가능하고, 해당 Member Id를 이용하여 요청한다.
- 연도별 페이징 사용 내역 조회가 가능하다.


------------------

## 제약 사항 · 향후 과제 <a id="constraints"></a>

| 구분                     | 내용                                                                                                 |
|--------------------------|------------------------------------------------------------------------------------------------------|
| 근로기준법 연차 계산     | 입사 연도·근속기간별 연차 부여 로직 미적용                             |
| 연차 한도 초과 시 검증   | 연차 사용 가능 일수를 초과하면 등록/수정이 불가하도록 처리 필요                                     |

