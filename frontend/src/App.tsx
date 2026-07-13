import { useEffect, useMemo, useRef, useState } from 'react'
import './App.css'
import ImageCropper from './ImageCropper'
import {
  createProject,
  getOptions,
  getProject,
  uploadPhotos,
  type CharacterInput,
  type JobResponse,
  type Options,
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

interface CharacterDraft {
  name: string
  role: string
  photos: File[]
  previews: string[]
}

const won = (n: number | null | undefined) =>
  n == null ? '-' : n.toLocaleString('ko-KR') + '원'

function App() {
  const [options, setOptions] = useState<Options | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [outputType, setOutputType] = useState('BOOK')
  const [theme, setTheme] = useState('')
  const [ageGroup, setAgeGroup] = useState('')
  const [dedication, setDedication] = useState('')
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

  const isBook = outputType === 'BOOK'

  const price = useMemo(() => {
    if (!options || !isBook) return null
    if (physical) return options.pricing.bookHardcopyKrw
    return options.pricing.bookPdf.find((p) => p.pages === bookPages)?.priceKrw ?? null
  }, [options, isBook, physical, bookPages])

  const canSubmit =
    !!theme &&
    !!ageGroup &&
    (isBook ? !!bookStyle && !!bookPages : !!videoStyle && !!videoDuration) &&
    characters.every((c) => c.name.trim() && c.photos.length > 0) &&
    !submitting

  function updateCharacter(idx: number, patch: Partial<CharacterDraft>) {
    setCharacters((prev) => prev.map((c, i) => (i === idx ? { ...c, ...patch } : c)))
  }

  function addCharacter() {
    setCharacters((prev) => [
      ...prev,
      { name: '', role: 'SIBLING', photos: [], previews: [] },
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
        chars.push({ name: c.name.trim(), role: c.role, photoUrls: urls })
      }
      const created = await createProject({
        outputType,
        theme,
        ageGroup,
        dedication: dedication.trim() || undefined,
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

  if (!options) {
    return (
      <main className="app">
        <div className="card">불러오는 중…</div>
      </main>
    )
  }

  // 생성 진행/완료 화면
  if (job) {
    const done = job.status === 'COMPLETED'
    const failed = job.status === 'FAILED'
    return (
      <main className="app">
        <header className="hero">
          <h1>StoryAI</h1>
          <p>우리 아이가 주인공인 동화</p>
        </header>
        <div className="card result">
          {!done && !failed && (
            <>
              <div className="spinner" />
              <h2>{STEP_LABELS[job.currentStep] ?? job.currentStep}</h2>
              <p className="muted">잠시만 기다려 주세요…</p>
            </>
          )}
          {failed && (
            <>
              <h2>생성에 실패했어요</h2>
              <p className="error-text">{job.errorMessage}</p>
            </>
          )}
          {done && (
            <>
              <div className="check">✓</div>
              <h2>{job.generatedTitle ?? '완성되었어요!'}</h2>
              <p className="muted">
                {job.outputType === 'BOOK' ? '동화책' : '영상'} · {won(job.priceKrw)}
                {job.physicalBookRequested && ' · 실물(인쇄본) 요청됨'}
              </p>
              {job.outputType === 'BOOK' && job.resultUrl ? (
                <a className="btn primary" href={job.resultUrl} target="_blank" rel="noreferrer">
                  PDF 다운로드
                </a>
              ) : (
                <p className="muted">영상 다운로드는 준비 중입니다.</p>
              )}
              {job.physicalBookRequested && (
                <p className="notice">
                  실물 인쇄본으로 요청하셨습니다. 결제 후 인쇄·배송됩니다. (결제 연동 준비 중)
                </p>
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
      <header className="hero">
        <h1>StoryAI</h1>
        <p>아이 사진으로 만드는 우리 아이 주인공 동화책 · 영상</p>
      </header>

      <section className="card">
        <h3 className="step">1. 무엇을 만들까요?</h3>
        <div className="chips">
          {options.outputTypes.map((o) => (
            <button
              key={o.code}
              className={`chip ${outputType === o.code ? 'on' : ''}`}
              onClick={() => setOutputType(o.code)}
            >
              {o.code === 'BOOK' ? '📖 ' : '🎬 '}
              {o.label}
            </button>
          ))}
        </div>
      </section>

      <section className="card">
        <h3 className="step">2. 주제</h3>
        <div className="chips">
          {options.themes.map((o) => (
            <button
              key={o.code}
              className={`chip ${theme === o.code ? 'on' : ''}`}
              onClick={() => setTheme(o.code)}
            >
              {o.label}
            </button>
          ))}
        </div>
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
              {options.bookPageOptions.map((p) => {
                const pdf = options.pricing.bookPdf.find((x) => x.pages === p)
                return (
                  <button
                    key={p}
                    className={`chip ${bookPages === p ? 'on' : ''}`}
                    onClick={() => setBookPages(p)}
                  >
                    {p}페이지 · {won(pdf?.priceKrw)}
                  </button>
                )
              })}
            </div>
            <label className="check-row">
              <input
                type="checkbox"
                checked={physical}
                onChange={(e) => setPhysical(e.target.checked)}
              />
              실물 인쇄본으로 받기 (+ 배송, {won(options.pricing.bookHardcopyKrw)})
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

      <section className="card summary">
        <div>
          <span className="muted">예상 금액</span>
          <strong className="price">{isBook ? won(price) : '준비 중'}</strong>
        </div>
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
