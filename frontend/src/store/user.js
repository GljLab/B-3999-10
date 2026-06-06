import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || ''
  }),
  actions: {
    setUserInfo(data) {
      this.token = data.token
      this.userId = data.userId
      this.role = data.role
      this.username = data.username
      this.realName = data.realName
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('role', data.role)
      localStorage.setItem('username', data.username)
      localStorage.setItem('realName', data.realName)
    },
    logout() {
      this.token = ''
      this.userId = ''
      this.role = ''
      this.username = ''
      this.realName = ''
      localStorage.clear()
    }
  }
})
