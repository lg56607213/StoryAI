import { useEffect, useRef, useState } from 'react'
import './App.css'
import ImageCropper from './ImageCropper'
import Landing from './Landing'
import AdminDashboard from './AdminDashboard'
import MyPage from './MyPage'
import ParentVoiceRecorder from './ParentVoiceRecorder'
import {
  apiUrl,
  confirmProject,
  createProject,
  getMe,
  getOptions,
  getProject,
  loginUrl,
  logout,
  startNarrationVideo,
  testEmail,
  uploadPhotos,
  type CharacterInput,
  type JobResponse,
  type Me,
  type Options,
  type ThemeOption,
} from './api'

interface CropQueue {
  charIdx: number
  files: File[]
  pos: number
}

const STEP_LABELS: Record<string, string> = {
  STORY_GENERATION: '스토리 생성 중',
  PAGE_PLANNING: '페이지 구성 중',
  CHARACTER_ANALYSIS: '캐릭터 분석 중',
  PAGE_ILLUSTRATION: '삽화 그리는 중',
  PDF_GENERATION: 'PDF 만드는 중',
  SCENE_SPLIT: '장면 나누는 중',
  IMAGE_GENERATION: '이미지 생성 중',
  VIDEO_GENERATION: '영상 만드는 중',
  VOICE_GENERATION: '나레이션 녹음 중',
  SUBTITLE_GENERATION: '자막 만드는 중',
  VIDEO_COMPOSITION: '영상 합성 중',
}

/** 주제 "직접입력"을 나타내는 내부 값(백엔드 enum이 아니므로 전송 시 customTheme으로 변환). */
const CUSTOM_THEME = '__CUSTOM__'

// 진행 상황 표시용 단계 순서(짧은 라벨). 백엔드 WorkflowPlan과 순서를 맞춘다.
const BOOK_STEPS_UI = [
  { code: 'STORY_GENERATION', label: '이야기 짓기' },
  { code: 'PAGE_PLANNING', label: '페이지 나누기' },
  { code: 'CHARACTER_ANALYSIS', label: '우리 아이 분석' },
  { code: 'PAGE_ILLUSTRATION', label: '삽화 그리기' },
  { code: 'PDF_GENERATION', label: '책 완성' },
]
const VIDEO_STEPS_UI = [
  { code: 'STORY_GENERATION', label: '이야기 짓기' },
  { code: 'SCENE_SPLIT', label: '장면 나누기' },
  { code: 'CHARACTER_ANALYSIS', label: '우리 아이 분석' },
  { code: 'IMAGE_GENERATION', label: '그림 그리기' },
  { code: 'VIDEO_GENERATION', label: '영상 만들기' },
  { code: 'VOICE_GENERATION', label: '목소리 입히기' },
  { code: 'SUBTITLE_GENERATION', label: '자막 넣기' },
  { code: 'VIDEO_COMPOSITION', label: '영상 완성' },
]

interface CharacterDraft {
  name: string
  role: string
  /** role === 'CUSTOM' 일 때 직접 입력한 관계 */
  customRole?: string
  photos: File[]
  previews: string[]
}

function App() {
  const [options, setOptions] = useState<Options | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [me, setMe] = useState<Me | null>(null)
  const [view, setView] = useState<'home' | 'create' | 'admin' | 'mypage'>('home')

  const [outputType, setOutputType] = useState('BOOK')
  const [theme, setTheme] = useState('')
  const [customTheme, setCustomTheme] = useState('')
  const [ageGroup, setAgeGroup] = useState('')
  const [dedication, setDedication] = useState('')
  const [dedicationPhoto, setDedicationPhoto] = useState<File | null>(null)
  const [dedicationPhotoPreview, setDedicationPhotoPreview] = useState<string | null>(null)
  const [storyDirection, setStoryDirection] = useState('')
  const [bookStyle, setBookStyle] = useState('')
  const [bookPages, setBookPages] = useState<number | null>(null)
  const [physical, setPhysical] = useState(false)
  const [videoStyle, setVideoStyle] = useState('')
  const [videoDuration, setVideoDuration] = useState<number | null>(null)
  const [characters, setCharacters] = useState<CharacterDraft[]>([
    { name: '', role: 'MAIN', photos: [], previews: [] },
  ])
  const [cropQueue, setCropQueue] = useState<CropQueue | null>(null)

  const [job, setJob] = useState<JobResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const pollRef = useRef<number | null>(null)
  // 낭독 영상 자동 트리거 중복 방지(같은 job에 한 번만).
  const narrationTriggeredRef = useRef<number | null>(null)

  // 미리보기 확정(구매) 관련
  const [purchaseType, setPurchaseType] = useState<'PDF' | 'PDF_VIDEO' | 'PDF_VIDEO_BOOK'>('PDF')
  const [email, setEmail] = useState('')
  // 실물(책자) 배송 정보
  const [recipientName, setRecipientName] = useState('')
  const [recipientPhone, setRecipientPhone] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [shippingAddress, setShippingAddress] = useState('')
  const [shippingAddressDetail, setShippingAddressDetail] = useState('')
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    getMe()
      .then(setMe)
      .catch(() => setMe({ authenticated: false }))
  }, [])

  async function onLogout() {
    await logout().catch(() => {})
    setJob(null)
    setView('home')
    // 로그아웃 후 로그인 상태를 다시 받아온다(loginEnabled 유지).
    try {
      setMe(await getMe())
    } catch {
      setMe({ authenticated: false, loginEnabled: true })
    }
  }

  function startCreate() {
    setJob(null)
    setView('create')
  }
  function goHome() {
    setJob(null)
    setView('home')
  }
  /** 마이페이지에서 고른 동화책을 열어 결과 화면으로 이동한다. */
  async function openBook(id: number) {
    try {
      setJob(await getProject(id))
      setView('create')
    } catch (e) {
      alert('동화책을 불러오지 못했어요: ' + String((e as Error).message ?? e))
    }
  }
  // 발송 설정 확인용: 본인 이메일로 테스트 메일 발송.
  function handleTestEmail() {
    testEmail()
      .then((r) => {
        if (r.sent) {
          alert(`테스트 메일을 ${r.to} 로 보냈어요! 📬\n받은편지함(안 보이면 스팸함)을 확인하세요.`)
        } else {
          alert(
            `발송 실패 ❌\n원인: ${r.error ?? '알 수 없음'}\n(SMTP 설정: ${r.mailConfigured ? '됨' : '안 됨 — Railway 환경변수 확인'})`,
          )
        }
      })
      .catch((e) => alert(`오류: ${e.message}`))
  }

  useEffect(() => {
    getOptions()
      .then((o) => {
        setOptions(o)
        setTheme(o.themes[0]?.code ?? '')
        setAgeGroup(o.ageGroups[0]?.code ?? '')
        setBookStyle(o.bookStyles[0]?.code ?? '')
        setBookPages(o.bookPageOptions[0] ?? null)
        setVideoStyle(o.videoStyles[0]?.code ?? '')
        setVideoDuration(o.videoDurationOptions[0] ?? null)
      })
      .catch((e) => setLoadError(String(e.message ?? e)))
  }, [])

  // 생성 후 완료까지 폴링
  useEffect(() => {
    if (!job || job.status === 'COMPLETED' || job.status === 'FAILED') return
    pollRef.current = window.setInterval(async () => {
      try {
        setJob(await getProject(job.id))
      } catch {
        // 일시 오류는 무시하고 다음 폴링에서 재시도
      }
    }, 700)
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [job])

  // 낭독 영상 생성 중이면 준비될 때까지 폴링(완료 화면에서만 트리거됨).
  useEffect(() => {
    if (!job || job.narrationVideoStatus !== 'generating') return
    const t = window.setInterval(async () => {
      try {
        setJob(await getProject(job.id))
      } catch {
        // 일시 오류 무시
      }
    }, 2500)
    return () => window.clearInterval(t)
  }, [job])

  // 영상 포함 주문이 완성되면 낭독 영상 생성을 자동 시작한다(수동 버튼도 유지).
  useEffect(() => {
    if (
      job &&
      job.status === 'COMPLETED' &&
      job.outputType === 'BOOK' &&
      job.videoIncluded &&
      (!job.narrationVideoStatus || job.narrationVideoStatus === 'none') &&
      narrationTriggeredRef.current !== job.id
    ) {
      narrationTriggeredRef.current = job.id
      setJob({ ...job, narrationVideoStatus: 'generating' })
      startNarrationVideo(job.id).catch(() => {})
    }
  }, [job])

  /** 부모 목소리가 등록되면 그 목소리로 낭독 영상을 다시 만든다. */
  async function handleParentVoiceRegistered(updated: JobResponse) {
    narrationTriggeredRef.current = updated.id
    setJob({ ...updated, narrationVideoStatus: 'generating' })
    try {
      await startNarrationVideo(updated.id)
    } catch (e) {
      alert('영상 재생성을 시작하지 못했어요: ' + String((e as Error).message ?? e))
    }
  }

  async function handleMakeNarration() {
    if (!job) return
    try {
      narrationTriggeredRef.current = job.id
      await startNarrationVideo(job.id)
      setJob({ ...job, narrationVideoStatus: 'generating' })
    } catch (e) {
      alert('영상 만들기를 시작하지 못했어요: ' + String((e as Error).message ?? e))
    }
  }

  const isBook = outputType === 'BOOK'

  // 주제: 목록 선택 또는 직접입력(문구 필수). 인물: 이름·사진 필수, 직접입력 관계면 문구도 필수.
  const themeReady = theme === CUSTOM_THEME ? !!customTheme.trim() : !!theme
  const canSubmit =
    themeReady &&
    !!ageGroup &&
    (isBook ? !!bookStyle && !!bookPages : !!videoStyle && !!videoDuration) &&
    characters.every(
      (c) =>
        c.name.trim() &&
        c.photos.length > 0 &&
        (c.role !== 'CUSTOM' || !!c.customRole?.trim()),
    ) &&
    !submitting

  function updateCharacter(idx: number, patch: Partial<CharacterDraft>) {
    setCharacters((prev) => prev.map((c, i) => (i === idx ? { ...c, ...patch } : c)))
  }

  function addCharacter() {
    setCharacters((prev) => [
      ...prev,
      { name: '', role: 'YOUNGER_SIBLING', photos: [], previews: [] },
    ])
  }

  function removeCharacter(idx: number) {
    setCharacters((prev) => prev.filter((_, i) => i !== idx))
  }

  function onPhotosPick(idx: number, files: FileList | null) {
    if (!files || files.length === 0) return
    // 바로 담지 않고 크롭 모달로 보냄(부모+자녀 사진에서 자녀만 자르도록)
    setCropQueue({ charIdx: idx, files: Array.from(files), pos: 0 })
  }

  function addPhotoToChar(idx: number, file: File) {
    setCharacters((prev) =>
      prev.map((c, i) =>
        i === idx
          ? { ...c, photos: [...c.photos, file], previews: [...c.previews, URL.createObjectURL(file)] }
          : c,
      ),
    )
  }

  function advanceCrop() {
    setCropQueue((q) => {
      if (!q) return null
      const next = q.pos + 1
      return next < q.files.length ? { ...q, pos: next } : null
    })
  }

  function onCropConfirm(cropped: File) {
    if (cropQueue) addPhotoToChar(cropQueue.charIdx, cropped)
    advanceCrop()
  }

  function onCropSkip() {
    if (cropQueue) addPhotoToChar(cropQueue.charIdx, cropQueue.files[cropQueue.pos])
    advanceCrop()
  }

  function removePhoto(idx: number, photoIdx: number) {
    setCharacters((prev) =>
      prev.map((c, i) =>
        i === idx
          ? {
              ...c,
              photos: c.photos.filter((_, p) => p !== photoIdx),
              previews: c.previews.filter((_, p) => p !== photoIdx),
            }
          : c,
      ),
    )
  }

  async function onSubmit() {
    if (!options) return
    setSubmitError(null)
    setSubmitting(true)
    try {
      // 각 인물의 사진을 서버에 업로드해 URL을 받는다.
      const chars: CharacterInput[] = []
      for (const c of characters) {
        const urls = await uploadPhotos(c.photos)
        chars.push({
          name: c.name.trim(),
          role: c.role,
          customRole: c.role === 'CUSTOM' ? c.customRole?.trim() || undefined : undefined,
          photoUrls: urls,
        })
      }
      // 헌정 페이지용 가족 사진(선택) — 변환 없이 원본 그대로 삽입.
      let dedicationPhotoUrl: string | undefined
      if (dedicationPhoto) {
        const [url] = await uploadPhotos([dedicationPhoto])
        dedicationPhotoUrl = url
      }
      const isCustomTheme = theme === CUSTOM_THEME
      const created = await createProject({
        outputType,
        // 직접입력이면 enum 대신 customTheme으로 보낸다(백엔드가 기본 분류를 채운다).
        theme: isCustomTheme ? null : theme,
        customTheme: isCustomTheme ? customTheme.trim() : undefined,
        ageGroup,
        dedication: dedication.trim() || undefined,
        dedicationPhotoUrl,
        storyDirection: storyDirection.trim() || undefined,
        physicalBookRequested: isBook ? physical : false,
        bookStyle: isBook ? bookStyle : null,
        bookPages: isBook ? bookPages : null,
        videoStyle: isBook ? null : videoStyle,
        videoDurationSec: isBook ? null : videoDuration,
        characters: chars,
      })
      setJob(created)
    } catch (e) {
      setSubmitError(String((e as Error).message ?? e))
    } finally {
      setSubmitting(false)
    }
  }

  function reset() {
    setJob(null)
    setSubmitError(null)
  }

  async function onConfirm() {
    if (!job) return
    setSubmitError(null)
    // 이메일은 기본, 책자는 배송 정보 필수.
    if (!email.trim()) {
      setSubmitError('완성본을 받을 이메일을 입력해 주세요.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setSubmitError('이메일 주소 형식을 확인해 주세요. (예: parent@example.com)')
      return
    }
    if (
      purchaseType === 'PDF_VIDEO_BOOK' &&
      (!recipientName.trim() || !recipientPhone.trim() || !shippingAddress.trim())
    ) {
      setSubmitError('책자 배송을 위해 받는 사람 이름·연락처·주소를 입력해 주세요.')
      return
    }
    setConfirming(true)
    try {
      const updated = await confirmProject(job.id, {
        purchaseType,
        deliveryEmail: email.trim() || undefined,
        ...(purchaseType === 'PDF_VIDEO_BOOK'
          ? {
              recipientName: recipientName.trim(),
              recipientPhone: recipientPhone.trim(),
              postalCode: postalCode.trim() || undefined,
              shippingAddress: shippingAddress.trim(),
              shippingAddressDetail: shippingAddressDetail.trim() || undefined,
            }
          : {}),
      })
      setJob(updated) // status RUNNING → 폴링이 다시 시작되어 전체 생성 진행
      // 확정되면 진행 화면으로 바뀌므로 맨 위로 올려 전환이 분명히 보이게 한다.
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setSubmitError(String((e as Error).message ?? e))
    } finally {
      setConfirming(false)
    }
  }

  if (loadError) {
    return (
      <main className="app">
        <div className="card error">
          <h2>백엔드에 연결할 수 없습니다</h2>
          <p>{loadError}</p>
          <p className="muted">백엔드(:8080)가 실행 중인지 확인해 주세요.</p>
        </div>
      </main>
    )
  }

  if (!options || me === null) {
    return (
      <main className="app">
        <div className="card">불러오는 중…</div>
      </main>
    )
  }

  // 주제를 갈래별로 묶는다(배경·모험 먼저, 생활 습관 다음). options가 확정된 이후라 안전.
  const themeGroups: [string, ThemeOption[]][] = (['BACKGROUND', 'HABIT'] as const)
    .map((cat) => [cat, options.themes.filter((t) => t.category === cat)] as [string, ThemeOption[]])
    .filter(([, list]) => list.length > 0)

  // 홈(랜딩) 화면.
  if (view === 'home') {
    return (
      <Landing
        me={me}
        onStart={startCreate}
        onLogout={onLogout}
        onAdmin={me?.isAdmin ? () => setView('admin') : undefined}
        onMyPage={me?.authenticated ? () => setView('mypage') : undefined}
      />
    )
  }

  if (view === 'admin') {
    return <AdminDashboard onHome={goHome} />
  }

  if (view === 'mypage') {
    return (
      <MyPage
        me={me}
        onHome={goHome}
        onStart={startCreate}
        onLogout={onLogout}
        onOpenBook={openBook}
      />
    )
  }

  // (여기부터는 view === 'create') 로그인 안 했으면 → 로그인 화면.
  if (!me.authenticated && me.loginEnabled) {
    return (
      <main className="app">
        <div className="login-bar">
          <button className="btn ghost small" onClick={goHome}>← 홈</button>
        </div>
        <div className="login-screen">
          <header className="hero">
            <h1>TodayHero</h1>
            <p>아이 사진으로 만드는 우리 아이 주인공 동화책</p>
          </header>
          <section className="card login-card">
            <p className="login-lead">로그인하고 우리 아이 동화를 만들어보세요</p>
            <a className="btn login-kakao" href={loginUrl('kakao')}>카카오로 시작하기</a>
            <a className="btn login-google" href={loginUrl('google')}>구글로 시작하기</a>
            <p className="muted small center">로그인하면 만든 동화를 내 계정에 안전하게 보관해요</p>
          </section>
        </div>
      </main>
    )
  }

  // 생성 진행/완료 화면
  if (job) {
    const done = job.status === 'COMPLETED'
    const failed = job.status === 'FAILED'
    // 책 미리보기 완료 = 확정(구매) 단계
    const isPreview = done && job.outputType === 'BOOK' && job.bookPhase === 'PREVIEW'
    const isFinal = done && !isPreview
    return (
      <main className="app">
        <header className="hero">
          <h1>TodayHero</h1>
          <p>우리 아이가 주인공인 동화</p>
        </header>
        <div className="card result">
          {!done && !failed && (() => {
            const order = job.outputType === 'BOOK' ? BOOK_STEPS_UI : VIDEO_STEPS_UI
            const curIdx = Math.max(0, order.findIndex((s) => s.code === job.currentStep))
            const pct = Math.round(((curIdx + 0.5) / order.length) * 100)
            const hint =
              job.currentStep === 'PAGE_ILLUSTRATION'
                ? '삽화는 페이지마다 한 장씩 정성껏 그려서 가장 오래 걸려요. 조금만 기다려 주세요 🎨'
                : job.bookPhase === 'PREVIEW'
                  ? '표지와 앞부분을 먼저 보여드릴게요. 보통 1~3분 걸려요.'
                  : '완성본을 만드는 중이에요. 페이지가 많아 몇 분 걸릴 수 있어요.'
            return (
              <>
                <div className="spinner" />
                <h2>{STEP_LABELS[job.currentStep] ?? '만드는 중'}</h2>
                <div className="gen-bar">
                  <div className="gen-bar-fill" style={{ width: `${pct}%` }} />
                </div>
                <ol className="gen-steps">
                  {order.map((s, i) => (
                    <li
                      key={s.code}
                      className={i < curIdx ? 'done' : i === curIdx ? 'active' : ''}
                    >
                      <span className="gen-dot">{i < curIdx ? '✓' : i + 1}</span>
                      {s.label}
                    </li>
                  ))}
                </ol>
                <p className="muted small">{hint}</p>
              </>
            )
          })()}
          {failed && (
            <>
              <h2>생성에 실패했어요</h2>
              <p className="error-text">{job.errorMessage}</p>
            </>
          )}
          {isPreview && (
            <>
              <div className="check">👀</div>
              <h2>{job.generatedTitle ?? '미리보기가 나왔어요!'}</h2>
              <p className="muted">표지와 앞부분을 먼저 보여드려요. 마음에 들면 전체 책을 만들어 드릴게요.</p>
              {job.resultUrl && (
                <a className="btn ghost" href={apiUrl(job.resultUrl)} target="_blank" rel="noreferrer">
                  미리보기 열어보기
                </a>
              )}
              <label className="field-label">받는 방법</label>
              <div className="tier-list">
                {(options?.pricing.bundles ?? []).map((b) => {
                  const price = b.prices.find((p) => p.pages === job.bookPages)?.priceKrw
                  return (
                    <button
                      key={b.code}
                      className={`tier ${purchaseType === b.code ? 'on' : ''}`}
                      onClick={() => {
                        setPurchaseType(b.code as typeof purchaseType)
                        setSubmitError(null)
                      }}
                    >
                      <span className="tier-label">{b.label}</span>
                      <span className="tier-price">
                        {price != null ? `${price.toLocaleString()}원` : ''}
                      </span>
                    </button>
                  )
                })}
              </div>
              <p className="muted small center">모든 금액 VAT 포함 · 영상은 "읽어주는 동화 영상"으로 함께 제공</p>
              <label className="field-label">완성본 받을 이메일 (필수)</label>
              <input
                className="text"
                type="email"
                placeholder="이메일 주소"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value)
                  setSubmitError(null)
                }}
              />
              {purchaseType === 'PDF_VIDEO_BOOK' && (
                <div className="ship-box">
                  <p className="muted small ship-note">
                    📦 실물 하드커버로 배송해 드려요. <b>PDF·영상도 이메일로 함께 보내드립니다.</b>
                  </p>
                  <label className="field-label">받는 사람 이름</label>
                  <input
                    className="text"
                    placeholder="이름"
                    value={recipientName}
                    onChange={(e) => setRecipientName(e.target.value)}
                  />
                  <label className="field-label">연락처</label>
                  <input
                    className="text"
                    type="tel"
                    placeholder="010-0000-0000"
                    value={recipientPhone}
                    onChange={(e) => setRecipientPhone(e.target.value)}
                  />
                  <label className="field-label">배송 주소</label>
                  <input
                    className="text"
                    placeholder="우편번호"
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                  />
                  <input
                    className="text ship-addr"
                    placeholder="주소 (도로명/지번)"
                    value={shippingAddress}
                    onChange={(e) => setShippingAddress(e.target.value)}
                  />
                  <input
                    className="text ship-addr"
                    placeholder="상세주소 (동/호수 등)"
                    value={shippingAddressDetail}
                    onChange={(e) => setShippingAddressDetail(e.target.value)}
                  />
                </div>
              )}
              {submitError && <p className="error-text center">{submitError}</p>}
              <button className="btn primary" disabled={confirming} onClick={onConfirm}>
                {confirming ? '주문 확정 중…' : '이걸로 전체 만들기'}
              </button>
              <p className="muted small">전체 생성은 몇 분 걸려요. 결제는 이후 단계에서 붙습니다.</p>
            </>
          )}
          {isFinal && (
            <>
              <div className="check">✓</div>
              <h2>{job.generatedTitle ?? '완성되었어요!'}</h2>
              <p className="muted">
                {job.outputType === 'BOOK' ? '동화책' : '영상'}
                {job.physicalBookRequested && ' · 실물(인쇄본) 요청됨'}
              </p>
              {job.outputType === 'BOOK' && job.resultUrl ? (
                <a className="btn primary" href={apiUrl(job.resultUrl)} target="_blank" rel="noreferrer">
                  전체 책 다운로드
                </a>
              ) : (
                <p className="muted">영상 다운로드는 준비 중입니다.</p>
              )}
              {job.deliveryEmail && (
                <p className={`email-status ${job.emailSent ? 'ok' : 'fail'}`}>
                  {job.emailSent
                    ? `✅ 완성본을 ${job.deliveryEmail} 로 이메일로 보냈어요. (스팸함도 확인해 주세요)`
                    : `⚠️ 이메일(${job.deliveryEmail}) 발송이 아직 안 됐어요. 위 다운로드 버튼으로 받아주세요.`}
                </p>
              )}
              {job.physicalBookRequested && (
                <p className="notice">
                  실물 인쇄본으로 요청하셨습니다. 결제 후 인쇄·배송됩니다. (결제 연동 준비 중)
                </p>
              )}
              {job.outputType === 'BOOK' && job.videoIncluded && (
                <div className="narration-box">
                  <h3>🎬 읽어주는 동화 영상</h3>
                  {job.narrationVideoStatus === 'ready' && job.narrationVideoUrl ? (
                    <>
                      <video
                        className="narration-video"
                        src={apiUrl(job.narrationVideoUrl)}
                        controls
                        playsInline
                      />
                      <a
                        className="btn ghost"
                        href={apiUrl(job.narrationVideoUrl)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        영상 다운로드
                      </a>
                    </>
                  ) : job.narrationVideoStatus === 'generating' ? (
                    <p className="muted">영상을 만들고 있어요… 목소리를 나눠 녹음하는 중이라 몇 분 걸려요. ⏳</p>
                  ) : (
                    <>
                      <p className="muted">
                        페이지 그림에 목소리를 입혀 동화를 읽어주는 영상으로 만들어 드려요.
                        (인물마다 목소리가 달라요)
                      </p>
                      {job.narrationVideoStatus === 'failed' && (
                        <p className="email-status fail">
                          ⚠️ 지난번 생성이 실패했어요. 다시 시도해 주세요.
                        </p>
                      )}
                      <button className="btn primary" onClick={handleMakeNarration}>
                        읽어주는 영상 만들기
                      </button>
                    </>
                  )}
                  <ParentVoiceRecorder
                    jobId={job.id}
                    hasParentVoice={job.hasParentVoice}
                    onRegistered={handleParentVoiceRegistered}
                  />
                </div>
              )}
            </>
          )}
          <button className="btn ghost" onClick={reset}>
            새로 만들기
          </button>
        </div>
      </main>
    )
  }

  // 생성 폼
  return (
    <main className="app">
      <div className="login-bar">
        <button className="btn ghost small" onClick={goHome}>← 홈</button>
        <span className="login-bar-spacer" />
        {me?.authenticated ? (
          <>
            {me.isAdmin && (
              <button className="btn ghost small" onClick={() => setView('admin')}>📊 관리자</button>
            )}
            <button className="btn ghost small" onClick={handleTestEmail}>✉️ 메일테스트</button>
            <span className="muted small">{me.name ?? '회원'}님</span>
            <button className="btn ghost small" onClick={onLogout}>로그아웃</button>
          </>
        ) : null}
      </div>
      <header className="hero">
        <h1>TodayHero</h1>
        <p>아이 사진으로 만드는 우리 아이 주인공 동화책</p>
      </header>

      <section className="card">
        <h3 className="step">1. 무엇을 만들까요?</h3>
        <div className="chips">
          {options.outputTypes
            .filter((o) => o.code === 'BOOK')
            .map((o) => (
              <button
                key={o.code}
                className={`chip ${outputType === o.code ? 'on' : ''}`}
                onClick={() => setOutputType(o.code)}
              >
                📖 {o.label}
              </button>
            ))}
        </div>
      </section>

      <section className="card">
        <h3 className="step">2. 주제</h3>
        {themeGroups.map(([cat, list]) => (
          <div key={cat} className="theme-group">
            <p className="theme-group-label">
              {cat === 'HABIT' ? '🪥 생활 습관 (아이 습관 들이기)' : '🏰 배경 · 모험'}
            </p>
            <div className="chips">
              {list.map((o) => (
                <button
                  key={o.code}
                  className={`chip ${theme === o.code ? 'on' : ''}`}
                  onClick={() => setTheme(o.code)}
                >
                  {o.label}
                </button>
              ))}
              {cat === 'HABIT' && (
                <button
                  className={`chip ${theme === CUSTOM_THEME ? 'on' : ''}`}
                  onClick={() => setTheme(CUSTOM_THEME)}
                >
                  ✏️ 직접입력
                </button>
              )}
            </div>
          </div>
        ))}
        {theme === CUSTOM_THEME && (
          <input
            className="text"
            placeholder="원하는 주제를 적어주세요 (예: 발레리나가 되는 날, 첫 등원)"
            value={customTheme}
            maxLength={60}
            onChange={(e) => setCustomTheme(e.target.value)}
          />
        )}
        <label className="field-label">대상 연령 (글의 분량·표현이 맞춰집니다)</label>
        <div className="chips">
          {options.ageGroups.map((o) => (
            <button
              key={o.code}
              className={`chip ${ageGroup === o.code ? 'on' : ''}`}
              onClick={() => setAgeGroup(o.code)}
            >
              {o.label}
            </button>
          ))}
        </div>
      </section>

      <section className="card">
        <h3 className="step">3. 스타일 &amp; 분량</h3>
        {isBook ? (
          <>
            <label className="field-label">그림 스타일</label>
            <div className="chips">
              {options.bookStyles.map((o) => (
                <button
                  key={o.code}
                  className={`chip ${bookStyle === o.code ? 'on' : ''}`}
                  onClick={() => setBookStyle(o.code)}
                >
                  {o.label}
                </button>
              ))}
            </div>
            <label className="field-label">페이지 수</label>
            <div className="chips">
              {options.bookPageOptions.map((p) => (
                <button
                  key={p}
                  className={`chip ${bookPages === p ? 'on' : ''}`}
                  onClick={() => setBookPages(p)}
                >
                  {p}페이지
                </button>
              ))}
            </div>
            <label className="check-row">
              <input
                type="checkbox"
                checked={physical}
                onChange={(e) => setPhysical(e.target.checked)}
              />
              실물 인쇄본으로 받기 (배송)
            </label>
          </>
        ) : (
          <>
            <label className="field-label">애니메이션 스타일</label>
            <div className="chips">
              {options.videoStyles.map((o) => (
                <button
                  key={o.code}
                  className={`chip ${videoStyle === o.code ? 'on' : ''}`}
                  onClick={() => setVideoStyle(o.code)}
                >
                  {o.label}
                </button>
              ))}
            </div>
            <label className="field-label">길이</label>
            <div className="chips">
              {options.videoDurationOptions.map((d) => (
                <button
                  key={d}
                  className={`chip ${videoDuration === d ? 'on' : ''}`}
                  onClick={() => setVideoDuration(d)}
                >
                  약 {Math.round(d / 60)}분
                </button>
              ))}
            </div>
          </>
        )}
      </section>

      <section className="card">
        <h3 className="step">4. 등장인물</h3>
        <p className="muted small">
          첫 번째 인물이 주인공이에요. 둘째나 친구를 함께 추가할 수 있어요.
        </p>
        <div className="photo-guide">
          <b>📸 사진 잘 올리는 법</b>
          <ul>
            <li><b>정면 1장 + 다른 각도(살짝 옆) 1~2장</b>을 함께 올리면 더 닮게 나와요.</li>
            <li>밝고 선명한 <b>최근 사진</b>, 얼굴이 크게 나온 걸 추천해요.</li>
            <li>여러 장이면 좋지만 <b>2~4장이면 충분</b>해요. (많아도 비용은 거의 안 늘어요)</li>
            <li>선글라스·마스크·심한 필터, 아기 때 사진 섞기는 피해주세요.</li>
          </ul>
        </div>
        {characters.map((c, idx) => (
          <div className="character-card" key={idx}>
            <div className="character-top">
              <input
                className="text"
                placeholder={idx === 0 ? '주인공 이름 (예: 소영)' : '이름 (예: 상우)'}
                value={c.name}
                onChange={(e) => updateCharacter(idx, { name: e.target.value })}
              />
              <select
                className="text role"
                value={idx === 0 ? 'MAIN' : c.role}
                disabled={idx === 0}
                onChange={(e) => updateCharacter(idx, { role: e.target.value })}
              >
                {options.characterRoles.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.label}
                  </option>
                ))}
              </select>
              {idx > 0 && (
                <button className="remove" onClick={() => removeCharacter(idx)} aria-label="인물 삭제">
                  ×
                </button>
              )}
            </div>
            {idx > 0 && c.role === 'CUSTOM' && (
              <input
                className="text"
                placeholder="관계를 적어주세요 (예: 할머니, 이모, 사촌)"
                value={c.customRole ?? ''}
                maxLength={20}
                onChange={(e) => updateCharacter(idx, { customRole: e.target.value })}
              />
            )}
            <div className="photos">
              {c.previews.map((src, p) => (
                <div className="thumb" key={p}>
                  <img src={src} alt="" />
                  <button className="thumb-x" onClick={() => removePhoto(idx, p)} aria-label="사진 삭제">
                    ×
                  </button>
                </div>
              ))}
              <label className="thumb add">
                <span>+ 사진</span>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  onChange={(e) => {
                    onPhotosPick(idx, e.target.files)
                    e.currentTarget.value = ''
                  }}
                />
              </label>
            </div>
          </div>
        ))}
        <button className="btn ghost small" onClick={addCharacter}>
          + 인물 추가
        </button>
        <p className="muted small">
          ※ 얼굴이 또렷한 사진 3~5장을 올리면 더 닮게 나와요.
        </p>
      </section>

      <section className="card">
        <h3 className="step">5. 특별한 요청 <span className="muted small">(선택)</span></h3>
        <label className="field-label">헌정 메세지 <span className="muted small">— 책 첫 장에 들어가요</span></label>
        <textarea
          className="text area"
          placeholder="예: 사랑하는 소영이에게, 언제나 반짝반짝 빛나렴. — 엄마아빠가"
          value={dedication}
          maxLength={200}
          rows={2}
          onChange={(e) => setDedication(e.target.value)}
        />
        <label className="field-label">가족 사진 <span className="muted small">— 헌정 페이지에 실제 사진 그대로 들어가요 (선택)</span></label>
        <div className="ded-photo">
          <label className="ded-photo-pick">
            {dedicationPhotoPreview ? (
              <img src={dedicationPhotoPreview} alt="가족 사진" />
            ) : (
              <span>사진 추가</span>
            )}
            <input
              type="file"
              accept="image/*"
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (!f) return
                setDedicationPhoto(f)
                setDedicationPhotoPreview((prev) => {
                  if (prev) URL.revokeObjectURL(prev)
                  return URL.createObjectURL(f)
                })
                e.target.value = ''
              }}
            />
          </label>
          {dedicationPhotoPreview && (
            <button
              type="button"
              className="btn ghost small"
              onClick={() => {
                if (dedicationPhotoPreview) URL.revokeObjectURL(dedicationPhotoPreview)
                setDedicationPhoto(null)
                setDedicationPhotoPreview(null)
              }}
            >
              사진 빼기
            </button>
          )}
        </div>
        <label className="field-label">스토리 방향 <span className="muted small">— 원하는 이야기 틀 (없으면 비워두세요)</span></label>
        <textarea
          className="text area"
          placeholder="예: 동생과 함께 용기를 내어 잃어버린 강아지를 찾아주는 이야기"
          value={storyDirection}
          maxLength={300}
          rows={3}
          onChange={(e) => setStoryDirection(e.target.value)}
        />
      </section>

      <section className="card submit-bar">
        <button className="btn primary" disabled={!canSubmit} onClick={onSubmit}>
          {submitting ? '만드는 중…' : '만들기 시작'}
        </button>
      </section>
      {submitError && <p className="error-text center">{submitError}</p>}
      {cropQueue && (
        <ImageCropper
          file={cropQueue.files[cropQueue.pos]}
          index={cropQueue.pos}
          total={cropQueue.files.length}
          onCancel={onCropSkip}
          onConfirm={onCropConfirm}
        />
      )}
    </main>
  )
}

export default App
