# StoryAI 진행 현황 / 다음 작업 (2026-07-14 갱신)

우리 아이 사진 → AI가 개인화 동화책(PDF)/영상 만들어주는 서비스.
경쟁 벤치마크: talecoco.com (파스텔 반실사, 페이지 단가제).

---

## ✅ 지금까지 완료 (깃허브 main에 반영됨)

- **AI 파이프라인**: 사진 업로드 → 캐릭터 시트(평상복+주제의상) → 페이지별 삽화 → 가로 PDF. Gemini(나노바나나)로 스토리 글 + 그림 모두 생성.
- **캐릭터 일관성**: 참조 이미지로 같은 아이 얼굴 유지, 표지=실제 옷 → 중간에 주제 의상으로 변신 연출.
- **연령별 텍스트**: 3~4 / 5~6 / 7~8 / 9~10세 (길이·의성어 조절).
- **헌정 메시지** → 헌정 페이지, **스토리 방향**(선택) 입력 반영.
- **사진 크롭**: 부모+아이 사진에서 아이만 잘라서 업로드.
- **그림 스타일 5종**(동화/스케치/색연필/수채화/카툰) + **주제 11종**(공주/왕자/용사/모험/동물친구/우주/공룡/바다/숲속/정글/해적) + 주제별 의상.
- **손글씨 폰트**(제목 Jua, 본문 Gaegu), 좌/우/상하/풀블리드 레이아웃 혼합.
- **가격 표시 숨김**(결제 단계에서 노출 예정).
- **그림 꽉 채우기**: 여백 제거(cover) + **가로(3:2) 그림 생성** → 페이지 끝까지 그림이 참. (검증: 1264×848 landscape 확인)
- 24페이지 / 36페이지 둘 다 생성 가능, 삽화 장수 제한 없음(전체 생성).
- **[07-14] 유령 인물 버그 수정**: 주인공 1명인데 2명 나오던 것 → 스토리·삽화 프롬프트에 "지정된 N명만, 친구·형제 발명 금지" 강제. (검증: 1명만 생성 확인)
- **[07-14] 빈 페이지 방지**: 삽화 생성 실패(503 등) 시 재시도 + 실패 시 직전 삽화/시트 재사용 → **어떤 경우에도 빈 페이지 없음**. (E2E 검증: 100% 실패 상황에서도 빈 박스 0)
- **[07-14] 헌정 페이지에 가족 사진**: 업로드한 가족 사진을 AI 변환 없이 흰 액자에 원본 그대로 삽입.
- **[07-14] 연령별 텍스트 정밀 튜닝**: 3~4세(≈13자·의성어 매 페이지) ~ 9~10세(≈90자·서사적) 실제 7배 차이 확인.
- **[07-14] 표지 강화 + 텍스트 여백 제거**: 표지=장면 풀블리드+제목 밴드, 본문=풀블리드 그림+반투명 손글씨 밴드(상/하 교차). 빈 패널 사라짐.
- **[07-14] 미리보기 2단계 + 구매선택 + 이메일**: 아래 참고.

---

## ✅ [07-14 완료] 미리보기 2단계 · 구매선택 · 전송

- **미리보기 먼저**: 책은 `bookPhase=PREVIEW`로 시작 → 표지+헌정+앞 4장(=최대 6장)만 생성 → 짧은 PDF. (설정: `storyai.book.preview-pages`, 기본 4)
- **확정 API**: `POST /api/video-jobs/{id}/confirm {purchaseType, deliveryEmail}` → `bookPhase=FULL`로 전환, PAGE_ILLUSTRATION부터 재개. **미리보기 4장은 재생성 안 하고 재사용**(비용 절약).
- **구매 선택**: 프론트 미리보기 화면에서 PDF/실물책 + 이메일 입력 + "전체 만들기".
- **전송**: `EmailNotifier`는 현재 **스텁(로그만)**. 실제 발송은 아래 "남은 작업" 참고.
- 검증(E2E): 미리보기 PDF=6p → 확정 → 전체 PDF=26p, 로그 "생성4, 재사용4", 이메일 스텁 발화 확인.

---

## ⏳ 남은 작업

### 0) [진행중] 로그인 (구글/카카오) — 다음 세션 이어서
현재까지: 배포 완료(https://todayhero.co.kr). CORS 코드는 인증정보 허용으로 바꿈(`675e7bc`). **로그인 코드는 아직 시작 안 함.**
남은 절차(순서대로):
1. **[사장님] Railway 변수** `CORS_ALLOWED_ORIGINS=https://todayhero.co.kr,https://www.todayhero.co.kr,https://dapper-selkie-898d41.netlify.app` 추가.
2. **[사장님+안내] api.todayhero.co.kr 붙이기** — Railway StoryAI → Settings → Networking → Custom Domain에 `api.todayhero.co.kr` 입력 → 나온 CNAME 대상을 Netlify DNS(도메인 관리)에 CNAME `api`로 등록. (쿠키 로그인을 같은 도메인 계열로 안정화)
   - 그 후 Netlify `VITE_API_BASE=https://api.todayhero.co.kr`, Railway `CORS_ALLOWED_ORIGINS=https://todayhero.co.kr`로 정리.
3. **[사장님+안내] OAuth 앱 등록**: Google Cloud OAuth 클라이언트 + Kakao Developers 앱 → client id/secret. 리다이렉트 URI = `https://api.todayhero.co.kr/login/oauth2/code/{google|kakao}`.
4. **[개발] 백엔드 코드**: `spring-boot-starter-oauth2-client`+`security` 추가, User 엔티티, SecurityConfig(OAuth2 로그인, /api/health·/api/options 공개), 카카오 커스텀 provider, `/api/me`, 로그인/로그아웃. 키는 env(GOOGLE_CLIENT_ID/SECRET, KAKAO_CLIENT_ID/SECRET).
5. **[개발] 프론트**: 로그인 버튼(구글/카카오), 로그인 상태 표시, 주문을 회원에 연결.
6. **[개발] 회원별 생성 제한** (IP 제한 → 회원 기준으로).

### 1) 실제 이메일 발송 (지금 스텁) — 외부 계정 필요
- `build.gradle`에 `spring-boot-starter-mail` 추가 → `application.yml`에 SMTP(예: Gmail 앱비밀번호/SendGrid) → `EmailNotifier.send()`를 JavaMailSender+PDF첨부로 교체.
- **카카오톡**: 사업자등록 + 채널 + 알림톡 심사 필요 → 이후.
- 👉 **결정 필요: 어떤 이메일 계정/서비스로 발송할지.**

### 2) 결제 연동 (현재 미리보기 후 정보만 수집)
- 확정 시 실제 결제(PG) 붙이기. 그 전엔 무료로 전체 생성되므로 **공개 시 반드시 필요.**

### 3) 배포
- 프론트 → **Netlify(무료)**, 백엔드+DB → **Railway(추천)**. AWS는 지금 불필요.
- 배포 전 필수: **영구 DB(H2→PostgreSQL)**, **비용 상한/요청 제한**, 도메인+HTTPS, 개인정보 약관(국외이전 동의 포함).

### 4) 품질 보강(선택)
- 미리보기에서 "안 닮은 페이지 재생성" 버튼(부모 만족도 방어).
- 삽화 병렬 생성으로 시간 단축(현재 순차 ~13초/장).

## ⚠️ 결정 대기 중 (사장님)
1. 완성본 전송 이메일 **계정/서비스** (Gmail? SendGrid?)
2. 배포 조합 = Netlify + Railway 진행?
3. 가비아 도메인 구매

---

## 💰 비용 메모
- 이미지 장당 ≈ 55~70원. 24p 책 ≈ 1,600원, 36p ≈ 2,200원, **미리보기(≤6장) ≈ 330원**.
- 모든 생성이 사장님 Gemini 키로 과금 → **결제/요청제한 붙이기 전엔 공개 금지.**
- Google AI Studio / Cloud Billing에서 일자별 확인. **예산 상한 설정 권장.**

## 🖥️ 로컬 실행 (개발 PC는 가상화 꺼져 Docker 불가 → H2 dev 프로파일)
- 백엔드: `backend/` 에서 `gradlew.bat bootRun --args=--spring.profiles.active=dev` (GEMINI_API_KEY 환경변수 주입)
- 프론트: `frontend/` 에서 `npm run dev` (→ :5173, /api는 :8080 프록시)
- 주의: H2 메모리라 백엔드 재시작 시 업로드/주문 데이터 초기화됨(사진 파일은 디스크에 남음).

## 🏠 다른 PC(집)에서 이어서 하기
1. `git clone https://github.com/lg56607213/StoryAI` (또는 기존 폴더면 `git pull`).
2. **`.env` 재생성** (gitignore라 리포에 없음): 루트에 `.env` 만들고 `GEMINI_API_KEY=AQ.…`(Google AI Studio 키) 한 줄.
3. 필요 도구: **JDK 21**, **Node 22**. (로컬 테스트할 때만 — 코드 수정→push하면 Railway/Netlify 자동 재배포되므로 라이브에서 바로 확인도 가능)
4. **Claude Code를 StoryAI 폴더에서 열고** "로그인 작업 이어서 하자" 라고 하면 위 "0) 로그인" 절차부터 진행.
5. 로그인/배포 설정에 필요한 계정 로그인 준비: **Railway, Netlify, Google Cloud, Kakao Developers**.
- 참고: 코드 작성은 Claude가, 앱 등록·대시보드 클릭은 사장님이 (Claude가 화면 보며 안내).
