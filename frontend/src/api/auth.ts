import request from './request'
import type { LoginVO } from '@/types'

export interface LoginParams {
  username: string
  password: string
  captchaKey: string
  captchaCode: string
}

export function login(params: LoginParams): Promise<LoginVO> {
  return request.post('/auth/login', params) as any
}

export function getCaptcha(): Promise<{ captchaKey: string; captchaText: string }> {
  return request.get('/auth/captcha') as any
}

export function refreshToken(refreshToken: string): Promise<LoginVO> {
  return request.post('/auth/refresh', { refreshToken }) as any
}
