import { useEffect, useState } from 'react'
import { apiUrl, createReview, getReviews, loginUrl } from './api'
import type { Me, Review } from './api'
import LegalModal, { type LegalDoc } from './Legal'

/**
 * 상용 홈페이지(랜딩). 마케팅·기획·CS 관점을 반영한 섹션 구성.
 * onStart: "아이책 만들기" CTA → 로그인/생성 흐름으로 이동.
 */
export default function Landing({
  me,
  onStart,
  onLogout,
  onAdmin,
}: {
  me: Me | null
  onStart: () => void
  onLogout: () => void
  onAdmin?: () => void
}) {
  const [legal, setLegal] = useState<LegalDoc | null>(null)
  return (
    <div className="landing">
      {/* 상단 바 */}
      <div className="lp-nav">
        <span className="lp-brand">TodayHero</span>
        <span className="lp-nav-spacer" />
        {me?.authenticated ? (
          <>
            {onAdmin && (
              <button className="btn ghost small" onClick={onAdmin}>📊 관리자</button>
            )}
            <span className="muted small">{me.name ?? '회원'}님</span>
            <button className="btn ghost small" onClick={onLogout}>로그아웃</button>
          </>
        ) : null}
        <button className="btn primary small" onClick={onStart}>아이책 만들기</button>
      </div>

      {/* 히어로 */}
      <section className="lp-hero">
        <div className="lp-hero-copy">
          <p className="lp-eyebrow">우리 아이가 주인공인 동화</p>
          <h1>세상에 하나뿐인<br />우리 아이 동화책</h1>
          <p className="lp-lead">
            아이 사진 한 장이면, AI가 우리 아이를 닮은 캐릭터로
            정성스러운 동화책을 만들어드려요. <b>실물 하드커버</b>로 소장하고, 선물하세요.
          </p>
          <div className="lp-hero-cta">
            <button className="btn primary lp-cta" onClick={onStart}>아이책 만들기 →</button>
            <span className="muted small">미리보기 무료 · 마음에 들 때만 주문</span>
          </div>
        </div>
        <div className="lp-hero-img">
          <img src="/hero-book.jpg" alt="실물 하드커버 동화책" />
        </div>
      </section>

      {/* 실물 하드커버 */}
      <section className="lp-section lp-hardcover">
        <div className="lp-section-head">
          <p className="lp-eyebrow">진짜 손에 잡히는</p>
          <h2>실물 하드커버 책으로 받아보세요</h2>
          <p className="muted">화면 속 파일이 아니라, 튼튼한 양장 하드커버로 제작해 배송해드려요. 아이가 몇 번이고 펼쳐보는 진짜 책이 됩니다.</p>
        </div>
        <div className="lp-hardcover-imgs">
          <figure className="lp-hc-cover">
            <img src="/hero-book.jpg" alt="하드커버 표지" />
            <figcaption>양장 하드커버 표지</figcaption>
          </figure>
          <figure className="lp-hc-open">
            <img src="/book-open.jpg" alt="펼친 내지 — 실제 인쇄 예시" />
            <figcaption>펼치면 이렇게 — 실제 받아보는 페이지 그대로예요</figcaption>
          </figure>
        </div>
        <div className="lp-badges">
          <div className="lp-badge"><b>양장 하드커버</b><span>튼튼하고 고급스러운 제본</span></div>
          <div className="lp-badge"><b>가로 대형 판형</b><span>그림이 시원하게 꽉 차게</span></div>
          <div className="lp-badge"><b>안전한 인쇄</b><span>아이가 봐도 안심되는 마감</span></div>
        </div>
      </section>

      {/* 이렇게 만들어져요 */}
      <section className="lp-section">
        <div className="lp-section-head">
          <p className="lp-eyebrow">3단계면 끝</p>
          <h2>이렇게 만들어져요</h2>
        </div>
        <div className="lp-steps">
          <div className="lp-step"><span className="lp-step-n">1</span><b>사진 올리기</b><p>우리 아이 사진을 올려요. 형제·친구도 함께 넣을 수 있어요.</p></div>
          <div className="lp-step"><span className="lp-step-n">2</span><b>주제·스타일 고르기</b><p>공주·용사·바다 등 주제와 그림체, 아이 연령을 선택해요.</p></div>
          <div className="lp-step"><span className="lp-step-n">3</span><b>미리보기 후 완성</b><p>앞부분을 미리 보고, 마음에 들면 전체를 완성해 받아요.</p></div>
        </div>
      </section>

      {/* 왜 TodayHero */}
      <section className="lp-section lp-why">
        <div className="lp-section-head">
          <p className="lp-eyebrow">왜 TodayHero</p>
          <h2>여기서만 되는 것들</h2>
        </div>
        <div className="lp-feature-grid">
          <div className="lp-feature"><h3>우리 아이 얼굴 그대로</h3><p>사진 속 아이를 닮은 캐릭터가 모든 페이지에 일관되게 등장해요.</p></div>
          <div className="lp-feature"><h3>실물 하드커버</h3><p>파일뿐 아니라 진짜 양장 책으로 제작·배송.</p></div>
          <div className="lp-feature"><h3>연령별 맞춤 글</h3><p>3~10세, 나이에 맞춰 문장 길이와 의성어까지 조절.</p></div>
          <div className="lp-feature"><h3>헌정 가족사진</h3><p>첫 장에 부모님의 메시지와 가족사진을 그대로 담아요.</p></div>
          <div className="lp-feature"><h3>우리 아이 이름으로</h3><p>표지와 이야기에 아이 이름이 들어가 더 특별해요.</p></div>
          <div className="lp-feature"><h3>안 닮으면 다시</h3><p>미리보기로 먼저 확인, 마음에 안 들면 다시 만들어드려요.</p></div>
        </div>
      </section>

      {/* 갤러리 — 실제 아이가 주인공이 된 예시 */}
      <section className="lp-section">
        <div className="lp-section-head">
          <p className="lp-eyebrow">작품 갤러리</p>
          <h2>이런 그림이 나와요</h2>
          <p className="muted">실제 아이 사진으로 만든 예시예요. 우리 아이가 동화의 주인공이 됩니다.</p>
        </div>
        <div className="lp-gallery-ba">
          <figure className="lp-ba-item">
            <img src="/example-girl.jpg" alt="공주가 된 우리 아이 — 실제 사진에서 변형" />
            <figcaption>공주가 된 우리 아이</figcaption>
          </figure>
          <figure className="lp-ba-item">
            <img src="/example-boy.jpg" alt="용사가 된 우리 아이 — 실제 사진에서 변형" />
            <figcaption>용사가 된 우리 아이</figcaption>
          </figure>
        </div>
      </section>

      {/* 후기 */}
      <ReviewsSection me={me} />

      {/* 상품·가격 */}
      <section className="lp-section lp-pricing">
        <div className="lp-section-head">
          <p className="lp-eyebrow">상품 · 가격</p>
          <h2>원하는 방식으로</h2>
        </div>
        <div className="lp-price-grid">
          <div className="lp-price">
            <h3>PDF 소장</h3>
            <div className="lp-price-num">9,900원~</div>
            <ul>
              <li>24페이지 9,900원</li>
              <li>36페이지 13,200원</li>
              <li>이메일로 바로 발송 · 미리보기 무료</li>
            </ul>
            <button className="btn ghost" onClick={onStart}>만들기</button>
          </div>
          <div className="lp-price featured">
            <div className="lp-price-tag">추천</div>
            <h3>PDF + 읽어주는 영상</h3>
            <div className="lp-price-num">15,400원~</div>
            <ul>
              <li>24페이지 15,400원</li>
              <li>36페이지 20,900원</li>
              <li>인물마다 목소리가 다른 <b>낭독 영상</b></li>
              <li>PDF 파일도 함께 제공</li>
            </ul>
            <button className="btn primary" onClick={onStart}>만들기</button>
          </div>
          <div className="lp-price">
            <h3>실물책 (PDF+영상 포함)</h3>
            <div className="lp-price-num">48,400원~</div>
            <ul>
              <li>24페이지 48,400원</li>
              <li>36페이지 53,900원</li>
              <li>양장 하드커버 실물 배송</li>
              <li><b>PDF·영상도 함께 제공</b></li>
            </ul>
            <button className="btn ghost" onClick={onStart}>만들기</button>
          </div>
        </div>
        <p className="muted small center">* 표시된 금액은 모두 <b>VAT 포함가</b>예요. 미리보기 이후 결제 단계에서 확인됩니다.</p>
      </section>

      {/* FAQ */}
      <section className="lp-section">
        <div className="lp-section-head">
          <p className="lp-eyebrow">자주 묻는 질문</p>
          <h2>궁금해요</h2>
        </div>
        <div className="lp-faq">
          <details><summary>사진은 몇 장 필요한가요?</summary><p>정면 얼굴이 잘 보이는 사진 1장이면 돼요. 여러 장이면 더 정확하게 표현됩니다.</p></details>
          <details><summary>우리 아이랑 안 닮으면요?</summary><p>완성 전 미리보기로 먼저 확인하실 수 있어요. 마음에 들지 않으면 다시 만들어드립니다.</p></details>
          <details><summary>하드커버 배송은 얼마나 걸려요?</summary><p>주문 확정 후 제작·배송됩니다(예상 약 7~10일). 정확한 일정은 주문 시 안내드려요.</p></details>
          <details><summary>형제·자매도 함께 넣을 수 있나요?</summary><p>네, 여러 명을 주인공·친구로 함께 등장시킬 수 있어요.</p></details>
          <details><summary>사진은 안전하게 관리되나요?</summary><p>동화 제작 목적으로만 사용하며 안전하게 관리합니다. 개인정보처리방침을 따릅니다.</p></details>
        </div>
      </section>

      {/* 최종 CTA */}
      <section className="lp-final">
        <h2>지금, 우리 아이를 주인공으로</h2>
        <p className="muted">미리보기는 무료예요. 마음에 들 때만 주문하세요.</p>
        <button className="btn primary lp-cta" onClick={onStart}>아이책 만들기 →</button>
      </section>

      {/* 푸터 */}
      <footer className="lp-footer">
        <div className="lp-footer-brand">TodayHero</div>
        <div className="lp-footer-links">
          <a href="#" onClick={(e) => { e.preventDefault(); setLegal('terms') }}>이용약관</a>
          <a href="#" onClick={(e) => { e.preventDefault(); setLegal('privacy') }}>개인정보처리방침</a>
        </div>
        <div className="lp-footer-biz muted small">
          <p>주식회사 제이디엔드 | 대표 박동천 | 사업자등록번호 766-86-02631</p>
          <p>사업장 소재지 : 서울특별시 영등포구 여의대방로379, 605호</p>
          <p>문의 : jdgp@jdgp.co.kr</p>
        </div>
      </footer>

      {legal && <LegalModal doc={legal} onClose={() => setLegal(null)} />}
    </div>
  )
}

/** 별점 문자열(채운 별 + 빈 별). */
function stars(n: number) {
  const f = Math.max(0, Math.min(5, Math.round(n)))
  return '★★★★★'.slice(0, f) + '☆☆☆☆☆'.slice(0, 5 - f)
}

/** 예시 후기(실제 후기가 하나도 없을 때만 노출, "예시" 배지 표시). */
const EXAMPLE_REVIEWS = [
  { rating: 5, content: '아이가 자기 얼굴이 나온 책이라고 매일 밤 가져와요. 하드커버라 선물로도 좋았어요.', who: '5세 아이 엄마', photo: null as string | null },
  { rating: 5, content: '펼쳐진 그림이 정말 예뻐요. 미리보기로 확인하고 주문해서 안심됐어요.', who: '7세 아이 아빠', photo: '/book-open.jpg' },
  { rating: 4, content: '책 퀄리티가 생각보다 좋네요. 둘째 것도 만들려고요.', who: '4세·6세 남매 엄마', photo: '/hero-book.jpg' },
]

/**
 * 고객 후기 섹션: 실제 후기를 불러와 표시하고, 로그인 사용자는 별점·글·사진으로 후기를 남긴다.
 * 실제 후기가 없으면 예시 후기를 "예시" 배지와 함께 보여준다.
 */
function ReviewsSection({ me }: { me: Me | null }) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [count, setCount] = useState(0)
  const [average, setAverage] = useState(0)
  const [open, setOpen] = useState(false)

  const load = () =>
    getReviews()
      .then((d) => {
        setReviews(d.items)
        setCount(d.count)
        setAverage(d.average)
      })
      .catch(() => undefined)

  useEffect(() => {
    load()
  }, [])

  const hasReal = reviews.length > 0

  return (
    <section className="lp-section lp-reviews">
      <div className="lp-section-head">
        <p className="lp-eyebrow">고객 후기 · 포토리뷰</p>
        <h2>먼저 만난 부모님들</h2>
        {hasReal ? (
          <p className="muted small">
            <b className="lp-avg">{stars(average)} {average.toFixed(1)}</b> · 후기 {count}개
          </p>
        ) : (
          <p className="muted small">아래는 예시 후기예요. 실제 후기가 쌓이면 이 자리에 보여집니다.</p>
        )}
      </div>

      <div className="lp-review-grid">
        {hasReal
          ? reviews.map((r) => (
              <div key={r.id} className={r.photoUrl ? 'lp-review has-photo' : 'lp-review'}>
                {r.photoUrl && <img src={apiUrl(r.photoUrl)} alt="포토리뷰" loading="lazy" />}
                <div className="lp-stars">{stars(r.rating)}</div>
                <p>{r.content}</p>
                <div className="lp-review-meta">{r.authorName}{r.createdAt ? ` · ${r.createdAt}` : ''}</div>
              </div>
            ))
          : EXAMPLE_REVIEWS.map((r, i) => (
              <div key={i} className={r.photo ? 'lp-review has-photo' : 'lp-review'}>
                {r.photo && <img src={r.photo} alt="포토리뷰" />}
                <div className="lp-stars">{stars(r.rating)}</div>
                <p>“{r.content}”</p>
                <div className="lp-review-meta"><span className="lp-example">예시</span> {r.who}</div>
              </div>
            ))}
      </div>

      <div className="center">
        <button className="btn primary" onClick={() => setOpen(true)}>후기 남기기</button>
      </div>

      {open && (
        <ReviewModal
          me={me}
          onClose={() => setOpen(false)}
          onSubmitted={() => {
            setOpen(false)
            load()
          }}
        />
      )}
    </section>
  )
}

/** 후기 작성 모달. 비로그인 시 로그인 안내, 로그인 시 별점·글·사진 폼. */
function ReviewModal({
  me,
  onClose,
  onSubmitted,
}: {
  me: Me | null
  onClose: () => void
  onSubmitted: () => void
}) {
  const [rating, setRating] = useState(5)
  const [content, setContent] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    if (!content.trim()) {
      setError('후기 내용을 입력해 주세요.')
      return
    }
    setSending(true)
    setError(null)
    createReview({ rating, content: content.trim(), photo })
      .then(() => onSubmitted())
      .catch((e) => setError(e?.message ?? '전송에 실패했어요.'))
      .finally(() => setSending(false))
  }

  return (
    <div className="lp-modal-backdrop" onClick={onClose}>
      <div className="lp-modal" onClick={(e) => e.stopPropagation()}>
        <button className="lp-modal-x" onClick={onClose} aria-label="닫기">✕</button>
        <h3>후기 남기기</h3>

        {!me?.authenticated ? (
          <div className="lp-modal-login">
            <p className="muted">후기는 로그인 후 남길 수 있어요.</p>
            {me?.loginEnabled ? (
              <div className="lp-modal-login-btns">
                <a className="btn login-kakao" href={loginUrl('kakao')}>카카오로 로그인</a>
                <a className="btn login-google" href={loginUrl('google')}>구글로 로그인</a>
              </div>
            ) : (
              <p className="muted small">지금은 로그인이 준비 중이에요.</p>
            )}
          </div>
        ) : (
          <>
            <label className="lp-field-label">별점</label>
            <div className="lp-star-pick">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  type="button"
                  className={n <= rating ? 'on' : ''}
                  onClick={() => setRating(n)}
                  aria-label={`${n}점`}
                >
                  ★
                </button>
              ))}
            </div>

            <label className="lp-field-label">후기</label>
            <textarea
              className="lp-textarea"
              value={content}
              maxLength={1000}
              placeholder="아이 반응, 그림·책 퀄리티 등 솔직한 후기를 남겨 주세요."
              onChange={(e) => setContent(e.target.value)}
            />

            <label className="lp-field-label">사진 (선택)</label>
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
            />
            {photo && <p className="muted small">{photo.name}</p>}

            {error && <p className="lp-error">{error}</p>}

            <button className="btn primary lp-modal-submit" onClick={submit} disabled={sending}>
              {sending ? '올리는 중…' : '후기 등록'}
            </button>
            <p className="muted small center">{me.name ?? '회원'}님 이름으로 등록돼요.</p>
          </>
        )}
      </div>
    </div>
  )
}
