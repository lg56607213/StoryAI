# StoryAI 배포 가이드 (Netlify + Railway)

구성: **프론트 → Netlify(무료)**, **백엔드 → Railway**, **DB → Railway MySQL**, **파일 → Railway 볼륨**, 도메인 → 가비아.

> 상태 표기: ✅ 코드 완료(계정만 있으면 됨) · ⏳ 코드 남음(사장님 키 필요) · 👤 사장님 준비

---

## 1) 배포 전 체크리스트

### 코드 (앱 준비 상태)
- ✅ **DB**: 기본 프로파일이 이미 MySQL(env 분리). dev만 H2. → Railway MySQL 붙이면 됨.
- ✅ **파일 저장**: `STORAGE_DIR` 환경변수로 경로 지정 → Railway **볼륨** 경로로 두면 재시작해도 유지.
- ✅ **CORS**: `CORS_ALLOWED_ORIGINS`로 프론트 도메인 허용.
- ✅ **프론트 API 주소**: `VITE_API_BASE`로 백엔드 도메인 지정(빌드 시 주입).
- ✅ **요청 제한**: `RATE_LIMIT_JOBS_PER_DAY`(IP당 하루 만들기 수). 배포 시 20 등으로.
- ✅ **폰트**: 번들 리소스(Jua/Gaegu) 사용 → 리눅스에서도 동작.
- ⏳ **로그인**(구글/카카오 OAuth2): 사장님이 앱 등록 → 키 주면 연동.
- ⏳ **결제(PG)**: 확정 시 결제 붙이기 (그 전엔 무료 전체생성이라 공개 시 필수).
- ⏳ **이메일 발송**: 현재 스텁(로그). SMTP 계정 주면 실연동.

### 계정·법적 (👤 사장님)
- 👤 Railway / Netlify 가입, 가비아 도메인
- 👤 Google Cloud OAuth 앱, Kakao Developers 앱 (로그인용)
- 👤 이메일 발송 계정(Gmail 앱비밀번호 / SendGrid)
- 👤 결제 PG 가맹(토스페이먼츠/포트원 등)
- 👤 **이용약관 + 개인정보처리방침(구글 국외이전 동의 포함)** — 변호사 검토 권장
- 👤 **Google AI Studio 예산 상한** 설정

---

## 2) 백엔드 환경변수 (Railway에 설정)

| 변수 | 예시 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (비움) | 기본 프로파일=MySQL. dev 넣지 말 것 |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | Railway MySQL 정보 | DB 접속 |
| `STORAGE_DIR` | `/data/storage` | **볼륨 마운트 경로**(중요: 안 하면 파일 유실) |
| `GEMINI_API_KEY` | AQ.… | 이미지·스토리 생성 |
| `CORS_ALLOWED_ORIGINS` | `https://내도메인` | 프론트 도메인 |
| `RATE_LIMIT_JOBS_PER_DAY` | `20` | 비용 방어 |
| `SERVER_PORT` | Railway가 주는 `$PORT` | 포트 |

## 3) 프론트 환경변수 (Netlify)
| 변수 | 예시 |
|---|---|
| `VITE_API_BASE` | `https://api.내도메인` (백엔드 주소) |

---

## 4) 배포 순서 (의존성 순 — 앞 단계 주소가 뒤 단계에 필요)

1. **Railway 프로젝트 생성 → MySQL 추가** → DB 접속정보 확보
2. **Railway 볼륨 생성**, 백엔드 서비스에 마운트(`/data`), `STORAGE_DIR=/data/storage`
3. **백엔드 배포**(GitHub 연결, `backend/` 빌드) + 위 env 설정 → **백엔드 URL** 확보
   - 헬스체크: `GET /api/health`
4. (로그인 붙일 때) 구글/카카오 **리다이렉트 URI** 등록 = `백엔드URL/login/oauth2/...`
5. **프론트 배포**(Netlify, `frontend/` 빌드, `VITE_API_BASE=백엔드URL`) → **Netlify URL** 확보
6. **가비아 도메인 연결**: 루트 도메인 → Netlify, `api.도메인` → Railway (DNS)
7. 최종 도메인으로 `CORS_ALLOWED_ORIGINS`, OAuth 리다이렉트 갱신
8. (결제 붙이면) PG 키 설정
9. **라이브 E2E 테스트**: 사진 업로드 → 미리보기 → (결제) → 전체 생성 → 다운로드/이메일

---

## 5) 배포 후 반드시 확인
- 재시작해도 **주문·파일이 유지되는지** (DB·볼륨 정상)
- 다른 브라우저/폰에서 접속되는지 (CORS)
- 생성 요청 한도(429) 동작 확인
- Google 결제 대시보드에서 비용 추이 + 예산 알림

## 6) 아직 안 붙은 것(공개 전 필수)
- **결제** (없으면 무료로 무제한 생성됨)
- **로그인 + 회원별 제한** (IP 제한은 임시 방어일 뿐)
- **약관/개인정보** (아동 사진 취급)
