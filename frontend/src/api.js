import axios from 'axios'
import { useAuthStore } from './stores/auth'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error.response?.data || error)
)
