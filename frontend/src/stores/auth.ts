import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userId = ref<number | null>(null)
  const username = ref<string | null>(null)
  const realName = ref<string | null>(null)
  const roles = ref<string[]>([])

  function setAuth(vo: LoginVO) {
    accessToken.value = vo.accessToken
    refreshToken.value = vo.refreshToken
    userId.value = vo.userId
    username.value = vo.username
    realName.value = vo.realName
    roles.value = vo.roles
    localStorage.setItem('refreshToken', vo.refreshToken)
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    userId.value = null
    username.value = null
    realName.value = null
    roles.value = []
    localStorage.removeItem('refreshToken')
  }

  return { accessToken, refreshToken, userId, username, realName, roles, setAuth, logout }
})
