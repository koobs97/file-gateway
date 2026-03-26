import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fileApi } from '../api/files'
import type { FilePage } from '../types/file'

export const useFileStore = defineStore('file', () => {
  const page = ref<FilePage | null>(null)
  const loading = ref(false)

  async function fetchList(pageNum = 0) {
    loading.value = true
    try {
      const res = await fileApi.getList(pageNum)
      page.value = res.data.data
    } finally {
      loading.value = false
    }
  }

  function updateStatus(fileId: number, status: string) {
    if (!page.value) return
    const item = page.value.content.find((f) => f.id === fileId)
    if (item) item.status = status
  }

  return { page, loading, fetchList, updateStatus }
})
