import { useEffect, useState } from 'react'
import {
  getAdminStats,
  getAdminPurchases,
  type AdminStats,
  type AdminPurchase,
} from './api'

/** 관리자 대시보드: 일자별 생성/구매 통계 + 구매요청 목록 + 예상 원가. */
export default function AdminDashboard({ onHome }: { onHome: () => void }) {
  const [days, setDays] = useState(30)
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [purchases, setPurchases] = useState<AdminPurchase[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([getAdminStats(days), getAdminPurchases()])
      .then(([s, p]) => {
        setStats(s)
        setPurchases(p)
        setError(null)
      })
      .catch((e) => setError(e?.message ?? '불러오기 실패'))
      .finally(() => setLoading(false))
  }, [days])

  const won = (n: number | null | undefined) =>
    n == null ? '-' : n.toLocaleString('ko-KR') + '원'

  // 최근 날짜가 위로 오도록 역순, 데이터 있는 날 위주로.
  const rows = stats ? [...stats.daily].reverse() : []
  const maxPrev = Math.max(1, ...rows.map((r) => r.previews))

  return (
    <main className="app admin">
      <div className="login-bar">
        <button className="btn ghost small" onClick={onHome}>← 홈</button>
        <span className="login-bar-spacer" />
        <span className="muted small">관리자</span>
      </div>

      <header className="hero">
        <h1 style={{ fontSize: 28 }}>📊 관리자 대시보드</h1>
        <p>생성·구매 현황과 예상 원가를 한눈에</p>
      </header>

      <div className="admin-period">
        {[7, 30, 90].map((d) => (
          <button
            key={d}
            className={`chip ${days === d ? 'on' : ''}`}
            onClick={() => setDays(d)}
          >
            최근 {d}일
          </button>
        ))}
      </div>

      {error && <div className="card admin-error">불러오기 실패: {error}</div>}
      {loading && <div className="card">불러오는 중…</div>}

      {stats && !loading && (
        <>
          {/* 요약 카드 */}
          <div className="admin-cards">
            <div className="admin-card">
              <span className="ac-label">미리보기 생성</span>
              <span className="ac-num">{stats.totals.previews}</span>
              <span className="ac-sub">건</span>
            </div>
            <div className="admin-card highlight">
              <span className="ac-label">구매요청</span>
              <span className="ac-num">{stats.totals.purchases}</span>
              <span className="ac-sub">건 (PDF {stats.totals.pdf} · 책 {stats.totals.hardcover})</span>
            </div>
            <div className="admin-card">
              <span className="ac-label">완성</span>
              <span className="ac-num">{stats.totals.completed}</span>
              <span className="ac-sub">건</span>
            </div>
            <div className="admin-card cost">
              <span className="ac-label">예상 원가</span>
              <span className="ac-num">{won(stats.estCostKrw)}</span>
              <span className="ac-sub">이미지 ~{stats.estImages}장 × {stats.costPerImageKrw}원</span>
            </div>
          </div>
          <p className="muted small center">
            * 구매요청 = 미리보기 확정 건. 원가는 이미지 생성 수 기반 추정치예요.
          </p>

          {/* 일자별 표 */}
          <section className="card">
            <h3 className="step">일자별 현황 (최근 {stats.days}일)</h3>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>날짜</th>
                    <th>미리보기</th>
                    <th>구매요청</th>
                    <th>PDF</th>
                    <th>하드커버</th>
                    <th>완성</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.date} className={r.previews || r.purchases ? '' : 'empty-day'}>
                      <td>{r.date.slice(5)}</td>
                      <td>
                        <span className="bar" style={{ width: `${(r.previews / maxPrev) * 60 + 4}px` }} />
                        {r.previews}
                      </td>
                      <td className={r.purchases ? 'buy' : ''}>{r.purchases || '-'}</td>
                      <td>{r.pdf || '-'}</td>
                      <td>{r.hardcover || '-'}</td>
                      <td>{r.completed || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* 구매요청 목록 */}
          <section className="card">
            <h3 className="step">구매요청 목록 ({purchases.length}건)</h3>
            {purchases.length === 0 ? (
              <p className="muted small">아직 구매요청이 없어요.</p>
            ) : (
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>일시</th>
                      <th>유형</th>
                      <th>페이지</th>
                      <th>가격</th>
                      <th>요청 계정</th>
                      <th>받을 이메일</th>
                      <th>상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    {purchases.map((p) => (
                      <tr key={p.id}>
                        <td>{p.confirmedAt ?? '-'}</td>
                        <td>
                          <span className={p.type === '하드커버' ? 'tag book' : 'tag pdf'}>{p.type}</span>
                        </td>
                        <td>{p.pages ?? '-'}p</td>
                        <td>{won(p.priceKrw)}</td>
                        <td>
                          {p.requesterEmail ?? <span className="muted">비로그인</span>}
                          {p.requesterProvider ? <span className="muted small"> ({p.requesterProvider})</span> : null}
                        </td>
                        <td>{p.deliveryEmail ?? '-'}</td>
                        <td>
                          <span className={`status ${p.status}`}>{statusKo(p.status)}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </main>
  )
}

function statusKo(s: string | null) {
  switch (s) {
    case 'COMPLETED':
      return '완성'
    case 'RUNNING':
      return '생성중'
    case 'FAILED':
      return '실패'
    case 'PENDING':
      return '대기'
    default:
      return s ?? '-'
  }
}
