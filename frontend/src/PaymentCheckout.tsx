import { useState } from 'react'
import { preparePayment, checkCoupon, payFree } from './api'
import type { JobResponse, CouponCheck } from './api'
import LegalModal, { type LegalDoc } from './Legal'

/**
 * 결제 화면. 주문 요약 + 쿠폰 + 약관·환불 규정 동의 + 결제하기.
 * - 쿠폰 적용 시 서버에서 할인율·최종금액을 확정한다(클라이언트 금액 신뢰 금지).
 * - 100% 쿠폰 등으로 최종 0원이면 결제창 없이 "무료로 받기"로 바로 제작 시작(onProceed).
 * - 유료(0원 아님)는 키움페이 결제창(linkEnc)으로 폼 전송. CPID 미설정 시 결제 불가.
 */
export default function PaymentCheckout({
  job,
  ready,
  onProceed,
}: {
  job: JobResponse
  ready: boolean
  onProceed: (job: JobResponse) => void
}) {
  const [agree, setAgree] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [legal, setLegal] = useState<LegalDoc | null>(null)
  const [couponInput, setCouponInput] = useState('')
  const [coupon, setCoupon] = useState<CouponCheck | null>(null)
  const [couponBusy, setCouponBusy] = useState(false)

  const baseAmount = job.priceKrw ?? 0
  const finalAmount = coupon?.valid ? coupon.finalAmount : baseAmount
  const isFree = finalAmount <= 0
  const tierLabel =
    job.physicalBookRequested ? 'PDF + 영상 + 실물책'
      : job.videoIncluded ? 'PDF + 읽어주는 영상'
        : 'PDF'

  async function applyCoupon() {
    if (!couponInput.trim()) return
    setError(null)
    setCouponBusy(true)
    try {
      const r = await checkCoupon(job.id, couponInput.trim())
      setCoupon(r)
      if (!r.valid) setError(r.message || '사용할 수 없는 쿠폰이에요.')
    } catch (e) {
      setError(String((e as Error).message ?? e))
    } finally {
      setCouponBusy(false)
    }
  }

  function requireAgree(): boolean {
    if (!agree) {
      setError('진행을 위해 구매 조건·환불 규정에 동의해 주세요.')
      return false
    }
    return true
  }

  // 유료 결제 → 키움페이 결제창으로 폼 전송(쿠폰 적용된 금액).
  async function onPay() {
    if (!requireAgree()) return
    setError(null)
    setBusy(true)
    try {
      const { action, fields } = await preparePayment(job.id, 'M', coupon?.valid ? couponInput.trim() : undefined)
      const form = document.createElement('form')
      form.method = 'POST'
      form.action = action
      form.acceptCharset = 'UTF-8'
      Object.entries(fields).forEach(([k, v]) => {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = k
        input.value = String(v)
        form.appendChild(input)
      })
      document.body.appendChild(form)
      form.submit()
    } catch (e) {
      setError(String((e as Error).message ?? e))
      setBusy(false)
    }
  }

  // 0원(100% 쿠폰) → 결제 없이 바로 제작 시작.
  async function onFree() {
    if (!requireAgree()) return
    setError(null)
    setBusy(true)
    try {
      const updated = await payFree(job.id, couponInput.trim())
      onProceed(updated)
    } catch (e) {
      setError(String((e as Error).message ?? e))
      setBusy(false)
    }
  }

  return (
    <div className="pay">
      <div className="check">💳</div>
      <h2>결제하기</h2>
      <p className="muted">아래 내용으로 진행해요. 결제(또는 무료 쿠폰)가 완료되면 완성본 제작이 시작됩니다.</p>

      <div className="pay-summary">
        <div className="pay-row">
          <span>상품</span>
          <b>{job.generatedTitle ?? '우리 아이 동화책'}</b>
        </div>
        <div className="pay-row">
          <span>구성</span>
          <b>{tierLabel}{job.bookPages ? ` · ${job.bookPages}페이지` : ''}</b>
        </div>
        {job.physicalBookRequested && (
          <div className="pay-row">
            <span>배송</span>
            <b>실물 하드커버 배송 (PDF·영상도 함께 제공)</b>
          </div>
        )}
        {coupon?.valid && (
          <div className="pay-row">
            <span>쿠폰 할인</span>
            <b className="coupon-off">-{coupon.discountPercent}% ({(baseAmount - finalAmount).toLocaleString()}원)</b>
          </div>
        )}
        <div className="pay-row total">
          <span>최종 결제 금액</span>
          <b>
            {coupon?.valid && finalAmount !== baseAmount && (
              <span className="pay-strike">{baseAmount.toLocaleString()}원 </span>
            )}
            {finalAmount.toLocaleString()}원 <span className="muted small">(VAT 포함)</span>
          </b>
        </div>
      </div>

      {/* 쿠폰 입력 */}
      <div className="coupon-box">
        <label className="field-label">쿠폰 코드 <span className="muted small">(있으면 입력)</span></label>
        <div className="coupon-row">
          <input
            className="text"
            placeholder="쿠폰 코드"
            value={couponInput}
            onChange={(e) => {
              setCouponInput(e.target.value.toUpperCase())
              setCoupon(null)
            }}
          />
          <button className="btn ghost" onClick={applyCoupon} disabled={couponBusy || !couponInput.trim()}>
            {couponBusy ? '확인 중…' : '적용'}
          </button>
        </div>
        {coupon?.valid && <p className="coupon-ok">✅ {coupon.message}</p>}
      </div>

      <div className="pay-policy">
        <b>취소·환불 규정</b>
        <ul>
          <li>본 상품은 고객 요청에 따라 개별 제작되는 <b>주문제작 상품</b>입니다.</li>
          <li><b>제작(전체 생성) 시작 전</b>에는 100% 환불됩니다.</li>
          <li>제작이 시작된 이후에는 <b>주문제작 상품 특성상 청약철회가 제한</b>될 수 있습니다(전자상거래법 제17조).</li>
          <li>실물 하드커버는 <b>인쇄 전</b>까지 취소·환불이 가능합니다.</li>
          <li>제작·배송 하자 시 관계 법령에 따라 교환·환불해 드립니다.</li>
        </ul>
      </div>

      <label className="pay-agree">
        <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        <span>
          <button type="button" className="linklike" onClick={() => setLegal('terms')}>이용약관</button>·
          <button type="button" className="linklike" onClick={() => setLegal('privacy')}>개인정보처리방침</button>
          {' '}및 위 취소·환불 규정을 확인하였으며 결제에 동의합니다.
        </span>
      </label>

      {error && <p className="error-text center">{error}</p>}

      {isFree ? (
        <button className="btn primary pay-btn" onClick={onFree} disabled={busy || !agree}>
          {busy ? '처리 중…' : '무료로 받기 (0원)'}
        </button>
      ) : ready ? (
        <button className="btn primary pay-btn" onClick={onPay} disabled={busy || !agree}>
          {busy ? '결제창 여는 중…' : `${finalAmount.toLocaleString()}원 결제하기`}
        </button>
      ) : (
        <>
          <button className="btn primary pay-btn" disabled title="PG 심사/설정 완료 후 활성화됩니다">
            {`${finalAmount.toLocaleString()}원 결제하기`}
          </button>
          <p className="muted small center">
            결제 시스템 준비 중입니다. (PG 심사 완료 후 카드 결제가 활성화됩니다)
          </p>
        </>
      )}
      {!agree && <p className="muted small center">※ 위 약관에 동의해야 진행할 수 있어요.</p>}

      {legal && <LegalModal doc={legal} onClose={() => setLegal(null)} />}
    </div>
  )
}
