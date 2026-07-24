import { useState } from 'react'
import { preparePayment } from './api'
import type { JobResponse } from './api'
import LegalModal, { type LegalDoc } from './Legal'

/**
 * 결제 화면. 주문 요약 + 약관·환불 규정 동의 + 결제하기.
 * '결제하기'는 서버에서 결제창 파라미터(해시)를 받아 키움페이 결제창(linkEnc)으로 폼 전송한다.
 */
export default function PaymentCheckout({ job, ready }: { job: JobResponse; ready: boolean }) {
  const [agree, setAgree] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [legal, setLegal] = useState<LegalDoc | null>(null)

  const amount = job.priceKrw ?? 0
  const tierLabel =
    job.physicalBookRequested ? 'PDF + 영상 + 실물책'
      : job.videoIncluded ? 'PDF + 읽어주는 영상'
        : 'PDF'

  async function onPay() {
    if (!agree) {
      setError('결제 진행을 위해 구매 조건·환불 규정에 동의해 주세요.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      const { action, fields } = await preparePayment(job.id, 'M')
      // 키움페이 결제창(linkEnc)으로 폼 POST 전송 → 결제창으로 이동.
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

  return (
    <div className="pay">
      <div className="check">💳</div>
      <h2>결제하기</h2>
      <p className="muted">아래 내용으로 결제를 진행해요. 결제가 완료되면 완성본 제작이 시작됩니다.</p>

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
        <div className="pay-row total">
          <span>결제 금액</span>
          <b>{amount.toLocaleString()}원 <span className="muted small">(VAT 포함)</span></b>
        </div>
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

      <button className="btn primary pay-btn" onClick={onPay} disabled={busy || !ready}>
        {busy ? '결제창 여는 중…' : `${amount.toLocaleString()}원 결제하기`}
      </button>
      {!ready && (
        <p className="muted small center">
          결제 시스템 준비 중입니다. (PG 심사 완료 후 결제가 활성화됩니다)
        </p>
      )}

      {legal && <LegalModal doc={legal} onClose={() => setLegal(null)} />}
    </div>
  )
}
