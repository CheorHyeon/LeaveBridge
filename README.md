# LeaveBridge
- 구글 캘린더와 연동하여 일정 관리하는 CRUD 프로젝트

## 사전 작업

### 브라우저 상 설정

- Google Cloud 프로젝트 생성
  - [Google Cloud](https://console.cloud.google.com/)

- API 사용 설정 & OAuth 동의 화면 구성
  - 데이터 액세스 : Google Calendar 범위 추가
  - 공유 대상 : 자신의 구글 계정 추가

- 데스크톱 애플리케이션의 사용자 인증 정보 승인
  - 생성되는 json 파일 `resources` 폴더 하단에 위치

- 참고 : [자바 빠른 시작](https://developers.google.com/workspace/calendar/api/quickstart/java?hl=ko)

### 설정 -Google Calendar Id 입력하기
- `application-secret.yml.template` 파일의 확장자를 `.template` 제거
- 다룰 Calendar 설정에서 `Calendar Id` 추출
