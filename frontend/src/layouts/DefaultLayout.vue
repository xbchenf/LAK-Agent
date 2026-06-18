<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

function logout() {
  auth.logout()
  router.push('/login')
}

function newChat() {
  chat.clearMessages()
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <span class="title">政法智能知识Agent平台</span>
      <div class="header-right">
        <el-button text @click="newChat">新建对话</el-button>
        <el-button text @click="router.push('/tickets')">工单查询</el-button>
        <el-button v-if="auth.roles.includes('ADMIN')" text @click="router.push('/admin')">管理</el-button>
        <span class="user">{{ auth.realName || auth.username }}</span>
        <el-button text type="danger" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout { height: 100vh; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #dcdfe6; }
.title { font-size: 18px; font-weight: bold; }
.header-right { display: flex; align-items: center; gap: 8px; }
.user { color: #909399; margin-right: 8px; }
</style>
