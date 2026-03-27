import client from './client'
import type { ApiResponse, FileDetailResponse, FileEvent, FilePage, FileStatistics, FileUploadResponse } from '../types/file'

export const fileApi = {
  upload(file: File, onProgress?: (percent: number) => void) {
    const form = new FormData()
    form.append('file', file)
    return client.post<ApiResponse<FileUploadResponse>>('/files', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total))
      },
    })
  },

  getList(page = 0, size = 20, keyword?: string, status?: string) {
    return client.get<ApiResponse<FilePage>>('/files', {
      params: { page, size, keyword: keyword || undefined, status: status || undefined },
    })
  },

  getDetail(id: number) {
    return client.get<ApiResponse<FileDetailResponse>>(`/files/${id}`)
  },

  getEvents(id: number) {
    return client.get<ApiResponse<FileEvent[]>>(`/files/${id}/events`)
  },

  delete(id: number) {
    return client.delete<ApiResponse<void>>(`/files/${id}`)
  },

  downloadFile(id: number) {
    return client.get<Blob>(`/files/${id}/download`, { responseType: 'blob' })
  },

  getStatistics() {
    return client.get<ApiResponse<FileStatistics>>('/admin/statistics')
  },
}
