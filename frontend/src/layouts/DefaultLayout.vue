<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

onMounted(() => loadSessions())

async function loadSessions() {
  try { const data = await request.get('/chat/sessions?page=1&size=50') as any; sessions.value = data.records || [] }
  catch { /* ignore */ }
}

async function switchSession(sid: string) {
  chat.currentSessionId = sid
  chat.clearMessages()
  // 加载历史消息
  try {
    const data = await request.get(`/chat/sessions/${sid}`) as any
    const msgs = data.messages || []
    // 去重：连续重复的用户消息只保留一条
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
  
  const resp = await fetch(`/api/v1/chat/sessions/${sid}`, {
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
</script>

<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <span class="logo-icon">⚖</span>
        <span class="logo-text">公安智能助手</span>
      </div>

      <nav class="sidebar-nav">
        <a class="nav-item new-chat" @click="newChat()">＋ 新建对话</a>
        <a class="nav-item" :class="{ active: route.path === '/' }" @click="router.push('/')">💬 智能问答</a>
        <a class="nav-item" :class="{ active: route.path === '/tickets' }" @click="router.push('/tickets')">📋 投诉建议</a>
        <a v-if="auth.roles?.includes('ADMIN')" class="nav-item"
           :class="{ active: route.path === '/admin' }" @click="router.push('/admin')">⚙ 系统管理</a>
      </nav>

      <div class="session-list" v-if="sessions.length">
        <div class="session-title">历史会话</div>
        <div v-for="s in sessions" :key="s.sessionId" class="session-item"
             :class="{ active: s.sessionId === chat.currentSessionId }"
             @click="switchSession(s.sessionId); router.push('/')">
          <span class="s-icon">{{ intentLabel[s.intentType] || '💬' }}</span>
          <span class="s-text">{{ s.createTime?.substring(5,16) || '' }}</span>
          <span class="s-del" title="删除会话" @click.stop="deleteSession(s.sessionId)">×</span>
        </div>
      </div>

      <div class="sidebar-footer">
        <span class="user-name">{{ auth.realName || auth.username }}</span>
        <a class="logout-link" @click="logout">退出登录</a>
      </div>
    </aside>

    <main class="main-content">
      <router-view @sessionCreated="loadSessions" />
    </main>
  </div>
</template>

<style scoped>
.app-layout { display: flex; height: 100vh; }

.sidebar {
  width: var(--sidebar-width); flex-shrink: 0;
  background: linear-gradient(180deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
  color: #fff; display: flex; flex-direction: column;
}
.sidebar-header {
  padding: 20px 16px 16px; display: flex; align-items: center; gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-icon { font-size: 28px; }
.logo-text { font-size: 15px; font-weight: 600; letter-spacing: 1px; }

.sidebar-nav { padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,0.08); }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 20px; margin: 2px 8px; border-radius: 6px; font-size: 14px;
  color: rgba(255,255,255,0.75); cursor: pointer; transition: all 0.2s;
}
.nav-item:hover { background: rgba(255,255,255,0.1); color: #fff; }
.nav-item.active { background: rgba(255,255,255,0.18); color: var(--color-accent); font-weight: 500; }

.session-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.session-title { font-size: 11px; color: rgba(255,255,255,0.4); padding: 8px 20px 4px; text-transform: uppercase; letter-spacing: 1px; }
.session-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 20px;
  font-size: 13px; color: rgba(255,255,255,0.6); cursor: pointer; transition: all 0.15s;
}
.session-item:hover { background: rgba(255,255,255,0.08); color: rgba(255,255,255,0.9); }
.session-item.active { background: rgba(255,255,255,0.12); color: #fff; }
.s-icon { font-size: 14px; flex-shrink: 0; }
.s-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; }
.s-del {
  opacity: 0.4; font-size: 16px; color: rgba(255,255,255,0.3);
  padding: 0 4px; cursor: pointer; transition: opacity 0.15s; flex-shrink: 0;
}
.s-del:hover { opacity: 1; color: var(--color-danger); }
.new-chat { color: var(--color-accent) !important; font-weight: 500; }

.sidebar-footer {
  padding: 14px 20px; border-top: 1px solid rgba(255,255,255,0.1);
  font-size: 13px; color: rgba(255,255,255,0.6);
}
.user-name { display: block; color: rgba(255,255,255,0.9); margin-bottom: 4px; }
.logout-link { color: rgba(255,255,255,0.5); cursor: pointer; font-size: 12px; }
.logout-link:hover { color: var(--color-accent); }

.main-content { flex: 1; overflow: hidden; background: var(--color-bg); }
</style>
