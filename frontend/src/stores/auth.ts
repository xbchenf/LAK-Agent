import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/types'
import { refreshToken as refreshTokenApi } from '@/api/auth'
import { getMyMenus } from '@/api/admin'

const ACCESS_KEY = 'lak_access_token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(sessionStorage.getItem(ACCESS_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userId = ref<number | null>(null)
  const username = ref<string | null>(null)
  const realName = ref<string | null>(null)
  const roles = ref<string[]>([])
  const menuCodes = ref<string[]>([])

  function applyLogin(vo: LoginVO) {
    accessToken.value = vo.accessToken
    refreshToken.value = vo.refreshToken
    userId.value = vo.userId
    username.value = vo.username
    realName.value = vo.realName
    roles.value = vo.roles
    sessionStorage.setItem(ACCESS_KEY, vo.accessToken)
    localStorage.setItem('refreshToken', vo.refreshToken)
  }

  function setAuth(vo: LoginVO) {
    applyLogin(vo)
  }

  function setMenuCodes(codes: string[]) {
    menuCodes.value = codes
  }

  /** 页面刷新后从服务端恢复用户状态（通过 refresh token），不信任本地存储 */
  async function restore() {
    const rt = refreshToken.value
    if (!rt || userId.value != null) return  // 无需恢复或已恢复
    try {
      const vo = await refreshTokenApi(rt)
      applyLogin(vo)
      // 同时恢复菜单权限
      try { menuCodes.value = await getMyMenus() } catch { /* keep empty */ }
    } catch {
      logout()
    }
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    userId.value = null
    username.value = null
    realName.value = null
    roles.value = []
    menuCodes.value = []
    sessionStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('lak_refresh_token')
  }

  return { accessToken, refreshToken, userId, username, realName, roles, menuCodes,
    setAuth, setMenuCodes, restore, logout }
})
