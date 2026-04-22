import { defineStore } from 'pinia'

const saved = JSON.parse(localStorage.getItem('auth') || 'null')

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: saved?.token || '',
    user: saved?.user || null
  }),
  actions: {
    setAuth(payload) {
      this.token = payload.token
      this.user = { userId: payload.userId, username: payload.username, role: payload.role }
      localStorage.setItem('auth', JSON.stringify({ token: this.token, user: this.user }))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('auth')
    }
  }
})
