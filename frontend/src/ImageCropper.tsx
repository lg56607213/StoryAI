import { useEffect, useRef, useState } from 'react'

interface Box {
  x: number
  y: number
  w: number
  h: number
}

interface Props {
  file: File
  index: number
  total: number
  onCancel: () => void
  onConfirm: (cropped: File) => void
}

const MAX_DISPLAY = 520
const MIN_BOX = 48
const OUT_MAX = 1400 // 출력 최대 변 길이(px)

/** 사진에서 자녀 부분만 잘라내는 크롭 모달. 자유 비율, 이동/크기조절 지원. */
export default function ImageCropper({ file, index, total, onCancel, onConfirm }: Props) {
  const [src, setSrc] = useState('')
  const [disp, setDisp] = useState({ w: 0, h: 0 })
  const natural = useRef({ w: 0, h: 0 })
  const [box, setBox] = useState<Box>({ x: 0, y: 0, w: 0, h: 0 })
  const drag = useRef<{ mode: 'move' | 'resize'; px: number; py: number; start: Box } | null>(null)

  useEffect(() => {
    const url = URL.createObjectURL(file)
    setSrc(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  function onImgLoad(e: React.SyntheticEvent<HTMLImageElement>) {
    const img = e.currentTarget
    natural.current = { w: img.naturalWidth, h: img.naturalHeight }
    const scale = Math.min(MAX_DISPLAY / img.naturalWidth, MAX_DISPLAY / img.naturalHeight, 1)
    const w = Math.round(img.naturalWidth * scale)
    const h = Math.round(img.naturalHeight * scale)
    setDisp({ w, h })
    const bw = Math.round(w * 0.7)
    const bh = Math.round(h * 0.7)
    setBox({ x: Math.round((w - bw) / 2), y: Math.round((h - bh) / 2), w: bw, h: bh })
  }

  function clamp(v: number, lo: number, hi: number) {
    return Math.max(lo, Math.min(hi, v))
  }

  function startDrag(mode: 'move' | 'resize', e: React.PointerEvent) {
    e.preventDefault()
    e.stopPropagation()
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
    drag.current = { mode, px: e.clientX, py: e.clientY, start: { ...box } }
  }

  function onMove(e: React.PointerEvent) {
    if (!drag.current) return
    const { mode, px, py, start } = drag.current
    const dx = e.clientX - px
    const dy = e.clientY - py
    if (mode === 'move') {
      setBox({
        ...start,
        x: clamp(start.x + dx, 0, disp.w - start.w),
        y: clamp(start.y + dy, 0, disp.h - start.h),
      })
    } else {
      setBox({
        ...start,
        w: clamp(start.w + dx, MIN_BOX, disp.w - start.x),
        h: clamp(start.h + dy, MIN_BOX, disp.h - start.y),
      })
    }
  }

  function endDrag() {
    drag.current = null
  }

  function confirm() {
    const scale = natural.current.w / disp.w
    const sx = box.x * scale
    const sy = box.y * scale
    const sw = box.w * scale
    const sh = box.h * scale
    const outScale = Math.min(OUT_MAX / sw, OUT_MAX / sh, 1)
    const ow = Math.max(1, Math.round(sw * outScale))
    const oh = Math.max(1, Math.round(sh * outScale))
    const canvas = document.createElement('canvas')
    canvas.width = ow
    canvas.height = oh
    const ctx = canvas.getContext('2d')
    const img = new Image()
    img.onload = () => {
      ctx?.drawImage(img, sx, sy, sw, sh, 0, 0, ow, oh)
      canvas.toBlob(
        (blob) => {
          if (!blob) return
          const base = file.name.replace(/\.[^.]+$/, '')
          onConfirm(new File([blob], `${base}-crop.jpg`, { type: 'image/jpeg' }))
        },
        'image/jpeg',
        0.92,
      )
    }
    img.src = src
  }

  return (
    <div className="crop-overlay" onPointerMove={onMove} onPointerUp={endDrag}>
      <div className="crop-card">
        <p className="crop-title">
          자녀만 남기고 잘라주세요{total > 1 ? ` (${index + 1}/${total})` : ''}
        </p>
        <div className="crop-stage" style={{ width: disp.w, height: disp.h }}>
          {src && <img src={src} alt="" onLoad={onImgLoad} draggable={false} />}
          {disp.w > 0 && (
            <div
              className="crop-box"
              style={{ left: box.x, top: box.y, width: box.w, height: box.h }}
              onPointerDown={(e) => startDrag('move', e)}
            >
              <span
                className="crop-handle"
                onPointerDown={(e) => startDrag('resize', e)}
              />
            </div>
          )}
        </div>
        <div className="crop-actions">
          <button className="btn ghost small" onClick={onCancel}>
            원본 그대로 사용
          </button>
          <button className="btn primary small" onClick={confirm}>
            잘라서 담기
          </button>
        </div>
      </div>
    </div>
  )
}
