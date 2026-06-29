<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, getCaptcha, register } from '@/api/auth'
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
    try {
      const codes = await getMyMenus()
      auth.setMenuCodes(codes)
      if (codes.includes('operator')) router.push('/operator')
      else if (codes.includes('chat')) router.push('/')
      else if (codes.includes('ticket')) router.push('/tickets')
      else if (codes.includes('admin')) router.push('/admin')
      else router.push('/')
    } catch { auth.setMenuCodes([]); router.push('/') }
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

// 注册
const showRegister = ref(false)
const regLoading = ref(false)
const regCaptchaText = ref('')
const regCaptchaKey = ref('')
const regForm = reactive({ username: '', password: '', realName: '', captchaCode: '', captchaKey: '' })

async function fetchRegCaptcha() {
  try {
    const data = await getCaptcha()
    regCaptchaKey.value = data.captchaKey
    regCaptchaText.value = data.captchaText
    regForm.captchaKey = data.captchaKey
  } catch { /* ignore */ }
}

async function doRegister() {
  if (!regForm.username || regForm.username.length < 2) { ElMessage.warning('用户名至少2位'); return }
  if (!regForm.password || regForm.password.length < 6) { ElMessage.warning('密码至少6位'); return }
  if (!regForm.captchaCode) { ElMessage.warning('请输入验证码'); return }
  regLoading.value = true
  try {
    await register(regForm)
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
  } catch { /* handled by interceptor */ }
  finally { regLoading.value = false }
}

fetchCaptcha()
</script>

<template>
  <div class="login-page">
    <!-- Decorative background blobs -->
    <div class="bg-blob bg-blob-1"></div>
    <div class="bg-blob bg-blob-2"></div>
    <div class="bg-grid"></div>

    <div class="login-wrapper">
      <!-- Left: Brand panel -->
      <div class="brand-panel">
        <div class="brand-logo">
          <div class="brand-icon">法</div>
          <div>
            <div class="brand-name">LAK-Agent</div>
            <div class="brand-sub">政法智能知识平台</div>
          </div>
        </div>
        <div class="brand-tagline">用<b>AI</b>赋能<br>政法知识服务</div>
        <div class="brand-features">
          <div class="feature-item"><span class="feature-check">✓</span> 智能政策法规问答</div>
          <div class="feature-item"><span class="feature-check">✓</span> 办事指引一站式查询</div>
          <div class="feature-item"><span class="feature-check">✓</span> 投诉建议快速响应</div>
          <div class="feature-item"><span class="feature-check">✓</span> 私有化部署 · 数据安全可控</div>
        </div>
        <div class="brand-footer">© 2026 LAK-Agent · 政法行业私有化智能平台</div>
      </div>

      <!-- Right: Form panel -->
      <div class="form-panel">
        <div class="form-title">欢迎登录</div>
        <div class="form-subtitle">请输入您的账号信息以继续</div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="submit">
          <el-form-item prop="username">
            <label class="field-label">用户名</label>
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>

          <el-form-item prop="password">
            <label class="field-label">密码</label>
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>

          <el-form-item prop="captchaCode">
            <label class="field-label">验证码</label>
            <div class="captcha-row">
              <el-input v-model="form.captchaCode" placeholder="请输入验证码" />
              <div class="captcha-box" @click="fetchCaptcha" :title="captchaText || '点击刷新'">
                {{ captchaText || '----' }}
              </div>
            </div>
          </el-form-item>

          <div class="form-options">
            <label class="remember-label">
              <input type="checkbox" checked /> 记住我
            </label>
            <a class="forgot-link" href="#">忘记密码？</a>
          </div>

          <el-form-item>
            <button type="button" class="btn-login" :disabled="loading" @click="submit">
              {{ loading ? '登录中…' : '登 录' }}
            </button>
          </el-form-item>
        </el-form>

        <div class="register-hint">还没有账号？<a href="#" @click.prevent="showRegister = true">申请注册</a></div>
      </div>

      <!-- Register Dialog -->
      <el-dialog v-model="showRegister" title="申请注册" width="420px" :close-on-click-modal="false">
        <el-form label-width="80px" @keyup.enter="doRegister">
          <el-form-item label="用户名">
            <el-input v-model="regForm.username" placeholder="登录用户名（至少2位）" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="regForm.password" type="password" placeholder="至少6位" show-password />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="regForm.realName" placeholder="真实姓名（选填）" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="captcha-row">
              <el-input v-model="regForm.captchaCode" placeholder="请输入验证码" />
              <div class="captcha-box" @click="fetchRegCaptcha" :title="regCaptchaText || '点击刷新'">{{ regCaptchaText || '----' }}</div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showRegister = false">取消</el-button>
          <el-button type="primary" :loading="regLoading" @click="doRegister">提交注册</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
/* ===== Page ===== */
.login-page {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #f0f4f8 0%, #e8edf5 40%, #f0f4fa 100%);
}

/* Decorative blobs */
.bg-blob {
  position: fixed; border-radius: 50%; filter: blur(100px);
  opacity: .35; pointer-events: none; z-index: 0;
}
.bg-blob-1 { width: 600px; height: 600px; background: #c4d0ff; top: -180px; right: -120px; }
.bg-blob-2 { width: 500px; height: 500px; background: #e8d5f5; bottom: -200px; left: -100px; }

.bg-grid {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(0,0,0,.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,.02) 1px, transparent 1px);
  background-size: 80px 80px;
}

/* ===== Wrapper ===== */
.login-wrapper {
  position: relative; z-index: 1; display: flex;
  border-radius: 16px; overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,.08), 0 8px 20px rgba(0,0,0,.04);
}

/* ===== Brand panel ===== */
.brand-panel {
  width: 440px; padding: 60px 48px; display: flex; flex-direction: column; justify-content: center;
  background: linear-gradient(180deg, var(--sidebar-bg-start) 0%, #0d2b50 100%);
  color: #fff; overflow: hidden; transition: width .3s ease, padding .3s ease, opacity .3s ease;
}
.brand-logo { display: flex; align-items: center; gap: 14px; margin-bottom: 40px; }
.brand-icon {
  width: 48px; height: 48px; border-radius: 12px; flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; font-weight: 700; color: #fff;
}
.brand-name { font-size: 20px; font-weight: 700; letter-spacing: 1px; }
.brand-sub { font-size: 12px; color: rgba(255,255,255,.5); margin-top: 2px; }
.brand-tagline { font-size: 28px; font-weight: 300; line-height: 1.4; margin-bottom: 16px; }
.brand-tagline b { font-weight: 700; color: #fff; }
.brand-features { display: flex; flex-direction: column; gap: 14px; margin-top: 32px; }
.feature-item { display: flex; align-items: center; gap: 12px; font-size: 14px; color: rgba(255,255,255,.7); }
.feature-check {
  width: 22px; height: 22px; border-radius: 50%; flex-shrink: 0;
  background: rgba(37,99,235,.3); display: flex; align-items: center; justify-content: center; font-size: 12px;
}
.brand-footer { margin-top: auto; font-size: 11px; color: rgba(255,255,255,.3); padding-top: 40px; }

/* ===== Form panel ===== */
.form-panel {
  width: 460px; padding: 60px 56px; display: flex; flex-direction: column; justify-content: center;
  background: #fff;
}
.form-title { font-size: 22px; font-weight: 700; color: var(--color-text); margin-bottom: 8px; }
.form-subtitle { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 36px; }

.field-label {
  display: block; font-size: 13px; font-weight: 500;
  color: var(--color-text); margin-bottom: 6px;
}

/* Captcha */
.captcha-row { display: flex; gap: 12px; width: 100%; align-items: stretch; }
.captcha-row :deep(.el-input) { flex: 1; }
.captcha-box {
  width: 120px; height: 44px; border-radius: var(--radius); flex-shrink: 0;
  background: linear-gradient(135deg, #f0f4f8, #e8edf5);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; color: #1a3f6e;
  letter-spacing: 3px; cursor: pointer; user-select: none;
  border: 1px solid var(--color-border);
  font-family: Georgia, serif; font-style: italic;
}

/* Options row */
.form-options { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; }
.remember-label { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-secondary); cursor: pointer; }
.remember-label input { accent-color: var(--color-primary); }
.forgot-link { font-size: 13px; color: var(--color-primary); text-decoration: none; }
.forgot-link:hover { text-decoration: underline; }

/* Login button */
.btn-login {
  width: 100%; padding: 13px; border: none; border-radius: var(--radius);
  background: linear-gradient(135deg, var(--color-primary), #1d4ed8);
  color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
  transition: all .15s; letter-spacing: .5px; font-family: inherit;
}
.btn-login:hover:not(:disabled) {
  background: linear-gradient(135deg, #1d4ed8, #1e40af);
  box-shadow: 0 4px 16px rgba(37,99,235,.3); transform: translateY(-1px);
}
.btn-login:disabled { opacity: .7; cursor: not-allowed; }

.register-hint { text-align: center; margin-top: 24px; font-size: 13px; color: var(--color-text-secondary); }
.register-hint a { color: var(--color-primary); text-decoration: none; font-weight: 500; }

/* ===== Responsive ===== */
@media (max-width: 920px) {
  .brand-panel { width: 0; padding: 0; opacity: 0; }
  .form-panel { width: 400px; padding: 40px 36px; }
  .login-wrapper { border-radius: var(--radius-lg); }
}
</style>
