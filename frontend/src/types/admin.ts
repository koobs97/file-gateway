export interface UserSummaryResponse {
  id: number
  username: string
  role: string
  active: boolean
  createdAt: string
}

export interface ApiClientResponse {
  id: number
  clientName: string
  apiKey: string
  status: string
  createdAt: string
}

export interface ProcessLogResponse {
  id: number
  fileId: number
  step: string
  status: string
  message: string
  createdAt: string
}

export interface AdminPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
