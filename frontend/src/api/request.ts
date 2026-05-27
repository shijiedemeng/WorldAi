import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/common'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  },
)

function unwrapResponse<T>(response: AxiosResponse<ApiResponse<T>>) {
  const payload = response.data
  if (payload.code !== 0) {
    ElMessage.error(payload.message || '请求失败')
    return Promise.reject(new Error(payload.message || 'Request failed'))
  }
  return payload.data
}

const request = {
  get<T = never, R = T>(url: string, config?: AxiosRequestConfig) {
    return client.get<T, AxiosResponse<ApiResponse<R>>>(url, config).then(unwrapResponse<R>)
  },
  post<T = never, R = T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return client.post<T, AxiosResponse<ApiResponse<R>>>(url, data, config).then(unwrapResponse<R>)
  },
  put<T = never, R = T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return client.put<T, AxiosResponse<ApiResponse<R>>>(url, data, config).then(unwrapResponse<R>)
  },
  delete<T = never, R = T>(url: string, config?: AxiosRequestConfig) {
    return client.delete<T, AxiosResponse<ApiResponse<R>>>(url, config).then(unwrapResponse<R>)
  },
}

export default request
