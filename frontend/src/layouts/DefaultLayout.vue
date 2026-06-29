<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const chat = useChatStore()

interface SessionBrief { sessionId: string; status: string; intentType: string; createTime: string }
const sessions = ref<SessionBrief[]>([])
const expandedAdmin = ref(route.path.startsWith('/admin') || route.path.startsWith('/knowledge'))

onMounted(async () => {
  await auth.restore()
  if (!auth.userId) { router.push('/login'); return }
  loadSessions()
})

watch(() => auth.accessToken, (val) => {
  if (val) { loadSessions() }
  else { sessions.value = []; chat.clearMessages() }
})

async function loadSessions() {
  try { const data = await request.get('/chat/sessions?page=1&size=50') as any; sessions.value = data.records || [] }
  catch { /* ignore */ }
}

async function switchSession(sid: string) {
  chat.currentSessionId = sid
  chat.clearMessages()
  try {
    const data = await request.get(`/chat/sessions/${sid}`) as any
    const msgs = data.messages || []
    const deduped: any[] = []
    for (let i = 0; i < msgs.length; i++) {
      if (i > 0 && msgs[i].role === msgs[i-1].role && msgs[i].content === msgs[i-1].content) continue
      deduped.push(msgs[i])
    }
    deduped.forEach((m: any) => chat.addMessage({ role: m.role, content: m.content }))
  } catch { /* ignore */ }
}

function newChat() {
  chat.clearMessages()
  router.push('/').then(() => loadSessions())
}

async function deleteSession(sid: string) {
  await fetch(`/api/v1/chat/sessions/${sid}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${auth.accessToken}` }
  })
  if (chat.currentSessionId === sid) { chat.clearMessages() }
  loadSessions()
}

function logout() { auth.logout(); router.push('/login') }

const intentLabel: Record<string, string> = {
  POLICY_CONSULT: '📋', PROCEDURE_GUIDE: '📝', COMPLAINT_SUGGEST: '📮', FALLBACK: '❓', CHITCHAT: '💬'
}

// Breadcrumb mapping
const breadcrumbMap: Record<string, string> = {
  '/': '智能问答',
  '/tickets': '工单管理',
  '/knowledge': '知识库',
  '/admin': '用户权限',
  '/admin/users': '用户管理',
  '/admin/roles': '角色权限',
  '/admin/audit': '审计日志',
  '/operator': '坐席工作台',
}

const currentPageTitle = computed(() => {
  const path = route.path
  if (path.startsWith('/knowledge/')) return '文档详情'
  return breadcrumbMap[path] || ''
})

const now = ref(formatNow())
let clockTimer: number | null = null
onMounted(() => { clockTimer = window.setInterval(() => { now.value = formatNow() }, 10000) })
onUnmounted(() => { if (clockTimer) clearInterval(clockTimer) })

function formatNow() {
  const d = new Date()
  const week = ['日','一','二','三','四','五','六']
  return `${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 星期${week[d.getDay()]} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<template>
  <div class="app-layout">
    <!-- ===== Sidebar ===== -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <span class="logo-icon">法</span>
        <span class="logo-text">
          <span class="logo-title">LAK-Agent</span>
          <span class="logo-sub">政法智能知识平台</span>
        </span>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section">
          <a v-if="auth.menuCodes.includes('chat')"
             class="nav-item" :class="{ active: route.path === '/' }"
             @click="router.push('/')">
            <span class="nav-dot"></span>💬 智能问答
          </a>
          <a v-if="auth.menuCodes.includes('operator')"
             class="nav-item" :class="{ active: route.path.startsWith('/operator') }"
             @click="router.push('/operator')">
            <span class="nav-dot"></span>👤 坐席工作台
          </a>
          <a v-if="auth.menuCodes.includes('ticket')"
             class="nav-item" :class="{ active: route.path === '/tickets' }"
             @click="router.push('/tickets')">
            <span class="nav-dot"></span>📋 投诉建议
          </a>
          <!-- Admin accordion -->
          <div v-if="auth.menuCodes.includes('admin')" class="nav-group">
            <div class="nav-item nav-parent"
                 :class="{ active: route.path.startsWith('/admin') || route.path.startsWith('/knowledge') }"
                 @click="expandedAdmin = !expandedAdmin">
              <span class="nav-dot"></span>⚙ 系统管理
              <span class="nav-arrow" :class="{ open: expandedAdmin }">▾</span>
            </div>
            <div v-show="expandedAdmin" class="nav-children">
              <a v-if="auth.menuCodes.includes('knowledge')" class="nav-item nav-child"
                 :class="{ active: route.path.startsWith('/knowledge') }"
                 @click="router.push('/knowledge')">
                <span class="nav-dot"></span>📚 知识库
              </a>
              <a v-if="auth.menuCodes.includes('role')" class="nav-item nav-child"
                 :class="{ active: route.path === '/admin' || route.path.startsWith('/admin/users') }"
                 @click="router.push('/admin')">
                <span class="nav-dot"></span>👥 用户权限
              </a>
              <a v-if="auth.menuCodes.includes('sensitive')" class="nav-item nav-child"
                 :class="{ active: route.path === '/admin/sensitive' }"
                 @click="router.push('/admin/sensitive')">
                <span class="nav-dot"></span>🛡 敏感词管理
              </a>
              <a v-if="auth.menuCodes.includes('audit')" class="nav-item nav-child"
                 :class="{ active: route.path === '/admin/audit' }"
                 @click="router.push('/admin/audit')">
                <span class="nav-dot"></span>📝 审计日志
              </a>
            </div>
          </div>
        </div>
      </nav>

      <!-- Session list -->
      <div class="session-list">
        <template v-if="sessions.length">
          <div class="session-title">历史会话</div>
          <div v-for="s in sessions" :key="s.sessionId" class="session-item"
               :class="{ active: s.sessionId === chat.currentSessionId }"
               @click="switchSession(s.sessionId); router.push('/')">
            <span class="s-icon">{{ intentLabel[s.intentType] || '💬' }}</span>
            <span class="s-text">{{ s.createTime?.substring(5,16) || '' }}</span>
            <span class="s-del" title="删除会话" @click.stop="deleteSession(s.sessionId)">×</span>
          </div>
        </template>
      </div>

      <!-- Sidebar footer -->
      <div class="sidebar-footer">
        <div class="sf-avatar">{{ (auth.realName || auth.username || 'U').charAt(0) }}</div>
        <div class="sf-info">
          <span class="sf-name">{{ auth.realName || auth.username }}</span>
          <span class="sf-role">{{ auth.roles?.includes('ADMIN') ? '系统管理员' : auth.roles?.includes('OPERATOR') ? '坐席专员' : '用户' }}</span>
        </div>
        <span class="sf-logout" title="退出登录" @click="logout">⏻</span>
      </div>
    </aside>

    <!-- ===== Main Container ===== -->
    <div class="main-container">
      <!-- Topbar -->
      <header class="topbar">
        <div class="topbar-left">
          <span class="breadcrumb-current">{{ currentPageTitle }}</span>
        </div>
        <div class="topbar-center">
          <span class="topbar-time">{{ now }}</span>
        </div>
        <div class="topbar-right">
          <span class="topbar-user">{{ auth.realName || auth.username }}</span>
          <button class="topbar-icon-btn" title="退出" @click="logout">⏻</button>
        </div>
      </header>

      <!-- Content -->
      <main class="main-content">
        <router-view @sessionCreated="loadSessions" />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ===== Layout ===== */
.app-layout { display: flex; height: 100vh; }

.main-container {
  flex: 1; display: flex; flex-direction: column;
  overflow: hidden; min-width: 0;
}

/* ===== Sidebar ===== */
.sidebar {
  width: var(--sidebar-width); flex-shrink: 0;
  background: linear-gradient(180deg, var(--sidebar-bg-start) 0%, var(--sidebar-bg-end) 100%);
  color: #fff; display: flex; flex-direction: column; z-index: 10;
}

.sidebar-logo {
  padding: 24px 20px; border-bottom: 1px solid rgba(255,255,255,.08);
  display: flex; align-items: center; gap: 12px;
}
.logo-icon {
  width: 38px; height: 38px; border-radius: 10px;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary));
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; color: #fff; flex-shrink: 0;
}
.logo-text { display: flex; flex-direction: column; }
.logo-title { font-size: 15px; font-weight: 600; letter-spacing: .5px; color: #fff; }
.logo-sub { font-size: 11px; color: rgba(255,255,255,.45); margin-top: 1px; }

/* ---- Nav ---- */
.sidebar-nav { padding: 12px 0; }
.nav-section { padding: 0 16px; margin-bottom: 8px; }
.nav-section-label {
  font-size: 11px; color: rgba(255,255,255,.3);
  text-transform: uppercase; letter-spacing: 1.5px;
  padding: 12px 4px 6px;
}
.nav-parent { position: relative; cursor: pointer; }
.nav-arrow { margin-left: auto; font-size: 10px; transition: transform .2s; opacity: .4; }
.nav-arrow.open { transform: rotate(-180deg); }
.nav-children { padding-left: 12px; }
.nav-child { font-size: 13px; padding: 8px 14px; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: var(--radius);
  color: rgba(255,255,255,.65); font-size: 14px;
  cursor: pointer; transition: all .18s; margin-bottom: 2px;
}
.nav-item:hover { background: rgba(255,255,255,.06); color: rgba(255,255,255,.9); }
.nav-item.active { background: rgba(255,255,255,.12); color: #fff; font-weight: 500; }
.nav-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: currentColor; opacity: .4; flex-shrink: 0;
}
.nav-item.active .nav-dot { opacity: 1; background: var(--color-primary-light); }

/* ---- Session list ---- */
.session-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.session-title {
  font-size: 11px; color: rgba(255,255,255,.3);
  padding: 8px 24px 4px; text-transform: uppercase; letter-spacing: 1.5px;
}
.session-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 20px;
  font-size: 13px; color: rgba(255,255,255,.6); cursor: pointer; transition: all .15s;
}
.session-item:hover { background: rgba(255,255,255,.08); color: rgba(255,255,255,.9); }
.session-item.active { background: rgba(255,255,255,.12); color: #fff; }
.s-icon { font-size: 14px; flex-shrink: 0; }
.s-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; }
.s-del {
  opacity: 0.4; font-size: 16px; color: rgba(255,255,255,.3);
  padding: 0 4px; cursor: pointer; transition: opacity .15s; flex-shrink: 0;
}
.s-del:hover { opacity: 1; color: #ef4444; }

/* ---- Sidebar footer ---- */
.sidebar-footer {
  padding: 14px 16px; border-top: 1px solid rgba(255,255,255,.08);
  display: flex; align-items: center; gap: 10px;
}
.sf-avatar {
  width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-primary), #6366f1);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 14px; font-weight: 600;
}
.sf-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.sf-name { font-size: 13px; color: #fff; }
.sf-role { font-size: 11px; color: rgba(255,255,255,.4); }
.sf-logout {
  font-size: 16px; color: rgba(255,255,255,.35); cursor: pointer;
  padding: 4px; transition: color .15s; flex-shrink: 0;
}
.sf-logout:hover { color: var(--color-accent); }

/* ===== Topbar ===== */
.topbar {
  height: var(--topbar-height); flex-shrink: 0;
  background: var(--color-bg-white);
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center; padding: 0 24px; gap: 16px;
}
.topbar-left, .topbar-right { display: flex; align-items: center; gap: 12px; }
.topbar-center { flex: 1; text-align: center; }
.topbar-left .breadcrumb-current { color: var(--color-text); font-weight: 500; font-size: 14px; }
.topbar-time { color: var(--color-text-secondary); font-size: 13px; }
.topbar-user { color: var(--color-text-secondary); font-size: 13px; }
.topbar-icon-btn {
  width: 34px; height: 34px; border-radius: 50%;
  background: var(--color-bg-hover); border: none;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; font-size: 15px; color: var(--color-text-secondary);
  transition: all .15s;
}
.topbar-icon-btn:hover { background: var(--color-border); color: var(--color-text); }

/* ===== Main content ===== */
.main-content { flex: 1; overflow-y: auto; background: var(--color-bg); }

@media (max-width: 768px) {
  .sidebar { width: 60px; }
  .sidebar .logo-sub, .sidebar .logo-title, .sidebar .nav-item, .sidebar .sf-info,
  .sidebar .session-list, .sidebar .nav-section-label { display: none; }
  .sidebar-logo { justify-content: center; padding: 16px 10px; }
  .sidebar-footer { justify-content: center; }
}
</style>
