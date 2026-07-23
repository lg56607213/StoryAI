import { useEffect, useMemo, useState } from 'react'
import { apiUrl, getMyBooks, getMyQuota, hideMyBook } from './api'
import type { Me, MyBook, MyQuota } from './api'

type Tab = 'books' | 'orders'
const FILTERS = ['전체', '제작 중', '미리보기', '제작 완료'] as const
type Filter = (typeof FILTERS)[number]

/**
 * 고객 마이페이지 — 본인이 만든 동화책 이력과 주문 내역을 관리한다.
 * 좌측 프로필·메뉴 + 우측 상태 필터 목록 구성.
 */
export default function MyPage({
  me,
  onHome,
  onOpenBook,
  onStart,
  onLogout,
}: {
  me: Me
  onHome: () => void
  onOpenBook: (id: number) => void
  onStart: () => void
  onLogout: () => void
}) {
  const [tab, setTab] = useState<Tab>('books')
  const [filter, setFilter] = useState<Filter>('전체')
  const [books, setBooks] = useState<MyBook[]>([])
  const [quota, setQuota] = useState<MyQuota | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    Promise.all([getMyBooks(), getMyQuota()])
      .then(([b, q]) => {
        setBooks(b)
        setQuota(q)
        setError(null)
      })
      .catch((e) => setError(e?.message ?? '불러오기 실패'))
      .finally(() => setLoading(false))
  }, [])

  const orders = useMemo(() => books.filter((b) => b.confirmedAt), [books])
  const source = tab === 'orders' ? orders : books
  const shown = useMemo(
    () => (filter === '전체' ? source : source.filter((b) => b.stage === filter)),
    [source, filter],
  )

  async function onDelete(id: number) {
    if (!confirm('내 목록에서 삭제할까요? (주문 기록은 보관됩니다)')) return
    try {
      await hideMyBook(id)
      setBooks((prev) => prev.filter((b) => b.id !== id))
    } catch (e) {
      alert(String((e as Error).message ?? e))
    }
  }

  const initial = (me.name ?? me.email ?? '나').trim().charAt(0)
  const fmt = (s: string | null) =>
    s ? new Date(s).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : ''

  return (
    <main className="app mypage">
      <div className="login-bar">
        <button className="btn ghost small" onClick={onHome}>← 홈</button>
        <span className="login-bar-spacer" />
        <button className="btn ghost small" onClick={onStart}>+ 새 동화 만들기</button>
      </div>

      <div className="mp-layout">
        {/* 좌측: 프로필 + 메뉴 */}
        <aside className="mp-side">
          <div className="mp-profile">
            <div className="mp-avatar">{initial}</div>
            <div className="mp-name">{me.name ?? me.email}</div>
            {me.email && <div className="mp-email">{me.email}</div>}
          </div>
          <nav className="mp-nav">
            <button className={tab === 'books' ? 'on' : ''} onClick={() => { setTab('books'); setFilter('전체') }}>
              내 동화책
            </button>
            <button className={tab === 'orders' ? 'on' : ''} onClick={() => { setTab('orders'); setFilter('전체') }}>
              주문 · 배송
            </button>
          </nav>
          <div className="mp-nav-foot">
            <button onClick={onLogout}>로그아웃</button>
          </div>
        </aside>

        {/* 우측: 목록 */}
        <section className="mp-main">
          {quota && quota.remaining >= 0 && (
            <div className="mp-quota">
              오늘 만들 수 있는 동화 <b>{quota.remaining}권</b> 남았어요
              <span className="muted small"> (하루 {quota.limit}권까지)</span>
            </div>
          )}

          <div className="mp-filters">
            {FILTERS.map((f) => (
              <button key={f} className={`chip ${filter === f ? 'on' : ''}`} onClick={() => setFilter(f)}>
                {f}
              </button>
            ))}
          </div>

          {loading && <p className="muted center">불러오는 중…</p>}
          {error && <p className="error-text center">{error}</p>}

          {!loading && !error && shown.length === 0 && (
            <div className="mp-empty">
              <p className="muted">
                {tab === 'orders' ? '아직 주문한 동화책이 없어요.' : '아직 만든 동화책이 없어요.'}
              </p>
              <button className="btn primary" onClick={onStart}>첫 동화 만들기</button>
            </div>
          )}

          <ul className="mp-list">
            {shown.map((b) => (
              <li className="mp-item" key={b.id}>
                <button className="mp-item-main" onClick={() => onOpenBook(b.id)}>
                  <span className="mp-thumb">
                    {b.thumbnailUrl
                      ? <img src={apiUrl(b.thumbnailUrl)} alt="" loading="lazy" />
                      : <span className="mp-thumb-ph">📖</span>}
                  </span>
                  <span className="mp-info">
                    <span className="mp-title">{b.title ?? `${b.theme} 이야기`}</span>
                    <span className="mp-meta">
                      <span className={`mp-badge ${badgeClass(b.stage)}`}>{b.stage}</span>
                      {b.videoIncluded && <span className="mp-badge video">영상</span>}
                      {b.physicalBookRequested && <span className="mp-badge book">실물책</span>}
                      <span className="muted small">{fmt(b.createdAt)}</span>
                    </span>
                  </span>
                </button>
                <button className="mp-del" onClick={() => onDelete(b.id)} aria-label="삭제">🗑</button>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  )
}

function badgeClass(stage: string) {
  if (stage === '제작 완료') return 'done'
  if (stage === '미리보기') return 'preview'
  if (stage === '실패') return 'fail'
  return 'running'
}
