<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const has = (code: string) => auth.menuCodes.includes(code)
</script>

<template>
  <div class="admin-page">
    <template v-if="has('role')">
      <div class="card-grid">
        <el-card class="admin-card">
          <h4>👤 用户管理</h4>
          <p>管理系统用户账号，创建、编辑、禁用、重置密码</p>
          <el-button type="primary" @click="router.push('/admin/users')">管理用户</el-button>
        </el-card>
        <el-card class="admin-card">
          <h4>🔑 角色管理</h4>
          <p>管理角色及其菜单权限分配</p>
          <el-button type="primary" @click="router.push('/admin/roles')">管理角色</el-button>
        </el-card>
      </div>
    </template>

  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }

.admin-card { transition: all .2s; }
.admin-card:hover { box-shadow: var(--shadow-md); transform: translateY(-1px); }

.section-title {
  font-size: 11px; color: var(--color-text-muted); text-transform: uppercase;
  letter-spacing: 1px; margin: 16px 0 8px; font-weight: 600;
}
.card-row { display: flex; justify-content: space-between; align-items: center; }

h4 { margin: 0 0 4px; font-size: 14px; color: var(--color-text); }
p { margin: 0 0 12px; color: var(--color-text-secondary); font-size: 13px; }
</style>
