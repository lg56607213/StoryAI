import type { JobResponse } from './api'

/**
 * 주문 완료 안내. 생성은 백그라운드로 진행되고, 완료되면 이메일로 발송된다.
 * 사용자가 화면에서 기다릴 필요 없이 닫고 나갈 수 있도록 안내만 보여준다.
 */
export default function CompletionScreen({
  job,
  onHome,
  onMyPage,
}: {
  job: JobResponse
  onHome: () => void
  onMyPage: () => void
}) {
  const withVideo = job.videoIncluded
  // 실측 기준 추정: PDF만 보통 5~10분, 영상 포함이면 음성·합성이 더해져 10~20분.
  const eta = withVideo ? '10~20분' : '5~10분'
  const deliverable = withVideo ? 'PDF 및 영상' : 'PDF'
  const email = job.deliveryEmail

  return (
    <main className="app">
      <header className="hero">
        <h1>TodayHero</h1>
        <p>우리 아이가 주인공인 동화</p>
      </header>
      <div className="card result done-card">
        <div className="check">🎉</div>
        <h2>주문이 접수되었어요!</h2>
        <p className="done-lead">
          동화책을 정성껏 만들고 있어요. 완성되면 요청하신 메일{email ? <> (<b>{email}</b>)</> : null}로{' '}
          <b>{deliverable}</b>를 보내드릴게요.
        </p>

        <div className="done-eta">
          <span>⏳ 예상 소요 시간</span>
          <b>약 {eta}</b>
        </div>

        <p className="muted small center">
          지금 이 화면을 닫으셔도 괜찮아요. 생성은 계속 진행됩니다.
          <br />
          만들어진 동화책은 <b>마이페이지</b>에서 언제든지 다시 볼 수 있어요.
        </p>

        <div className="done-actions">
          <button className="btn primary" onClick={onMyPage}>
            마이페이지에서 확인
          </button>
          <button className="btn ghost" onClick={onHome}>
            홈으로
          </button>
        </div>

        <p className="muted small center done-thanks">감사합니다 💛</p>
      </div>
    </main>
  )
}
