<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, getCaptcha } from '@/api/auth'

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
    router.push('/')
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
.login-page { display: flex; align-items: center; justify-content: center; height: 100vh; background: #f5f7fa; }
.login-card { width: 400px; }
.login-card h2 { text-align: center; margin-bottom: 24px; }
.captcha-row { display: flex; gap: 8px; width: 100%; }
.captcha-btn { min-width: 110px; letter-spacing: 4px; font-weight: bold; }
.submit-btn { width: 100%; }
</style>
