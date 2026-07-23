// StoryAI 백엔드 API 클라이언트.
// dev: vite.config.ts 프록시로 /api → :8080. prod: VITE_API_BASE(백엔드 도메인)를 앞에 붙인다.
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')
/** 상대 API 경로(예: "/api/...")를 배포 백엔드 절대 URL로 변환. dev에선 그대로 프록시. */
export const apiUrl = (path: string) => `${API_BASE}${path}`

export interface Option {
  code: string
  label: string
}

export interface PriceRow {
  pages: number
  priceKrw: number
}
export interface Bundle {
  code: string
  label: string
  video: boolean
  physical: boolean
  prices: PriceRow[]
}
export interface Pricing {
  currency: string
  vatIncluded: boolean
  bookPdf: PriceRow[]
  bookHardcover: PriceRow[]
  bookVideoAddon: PriceRow[]
  bundles: Bundle[]
  note: string
}

export interface Options {
  outputTypes: Option[]
  themes: Option[]
  ageGroups: Option[]
  bookStyles: Option[]
  bookPageOptions: number[]
  videoStyles: Option[]
  videoDurationOptions: number[]
  characterRoles: Option[]
  pricing: Pricing
}

export interface CharacterInput {
  name: string
  role: string
  /** role === 'CUSTOM' 일 때 직접 입력한 관계 */
  customRole?: string
  photoUrls: string[]
}

export interface CreateRequest {
  outputType: string
  /** 직접입력 주제를 쓰면 생략 가능 */
  theme?: string | null
  /** 목록에 없는 주제를 직접 입력할 때 */
  customTheme?: string
  ageGroup: string
  dedication?: string
  dedicationPhotoUrl?: string
  storyDirection?: string
  mood?: string
  bookStyle?: string | null
  bookPages?: number | null
  physicalBookRequested: boolean
  videoStyle?: string | null
  videoDurationSec?: number | null
  characters: CharacterInput[]
}

export interface JobResponse {
  id: number
  outputType: string
  theme: string
  bookStyle: string | null
  bookPages: number | null
  bookPhase: 'PREVIEW' | 'FULL'
  physicalBookRequested: boolean
  videoIncluded: boolean
  videoStyle: string | null
  videoDurationSec: number | null
  characters: { name: string; role: string }[]
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  currentStep: string
  generatedTitle: string | null
  priceKrw: number | null
  resultUrl: string | null
  resultVideoUrl: string | null
  narrationVideoUrl: string | null
  narrationVideoStatus: string | null
  hasParentVoice: boolean
  deliveryEmail: string | null
  emailSent: boolean
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `요청 실패 (HTTP ${res.status})`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      // ignore
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

// 모든 요청에 로그인 세션 쿠키를 함께 보낸다.
const withCreds: RequestInit = { credentials: 'include' }

export function getOptions(): Promise<Options> {
  return fetch(apiUrl('/api/options'), withCreds).then((r) => handle<Options>(r))
}

/** 사진 파일들을 서버에 업로드하고 저장된 URL 목록을 반환. */
export function uploadPhotos(files: File[]): Promise<string[]> {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  return fetch(apiUrl('/api/uploads'), { method: 'POST', body: form, ...withCreds })
    .then((r) => handle<{ urls: string[] }>(r))
    .then((d) => d.urls)
}

export function createProject(req: CreateRequest): Promise<JobResponse> {
  return fetch(apiUrl('/api/video-jobs'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    ...withCreds,
  }).then((r) => handle<JobResponse>(r))
}

export function getProject(id: number): Promise<JobResponse> {
  return fetch(apiUrl(`/api/video-jobs/${id}`), withCreds).then((r) => handle<JobResponse>(r))
}

/** 부모 목소리 등록 — 녹음 파일을 올려 음성을 복제한다(동의 필수). */
export function uploadParentVoice(id: number, audio: Blob, consent: boolean): Promise<JobResponse> {
  // 실제 녹음 형식에 맞는 확장자를 붙인다(아이폰=mp4, 안드로이드/PC=webm 등).
  const type = audio.type || 'audio/webm'
  const ext = type.includes('mp4') ? 'mp4'
    : type.includes('mpeg') ? 'mp3'
    : type.includes('ogg') ? 'ogg'
    : type.includes('wav') ? 'wav'
    : 'webm'
  const form = new FormData()
  form.append('file', audio, `parent-voice.${ext}`)
  form.append('consent', String(consent))
  return fetch(apiUrl(`/api/video-jobs/${id}/parent-voice`), {
    method: 'POST',
    body: form,
    ...withCreds,
  }).then((r) => handle<JobResponse>(r))
}

/** 낭독 영상(mp4) 생성 시작. 비동기로 시작되며 이후 getProject 폴링으로 narrationVideoStatus/Url 확인. */
export function startNarrationVideo(id: number): Promise<JobResponse> {
  return fetch(apiUrl(`/api/video-jobs/${id}/narration-video`), {
    method: 'POST',
    ...withCreds,
  }).then((r) => handle<JobResponse>(r))
}

/** 미리보기 확정 → 전체 생성 시작. 구매 유형(PDF/BOOK)과 받을 이메일(선택)을 넘긴다. */
export function confirmProject(
  id: number,
  req: {
    purchaseType: string
    deliveryEmail?: string
    recipientName?: string
    recipientPhone?: string
    postalCode?: string
    shippingAddress?: string
    shippingAddressDetail?: string
  },
): Promise<JobResponse> {
  return fetch(apiUrl(`/api/video-jobs/${id}/confirm`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
    ...withCreds,
  }).then((r) => handle<JobResponse>(r))
}

// ---- 로그인 ----
export interface Me {
  authenticated: boolean
  loginEnabled?: boolean
  isAdmin?: boolean
  provider?: string
  name?: string | null
  email?: string | null
}

export function getMe(): Promise<Me> {
  return fetch(apiUrl('/api/me'), withCreds).then((r) => handle<Me>(r))
}

export function logout(): Promise<void> {
  return fetch(apiUrl('/api/logout'), { method: 'POST', ...withCreds }).then(() => undefined)
}

export interface TestEmailResult {
  mailConfigured: boolean
  to?: string
  sent: boolean
  error?: string
}

/** 로그인한 본인 이메일로 테스트 메일 발송(발송 설정 확인용). */
export function testEmail(): Promise<TestEmailResult> {
  return fetch(apiUrl('/api/me/test-email'), { method: 'POST', ...withCreds }).then((r) =>
    handle<TestEmailResult>(r),
  )
}

// ---- 관리자 대시보드 ----
export interface AdminDailyRow {
  date: string
  previews: number
  purchases: number
  pdf: number
  hardcover: number
  completed: number
}
export interface AdminStats {
  days: number
  daily: AdminDailyRow[]
  totals: { previews: number; purchases: number; pdf: number; hardcover: number; completed: number }
  estImages: number
  estCostKrw: number
  costPerImageKrw: number
}
export interface AdminPurchase {
  id: number
  confirmedAt: string | null
  type: string
  pages: number | null
  priceKrw: number | null
  title: string | null
  deliveryEmail: string | null
  requesterEmail: string | null
  requesterProvider: string | null
  status: string | null
  recipientName: string | null
  recipientPhone: string | null
  postalCode: string | null
  address: string | null
}

// ---- 마이페이지(고객) ----
export interface MyBook {
  id: number
  title: string | null
  theme: string
  bookPages: number | null
  stage: string
  thumbnailUrl: string | null
  priceKrw: number | null
  purchaseType: string | null
  videoIncluded: boolean
  physicalBookRequested: boolean
  resultUrl: string | null
  narrationVideoUrl: string | null
  narrationVideoStatus: string | null
  emailSent: boolean
  createdAt: string
  confirmedAt: string | null
}
export interface MyQuota {
  limit: number
  usedToday: number
  /** -1이면 무제한 */
  remaining: number
}

export function getMyBooks(): Promise<MyBook[]> {
  return fetch(apiUrl('/api/me/books'), withCreds).then((r) => handle<MyBook[]>(r))
}
export function getMyQuota(): Promise<MyQuota> {
  return fetch(apiUrl('/api/me/quota'), withCreds).then((r) => handle<MyQuota>(r))
}
export async function hideMyBook(id: number): Promise<void> {
  const res = await fetch(apiUrl(`/api/me/books/${id}`), { method: 'DELETE', ...withCreds })
  if (!res.ok) throw new Error(`삭제 실패 (HTTP ${res.status})`)
}

/** 멈춘 작업을 현재 단계부터 재시도(관리자). */
export function retryAdminJob(id: number): Promise<{ id: number; resumedFrom: string; status: string }> {
  return fetch(apiUrl(`/api/admin/jobs/${id}/retry`), { method: 'POST', ...withCreds })
    .then((r) => handle<{ id: number; resumedFrom: string; status: string }>(r))
}

/** 연동 상태 진단(무엇이 켜져 있는가). 값은 중첩 맵이라 unknown으로 받는다. */
export function getAdminDiagnostics(): Promise<Record<string, Record<string, unknown>>> {
  return fetch(apiUrl('/api/admin/diagnostics'), withCreds)
    .then((r) => handle<Record<string, Record<string, unknown>>>(r))
}

export function getAdminStats(days = 30): Promise<AdminStats> {
  return fetch(apiUrl(`/api/admin/stats?days=${days}`), withCreds).then((r) => handle<AdminStats>(r))
}
export function getAdminPurchases(): Promise<AdminPurchase[]> {
  return fetch(apiUrl('/api/admin/purchases'), withCreds).then((r) => handle<AdminPurchase[]>(r))
}

export interface AdminUser {
  email: string
  provider: string | null
  previews: number
  purchases: number
  pdf: number
  hardcover: number
  estCostKrw: number
  lastAt: string | null
}
export interface AdminJob {
  id: number
  createdAt: string | null
  requesterEmail: string | null
  requesterProvider: string | null
  stage: string
  theme: string | null
  style: string | null
  age: string | null
  pages: number | null
  characters: string | null
  title: string | null
  priceKrw: number | null
  deliveryEmail: string | null
  status: string | null
}
export function getAdminUsers(): Promise<AdminUser[]> {
  return fetch(apiUrl('/api/admin/users'), withCreds).then((r) => handle<AdminUser[]>(r))
}
export function getAdminJobs(): Promise<AdminJob[]> {
  return fetch(apiUrl('/api/admin/jobs'), withCreds).then((r) => handle<AdminJob[]>(r))
}

/** 소셜 로그인 시작 주소(브라우저 전체 이동용). */
export function loginUrl(provider: 'google' | 'kakao'): string {
  return apiUrl(`/oauth2/authorization/${provider}`)
}

// ---- 후기 ----
export interface Review {
  id: number
  authorName: string
  rating: number
  content: string
  photoUrl: string | null
  createdAt: string | null
}

export interface ReviewList {
  count: number
  average: number
  items: Review[]
}

export function getReviews(): Promise<ReviewList> {
  return fetch(apiUrl('/api/reviews'), withCreds).then((r) => handle<ReviewList>(r))
}

/** 후기 작성(로그인 필수). 사진은 선택. */
export function createReview(input: {
  rating: number
  content: string
  photo?: File | null
}): Promise<Review> {
  const form = new FormData()
  form.append('rating', String(input.rating))
  form.append('content', input.content)
  if (input.photo) form.append('photo', input.photo)
  return fetch(apiUrl('/api/reviews'), { method: 'POST', body: form, ...withCreds }).then((r) =>
    handle<Review>(r),
  )
}
