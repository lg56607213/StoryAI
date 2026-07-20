// StoryAI 백엔드 API 클라이언트.
// dev: vite.config.ts 프록시로 /api → :8080. prod: VITE_API_BASE(백엔드 도메인)를 앞에 붙인다.
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')
/** 상대 API 경로(예: "/api/...")를 배포 백엔드 절대 URL로 변환. dev에선 그대로 프록시. */
export const apiUrl = (path: string) => `${API_BASE}${path}`

export interface Option {
  code: string
  label: string
}

export interface Pricing {
  currency: string
  bookPdf: { pages: number; priceKrw: number }[]
  bookHardcopyKrw: number
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
  photoUrls: string[]
}

export interface CreateRequest {
  outputType: string
  theme: string
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
  videoStyle: string | null
  videoDurationSec: number | null
  characters: { name: string; role: string }[]
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  currentStep: string
  generatedTitle: string | null
  priceKrw: number | null
  resultUrl: string | null
  resultVideoUrl: string | null
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

/** 미리보기 확정 → 전체 생성 시작. 구매 유형(PDF/BOOK)과 받을 이메일(선택)을 넘긴다. */
export function confirmProject(
  id: number,
  req: { purchaseType: string; deliveryEmail?: string },
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

/** 소셜 로그인 시작 주소(브라우저 전체 이동용). */
export function loginUrl(provider: 'google' | 'kakao'): string {
  return apiUrl(`/oauth2/authorization/${provider}`)
}
