import { useEffect, useRef, useState } from 'react'
import { uploadParentVoice } from './api'
import type { JobResponse } from './api'

/** 목소리 복제 품질을 위해 읽어주실 대본(30초~1분 분량, 다양한 발음이 섞이도록 구성). */
const SCRIPT = `안녕, 우리 아기. 오늘은 아주 특별한 이야기를 들려줄게.
깊고 깊은 숲속에는 반짝이는 호수가 하나 있었어요.
그 호수에는 별빛을 모으는 작은 친구가 살고 있었지요.
"같이 갈래?" 하고 물으면, 언제나 방긋 웃으며 고개를 끄덕였어요.
바람이 살랑살랑 불고, 나뭇잎이 사그락사그락 노래하는 밤.
우리는 손을 꼭 잡고 천천히 걸어갔답니다.
무섭지 않아. 엄마 아빠가 항상 곁에 있으니까.
자, 이제 눈을 감고 편안하게 쉬렴. 좋은 꿈 꾸자.`

const MIN_SECONDS = 30

/**
 * 부모 목소리 녹음 → 업로드(음성 복제).
 * 등록되면 낭독 영상의 "서술" 부분이 부모 목소리로 읽힌다(등장인물 대사는 캐릭터 목소리 유지).
 */
export default function ParentVoiceRecorder({
  jobId,
  hasParentVoice,
  onRegistered,
}: {
  jobId: number
  hasParentVoice: boolean
  onRegistered: (job: JobResponse) => void
}) {
  const [consent, setConsent] = useState(false)
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [blob, setBlob] = useState<Blob | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)

  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<BlobPart[]>([])
  const timerRef = useRef<number | null>(null)

  // 언마운트 시 타이머·미리듣기 URL·마이크 정리
  useEffect(() => {
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current)
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      recorderRef.current?.stream.getTracks().forEach((t) => t.stop())
    }
  }, [previewUrl])

  async function startRecording() {
    setError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const rec = new MediaRecorder(stream)
      chunksRef.current = []
      rec.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      rec.onstop = () => {
        const b = new Blob(chunksRef.current, { type: 'audio/webm' })
        setBlob(b)
        setPreviewUrl((prev) => {
          if (prev) URL.revokeObjectURL(prev)
          return URL.createObjectURL(b)
        })
        stream.getTracks().forEach((t) => t.stop())
      }
      recorderRef.current = rec
      rec.start()
      setRecording(true)
      setSeconds(0)
      timerRef.current = window.setInterval(() => setSeconds((s) => s + 1), 1000)
    } catch {
      setError('마이크를 사용할 수 없어요. 브라우저에서 마이크 권한을 허용해 주세요.')
    }
  }

  function stopRecording() {
    recorderRef.current?.stop()
    setRecording(false)
    if (timerRef.current) window.clearInterval(timerRef.current)
  }

  async function upload() {
    if (!blob) return
    setError(null)
    setUploading(true)
    try {
      const job = await uploadParentVoice(jobId, blob, consent)
      onRegistered(job)
    } catch (e) {
      setError(String((e as Error).message ?? e))
    } finally {
      setUploading(false)
    }
  }

  const mmss = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  const tooShort = seconds > 0 && seconds < MIN_SECONDS

  if (hasParentVoice) {
    return (
      <div className="pv-done">
        ✅ <b>엄마·아빠 목소리가 등록됐어요.</b> 이야기 서술을 이 목소리로 읽어드려요.
        <button className="btn ghost small" onClick={() => setOpen(true)}>
          다시 녹음하기
        </button>
      </div>
    )
  }

  if (!open) {
    return (
      <div className="pv-cta">
        <button className="btn ghost" onClick={() => setOpen(true)}>
          🎙️ 엄마·아빠 목소리로 읽어주기
        </button>
        <p className="muted small">30초만 녹음하면 이야기를 부모님 목소리로 들려드려요.</p>
      </div>
    )
  }

  return (
    <div className="pv-box">
      <h4>🎙️ 엄마·아빠 목소리 녹음</h4>
      <p className="muted small">
        아래 글을 <b>또렷하고 편안하게</b> 읽어 주세요. 조용한 곳에서 <b>30초 이상</b> 녹음하면 더 비슷해져요.
      </p>
      <pre className="pv-script">{SCRIPT}</pre>

      <label className="pv-consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          목소리를 <b>이 동화 영상 제작 목적</b>으로 복제·이용하는 데 동의합니다. 녹음본과 복제된 음성은
          제작 완료 후 삭제됩니다.
        </span>
      </label>

      <div className="pv-controls">
        {!recording ? (
          <button className="btn primary" onClick={startRecording} disabled={!consent || uploading}>
            {blob ? '다시 녹음' : '● 녹음 시작'}
          </button>
        ) : (
          <button className="btn primary" onClick={stopRecording}>
            ■ 녹음 종료 ({mmss})
          </button>
        )}
        {recording && <span className="pv-rec">● 녹음 중 {mmss}</span>}
      </div>

      {previewUrl && !recording && (
        <div className="pv-preview">
          <audio src={previewUrl} controls />
          <p className="muted small">녹음 길이: {mmss}</p>
          {tooShort && (
            <p className="email-status fail">
              ⚠️ 30초 이상 녹음하는 걸 권장해요. 짧으면 목소리가 덜 비슷할 수 있어요.
            </p>
          )}
          <button className="btn primary" onClick={upload} disabled={uploading || !consent}>
            {uploading ? '등록 중… (30초쯤 걸려요)' : '이 목소리로 등록하기'}
          </button>
        </div>
      )}

      {error && <p className="email-status fail">{error}</p>}
      <button className="btn ghost small" onClick={() => setOpen(false)} disabled={recording || uploading}>
        닫기
      </button>
    </div>
  )
}
