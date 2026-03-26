export interface FileUploadResponse {
  fileId: number
  originalName: string
  fileSize: number
  status: string
  createdAt: string
}

export interface FileDetailResponse {
  id: number
  originalName: string
  storedName: string
  fileSize: number
  mimeType: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface FilePage {
  content: FileDetailResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface FileEvent {
  id: number
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface FileStatistics {
  total: number
  done: number
  fail: number
  processing: number
  deleted: number
  successRate: number
}

export interface FileStatusNotification {
  fileId: number
  status: string
  timestamp: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: { code: string; message: string } | null
}
