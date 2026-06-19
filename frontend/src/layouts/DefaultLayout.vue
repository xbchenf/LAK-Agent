<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const chat = useChatStore()

const activeMenu = computed(() => route.path)

function logout() { auth.logout(); router.push('/login') }
function newChat() { chat.clearMessages() }
</script>

<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo-icon">⚖</div>
        <span class="logo-text">公安智能助手</span>
      </div>
      <nav class="sidebar-nav">
        <a class="nav-item" :class="{ active: activeMenu === '/' }" @click="newChat(); router.push('/')">
          <span class="nav-icon">💬</span> 智能问答
        </a>
        <a class="nav-item" :class="{ active: activeMenu === '/tickets' }" @click="router.push('/tickets')">
          <span class="nav-icon">📋</span> 工单查询
        </a>
        <a v-if="auth.roles.includes('ADMIN')" class="nav-item"
           :class="{ active: activeMenu === '/admin' }" @click="router.push('/admin')">
          <span class="nav-icon">⚙</span> 系统管理
        </a>
      </nav>
      <div class="sidebar-footer">
        <span class="user-name">{{ auth.realName || auth.username }}</span>
        <a class="logout-link" @click="logout">退出登录</a>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-layout { display: flex; height: 100vh; }

/* === 侧边栏 === */
.sidebar {
  width: var(--sidebar-width);
  background: linear-gradient(180deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
  color: #fff;
  display: flex; flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 20px 16px 24px;
  display: flex; align-items: center; gap: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-icon { font-size: 28px; }
.logo-text { font-size: 15px; font-weight: 600; letter-spacing: 1px; }

.sidebar-nav { flex: 1; padding: 12px 0; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 20px; margin: 2px 8px;
  border-radius: 6px; font-size: 14px;
  color: rgba(255,255,255,0.75);
  cursor: pointer; text-decoration: none;
  transition: all 0.2s;
}
.nav-item:hover { background: rgba(255,255,255,0.1); color: #fff; }
.nav-item.active { background: rgba(255,255,255,0.18); color: var(--color-accent); font-weight: 500; }
.nav-icon { font-size: 18px; }

.sidebar-footer {
  padding: 16px 20px; border-top: 1px solid rgba(255,255,255,0.1);
  font-size: 13px; color: rgba(255,255,255,0.6);
}
.user-name { display: block; color: rgba(255,255,255,0.9); margin-bottom: 4px; }
.logout-link { color: rgba(255,255,255,0.5); cursor: pointer; font-size: 12px; }
.logout-link:hover { color: var(--color-accent); }

/* === 主内容 === */
.main-content { flex: 1; overflow: hidden; background: var(--color-bg); }
</style>
