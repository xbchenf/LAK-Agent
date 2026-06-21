<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, getCaptcha } from '@/api/auth'
import { getMyMenus } from '@/api/admin'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaText = ref('')
const captchaKey = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度 2-32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function fetchCaptcha() {
  try {
    const data = await getCaptcha()
    captchaKey.value = data.captchaKey
    captchaText.value = data.captchaText
  } catch { /* ignore */ }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const result = await login({ ...form, captchaKey: captchaKey.value })
    auth.setAuth(result)
    // 获取用户可访问的菜单
    try {
      const codes = await getMyMenus()
      auth.setMenuCodes(codes)
      // 按优先级跳转第一个可用菜单
      if (codes.includes('chat')) router.push('/')
      else if (codes.includes('ticket')) router.push('/tickets')
      else if (codes.includes('admin')) router.push('/admin')
      else router.push('/')
    } catch { auth.setMenuCodes([]); router.push('/') }
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

fetchCaptcha()
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>LAK-Agent 登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="验证码" />
            <el-button class="captcha-btn" @click="fetchCaptcha">{{ captchaText || '点击获取' }}</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="submit-btn" @click="submit">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex; align-items: center; justify-content: center; height: 100vh;
  background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 50%, #2c5f8a 100%);
  position: relative;
}
.login-page::before {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(circle at 20% 80%, rgba(201,168,76,0.1) 0%, transparent 50%),
              radial-gradient(circle at 80% 20%, rgba(255,255,255,0.05) 0%, transparent 50%);
}
.login-card {
  width: 400px; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  position: relative; z-index: 1;
}
.login-card :deep(.el-card__body) { padding: 40px 36px; }
.login-card h2 {
  text-align: center; margin-bottom: 32px; font-size: 22px;
  color: var(--color-primary); letter-spacing: 2px;
}
.login-card h2::after {
  content: ''; display: block; width: 40px; height: 3px;
  background: var(--color-accent); margin: 12px auto 0; border-radius: 2px;
}
.captcha-row { display: flex; gap: 8px; width: 100%; }
.captcha-btn {
  min-width: 110px; letter-spacing: 4px; font-weight: bold;
  background: var(--color-primary); color: #fff; border: none;
}
.captcha-btn:hover { background: var(--color-primary-light); }
.submit-btn { width: 100%; height: 44px; font-size: 16px; letter-spacing: 4px; }
</style>
