import { useEffect, useState } from 'react'
import { approveOutline, reviseOutline } from './api'
import type { JobResponse } from './api'

/**
 * 줄거리 확인 화면. 그림 생성 전에 이야기 줄거리를 먼저 보여주고,
 * 고객이 직접 고치거나 수정 요청을 남겨 다시 만든 뒤, 마음에 들면 미리보기 생성으로 넘어간다.
 * (그림 전 단계라 여기서 방향을 잡으면 비용 낭비가 없다)
 */
export default function OutlineReview({
  job,
  onUpdated,
}: {
  job: JobResponse
  onUpdated: (job: JobResponse) => void
}) {
  const [title, setTitle] = useState(job.generatedTitle ?? '')
  const [synopsis, setSynopsis] = useState(job.synopsis ?? '')
  const [feedback, setFeedback] = useState('')
  const [busy, setBusy] = useState<'revise' | 'approve' | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 줄거리가 다시 생성되면(재작성 후) 편집 칸을 새 내용으로 갱신한다.
  useEffect(() => {
    setTitle(job.generatedTitle ?? '')
    setSynopsis(job.synopsis ?? '')
    setFeedback('')
  }, [job.generatedTitle, job.synopsis])

  async function onRevise() {
    if (!feedback.trim()) {
      setError('어떤 점을 바꾸고 싶은지 적어주세요.')
      return
    }
    setError(null)
    setBusy('revise')
    try {
      onUpdated(await reviseOutline(job.id, feedback.trim()))
    } catch (e) {
      setError(String((e as Error).message ?? e))
      setBusy(null)
    }
  }

  async function onApprove() {
    if (!synopsis.trim()) {
      setError('줄거리 내용을 확인해 주세요.')
      return
    }
    setError(null)
    setBusy('approve')
    try {
      onUpdated(await approveOutline(job.id, title.trim(), synopsis.trim()))
    } catch (e) {
      setError(String((e as Error).message ?? e))
      setBusy(null)
    }
  }

  return (
    <div className="outline">
      <div className="check">📖</div>
      <h2>이런 이야기는 어떠세요?</h2>
      <p className="muted">
        그림을 그리기 전에, 먼저 이야기의 줄거리를 보여드려요.
        마음에 들면 그대로, 바꾸고 싶으면 아래에 적어주세요.
      </p>

      <label className="field-label">제목</label>
      <input
        className="text"
        value={title}
        maxLength={60}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="동화 제목"
      />

      <label className="field-label">줄거리 <span className="muted small">— 직접 고치셔도 돼요</span></label>
      <textarea
        className="text area outline-synopsis"
        value={synopsis}
        rows={7}
        onChange={(e) => setSynopsis(e.target.value)}
      />

      <label className="field-label">✏️ 이렇게 바꿔주세요 <span className="muted small">(선택)</span></label>
      <textarea
        className="text area"
        value={feedback}
        rows={2}
        placeholder="예: 동생도 함께 나오게 해주세요 / 마지막에 가족이 안아주는 장면을 넣어주세요 / 조금 더 신나게"
        onChange={(e) => setFeedback(e.target.value)}
      />

      {error && <p className="error-text center">{error}</p>}

      <div className="outline-actions">
        <button className="btn ghost" onClick={onRevise} disabled={busy !== null}>
          {busy === 'revise' ? '줄거리 다시 짓는 중…' : '🔄 이 요청으로 줄거리 다시 만들기'}
        </button>
        <button className="btn primary" onClick={onApprove} disabled={busy !== null}>
          {busy === 'approve' ? '만드는 중…' : '✅ 이 줄거리로 동화책 만들기'}
        </button>
      </div>
      <p className="muted small">‘다시 만들기’는 무료예요. 마음에 들 때까지 바꿔보세요.</p>
    </div>
  )
}
