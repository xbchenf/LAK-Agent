import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/types'

const ACCESS_KEY = 'lak_access_token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(sessionStorage.getItem(ACCESS_KEY))
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
    sessionStorage.setItem(ACCESS_KEY, vo.accessToken)
    localStorage.setItem('refreshToken', vo.refreshToken)
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    userId.value = null
    username.value = null
    realName.value = null
    roles.value = []
    sessionStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('lak_refresh_token')
  }

  return { accessToken, refreshToken, userId, username, realName, roles, setAuth, logout }
})
