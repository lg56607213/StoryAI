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

## 2) 백엔드 환경변수 (Railway → 백엔드 서비스 → Variables)

| 변수 | 값 | 설명 |
|---|---|---|
| `DB_HOST` | `${{MySQL.MYSQLHOST}}` | Railway MySQL 참조 |
| `DB_PORT` | `${{MySQL.MYSQLPORT}}` | 〃 |
| `DB_NAME` | `${{MySQL.MYSQLDATABASE}}` | 〃 |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` | 〃 |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | 〃 |
| `STORAGE_DIR` | `/data/storage` | **볼륨 경로**(안 하면 파일 유실) |
| `GEMINI_API_KEY` | `AQ.…` | 이미지·스토리 생성 |
| `CORS_ALLOWED_ORIGINS` | `https://<netlify주소>` | 프론트 도메인 |
| `RATE_LIMIT_JOBS_PER_DAY` | `20` | 비용 방어 |

> `${{MySQL.XXX}}`는 Railway의 "변수 참조" 문법 — 백엔드가 MySQL 서비스 값을 자동으로 물어옴. `PORT`는 Railway가 자동 주입(따로 설정 X). `SPRING_PROFILES_ACTIVE`는 **비워둠**(기본=MySQL).

## 3) 프론트 환경변수 (Netlify → Site settings → Environment variables)
| 변수 | 값 |
|---|---|
| `VITE_API_BASE` | `https://<railway-백엔드-도메인>` |

---

## 4) 배포 순서 (클릭 단위 — 앞 단계 주소가 뒤에 필요)

**리포에 이미 준비됨**: `backend/Dockerfile`(백엔드 빌드), `netlify.toml`(프론트 빌드).

1. **Railway → New Project → Deploy from GitHub** → StoryAI 선택
2. 백엔드 서비스 설정: **Settings → Root Directory = `backend`** (Dockerfile 자동 인식)
3. 같은 프로젝트에 **+ New → Database → MySQL** 추가
4. **+ New → Volume** 생성 → 백엔드 서비스에 **Mount path = `/data`**
5. 백엔드 **Variables**에 위 2)의 변수들 입력 → 자동 재배포
6. 백엔드 **Settings → Networking → Generate Domain** → **백엔드 URL 확보**
   - 확인: 브라우저에서 `백엔드URL/api/health`
7. **Netlify → Add new site → Import from GitHub** → StoryAI 선택 (netlify.toml이 빌드 자동 설정)
8. Netlify **환경변수 `VITE_API_BASE = 백엔드URL`** 입력 → 재배포 → **Netlify URL 확보**
9. 백엔드 `CORS_ALLOWED_ORIGINS`를 **Netlify URL**로 갱신
10. (도메인) 가비아 DNS: 루트 → Netlify, `api.도메인` → Railway. 그 후 CORS/VITE_API_BASE를 최종 도메인으로 갱신
11. **라이브 E2E**: 사진 업로드 → 미리보기 → (결제) → 전체 생성 → 다운로드

> 로그인(구글/카카오) 리다이렉트 URI 등록은 백엔드 URL이 정해진 뒤(6번 이후) 로그인 붙일 때 진행.

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
