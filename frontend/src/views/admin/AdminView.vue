<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/request'

const router = useRouter()
const auth = useAuthStore()
const has = (code: string) => auth.menuCodes.includes(code)

const reloading = ref(false)
const wordCount = ref(0)

async function reloadWords() {
  reloading.value = true
  try {
    const data = await request.post('/admin/sensitive-words/reload') as any
    wordCount.value = data.count || 0
    ElMessage.success('敏感词库热加载完成')
  } catch {
    ElMessage.error('加载失败')
  } finally {
    reloading.value = false
  }
}
</script>

<template>
  <div class="admin-page">
    <h3>系统管理</h3>

    <template v-if="has('knowledge')">
      <div class="section-title">内容管理</div>
      <el-card style="margin-bottom:16px">
        <div class="card-row">
          <div>
            <h4>📚 知识库管理</h4>
            <p>管理政策法规和办事指引文档，上传、发布、下架、检索</p>
          </div>
          <el-button type="primary" @click="router.push('/knowledge')">进入知识库</el-button>
        </div>
      </el-card>
    </template>

    <template v-if="has('sensitive')">
      <div class="section-title">安全与合规</div>
      <el-card style="margin-bottom:16px">
        <div class="card-row">
          <div>
            <h4>🛡 敏感词管理</h4>
            <p>当前词库: {{ wordCount || '--' }} 条 | 支持热加载，无需重启</p>
          </div>
          <el-button type="primary" :loading="reloading" @click="reloadWords">热加载词库</el-button>
        </div>
      </el-card>
    </template>

    <template v-if="has('role')">
      <div class="section-title">用户与权限</div>
      <el-card style="margin-bottom:16px">
        <el-row :gutter="24">
          <el-col :span="12">
            <div class="placeholder-item">
              <h4>👤 用户管理</h4>
              <p>管理系统用户账号</p>
              <el-button type="primary" @click="router.push('/admin/users')">管理用户</el-button>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="placeholder-item">
              <h4>🔑 角色管理</h4>
              <p>管理角色及其权限分配</p>
              <el-button type="primary" @click="router.push('/admin/roles')">管理角色</el-button>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <template v-if="has('audit')">
      <div class="section-title">审计与监控</div>
      <el-card style="margin-bottom:16px">
        <div class="card-row">
          <div>
            <h4>📋 操作审计</h4>
            <p>全量操作日志，按月分表存储，留存六个月</p>
          </div>
          <el-button type="primary" @click="router.push('/admin/audit')">查看审计日志</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.admin-page { max-width: 720px; margin: 0 auto; padding: 24px; }

h3 { margin: 0 0 20px; font-size: 20px; }

.section-title {
  font-size: 12px; color: #909399; text-transform: uppercase;
  letter-spacing: 1px; margin-bottom: 8px; margin-top: 4px;
}

.card-row { display: flex; justify-content: space-between; align-items: center; }

.placeholder-item { text-align: center; }

h4 { margin: 0 0 6px; font-size: 15px; }
p { margin: 0 0 12px; color: #606266; font-size: 13px; }
</style>
