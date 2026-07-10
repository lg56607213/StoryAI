// StoryAI 백엔드 API 클라이언트. dev 서버는 vite.config.ts의 프록시로 /api → :8080 로 전달한다.

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
  mood?: string
  targetAgeGroup?: string
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

export function getOptions(): Promise<Options> {
  return fetch('/api/options').then((r) => handle<Options>(r))
}

/** 사진 파일들을 서버에 업로드하고 저장된 URL 목록을 반환. */
export function uploadPhotos(files: File[]): Promise<string[]> {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  return fetch('/api/uploads', { method: 'POST', body: form })
    .then((r) => handle<{ urls: string[] }>(r))
    .then((d) => d.urls)
}

export function createProject(req: CreateRequest): Promise<JobResponse> {
  return fetch('/api/video-jobs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  }).then((r) => handle<JobResponse>(r))
}

export function getProject(id: number): Promise<JobResponse> {
  return fetch(`/api/video-jobs/${id}`).then((r) => handle<JobResponse>(r))
}
