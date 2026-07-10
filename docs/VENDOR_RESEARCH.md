# 이미지 생성 벤더 조사 (2026-07)

동화책/영상의 **삽화 생성 + 캐릭터(아이 얼굴) 일관성**을 위한 이미지 생성 API 비교.
평가 기준: ① 얼굴 일관성(참조 기반) ② 화풍(파스텔·근사 실사) ③ API/장당 비용 ④ 아동·실존인물 정책 ⑤ 상업 라이선스.

---

## 0. 가장 중요한 결론 (먼저 읽기)

> **"실사 그대로"는 사실상 막혀 있고, 방향을 "실사에 가까운 파스텔 스타일"로 잡는 것이 정답입니다.**

- **OpenAI(gpt-image)**: *아동의 photorealistic(실사) 묘사 생성은 명시적으로 금지.*
- **Google(Gemini/Nano Banana)**: *실존 식별 가능 인물의 photorealistic 이미지 차단(2026-02 강화, 얼굴 스왑 포함).*
- 즉 **유아 실사 얼굴 생성은 주요 상용 API에서 정책 위반** → 계정 정지·법적/브랜드 리스크.
- 참고한 **talecoco도 실사가 아니라 "파스텔톤으로, 사진과 거의 흡사하게"** = 스타일라이즈드 **근사**입니다. 순수 실사가 아님.
- 스타일라이즈(일러스트/파스텔)는 (1) 정책 통과 (2) 페이지 간 얼굴 일관성이 훨씬 잘 잡힘 (3) 경쟁사와 동일 노선 → **모든 면에서 유리**.

**→ 제품 표현을 "실사 유지"에서 "실사에 가까운 파스텔 스타일라이즈"로 조정 권장.**

---

## 1. 후보 비교

| 벤더 / 모델 | 얼굴 일관성 | 화풍 | 장당 비용(1K) | 아동·실존인물 정책 | 접근·상업성 |
|---|---|---|---|---|---|
| **Nano Banana (Gemini Flash Image, Google)** | ★★★★ 스토리텔링 일관성 특화 | 다양·근사 실사 가능 | ~$0.03–0.06 | 실존인물 실사 제한 (스타일라이즈는 의도된 용도) | Gemini API / fal / OpenRouter |
| **gpt-image (OpenAI)** | ★★★★ 멀티 참조 | 최상 품질 | $0.009(저)–0.13(고) | **아동 실사 금지** | OpenAI API |
| **Ideogram Character** | ★★★★★ 단일 참조 정체성 락 (비교 테스트 1위) | 일러스트 | 플랜제 | 실존인물 실사 제한 | Ideogram API |
| **FLUX / FLUX Kontext (Black Forest Labs)** | ★★★☆ 편집·참조 기반 | 유연·파인튜닝 여지 | $0.015–0.055 | 상대적으로 유연(오픈웨이트 계열) | BFL API / fal / Replicate |
| **fal.ai · Replicate (애그리게이터)** | 모델별 | 모델별 | 모델별 | 모델별 | 여러 모델 단일 API |

**애그리게이터(fal.ai/Replicate) 위 세부 모델**
- **PhotoMaker** (fal): 얼굴 정체성 보존, **상업 사용 가능**.
- **InstantCharacter** (fal): 텍스트→일관 캐릭터, 강한 정체성 제어.
- **InstantID** (Replicate): 사진 1장으로 정체성 보존. ⚠️ **주의**: 코드(Apache-2.0)와 별개로 얼굴 인코더(InsightFace)가 **비상업 연구용** → 상업 배포 시 라이선스 갭. 대체 인코더 필요.

---

## 2. 비용 시뮬레이션 (삽화 생성 원가)

- 24페이지 ≈ 삽화 25장 × ~$0.03 ≈ **$0.75/권** (약 1,000원)
- 36페이지 ≈ 37장 × ~$0.03 ≈ **$1.1/권** (약 1,500원)
- 스토리(Claude)는 권당 수백 원.
- 재생성/실패분 버퍼 2–3배 감안해도 원가 수천 원 → **판매가 2만~3만원 대비 마진 충분.**
- 영상은 장당·초당 비용이 훨씬 커서 별도 산정 필요(후순위).

---

## 3. 권장 스택 (MVP)

| 역할 | 권장 | 이유 |
|---|---|---|
| 스토리(글) | **Claude (Sonnet/Opus)** | 유아 눈높이·한국어·안전, 이미 `.env`에 키 자리 |
| 삽화(그림) | **Nano Banana(Gemini Flash Image)** 또는 **FLUX Kontext** | 캐릭터 일관성 강 + 파스텔·근사 실사 가능 |
| 접근 방식 | **fal.ai** (애그리게이터) | 여러 모델을 단일 API로 실험·교체, 상업 가능 모델 선택 |
| 일관성 기법 | **캐릭터 시트 먼저 1회 생성 → 페이지마다 참조로 재사용** | 2026 표준 워크플로우 |

**대안**: 최고 정체성 락이 필요하면 **Ideogram Character**, 커스터마이즈/파인튜닝 여지를 원하면 **FLUX**.

---

## 4. 법무·안전 체크리스트 (유아 대상 필수)

- [ ] 보호자 동의 획득 (결제·업로드 주체 = 보호자)
- [ ] 아동 사진 **암호화 보관 + 자동 삭제(TTL)** 정책
- [ ] **photorealistic 아동 얼굴 미생성** — 스타일라이즈로 통일
- [ ] 선택한 벤더 약관의 아동·실존인물 조항 재확인
- [ ] 상업 라이선스 확인 (특히 InstantID/InsightFace 계열)

---

## 출처
- Nano Banana / Gemini Flash Image: https://developers.googleblog.com/en/introducing-gemini-2-5-flash-image/ , https://openrouter.ai/google/gemini-3.1-flash-image , https://ai.google.dev/gemini-api/docs/image-generation
- 가격 비교: https://www.buildmvpfast.com/api-costs/ai-image , https://pricepertoken.com/image , https://pricepertoken.com/gpt-image-pricing
- 캐릭터 일관성 개요: https://blog.mage.space/article/best-ai-image-generators-consistent-characters-2026/392f47f0-6619-4021-9b07-ba3ed8c86ba8 , https://ideogram.ai/features/character/
- 정책(실존인물·아동): https://openai.com/policies/usage-policies/ , https://blog.laozhang.ai/en/posts/gemini-image-generation-people-restriction
- 얼굴 정체성 모델: https://fal.ai/models/fal-ai/photomaker , https://fal.ai/models/fal-ai/instant-character , https://replicate.com/zsxkib/instant-id
- 경쟁사 예시: https://toonystory.com/ , https://www.childbook.ai/
