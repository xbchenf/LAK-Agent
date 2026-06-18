<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

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
    <el-card header="敏感词管理">
      <p>当前词库数量: {{ wordCount || '--' }}</p>
      <el-button type="primary" :loading="reloading" @click="reloadWords">热加载敏感词库</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.admin-page { max-width: 600px; margin: 0 auto; }
</style>
