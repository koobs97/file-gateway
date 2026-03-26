import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.error?.message ?? '요청 처리 중 오류가 발생했습니다.'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default client
