# AI Story Engine (StoryAI)

사진을 업로드하면 AI가 주인공을 선정하고 주제에 맞는 스토리를 생성해 짧은 영상으로 만들어주는 플랫폼.

## 구조

```
StoryAI/
├── backend/    # Spring Boot 3.5 (Java 21, Gradle)
├── frontend/   # React + TypeScript (Vite)
└── docker-compose.yml   # MySQL, Redis (로컬 개발용)
```

## 로컬 개발 시작

### 1. 인프라 (MySQL, Redis)

```bash
docker compose up -d
```

### 2. 백엔드

```bash
cd backend
./gradlew bootRun
```

기본 포트: `http://localhost:8080` — 헬스체크: `GET /api/health`

환경변수는 `.env.example`을 참고해 `.env`로 복사 후 채워 넣는다 (Spring 실행 시 시스템 환경변수로 주입 필요 — IDE run config 또는 `--env-file` 방식 사용).

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

기본 포트: `http://localhost:5173`

## 파이프라인 개요

```
사진 업로드 → Story AI(대본 생성) → Scene 분리 → Character 분석
  → Image 생성 → Video 생성 → Voice(나레이션) → 자막 생성 → 영상 합성(FFmpeg) → MP4 출력
```

각 단계는 `VideoJob` 상태 머신으로 비동기 처리하며, 외부 AI 연동은 벤더 교체가 쉽도록 인터페이스로 추상화한다.

## 다음 단계

- [ ] AI 벤더별 API 원가 측정 (Story/Image/Video/Voice)
- [ ] VideoJob 상태 머신 및 도메인 모델 설계
- [ ] 외부 AI 클라이언트 인터페이스 (StoryGenerator, ImageGenerator, VideoGenerator, VoiceGenerator)
- [ ] S3 업로드 연동
- [ ] FFmpeg 합성 워커
