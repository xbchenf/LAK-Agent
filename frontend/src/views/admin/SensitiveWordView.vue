<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const wordCount = ref(0)
const words = ref<string[]>([])

onMounted(() => loadList())

async function loadList() {
  try {
    const data = await request.get('/admin/sensitive-words/list') as any
    words.value = data || []
    wordCount.value = words.value.length
  } catch { /* ignore */ }
}

async function doReload() {
  loading.value = true
  try {
    const data = await request.post('/admin/sensitive-words/reload') as any
    wordCount.value = data.count || 0
    ElMessage.success(`敏感词库热加载完成，共 ${data.count || 0} 条`)
    loadList()
  } catch {
    ElMessage.error('加载失败')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="sw-page">
    <div class="panel-toolbar">
      <span>当前词库: <strong>{{ wordCount }}</strong> 条</span>
      <span class="spacer"></span>
      <button class="btn btn-primary" :disabled="loading" @click="doReload">{{ loading ? '加载中…' : '🔄 热加载词库' }}</button>
    </div>

    <div class="panel-wrap word-panel">
      <div class="panel-header">📋 敏感词列表</div>
      <div class="panel-body">
        <div v-if="words.length === 0" class="empty-state">暂无数据</div>
        <div v-else class="word-grid">
          <span v-for="(w, i) in words" :key="i" class="word-tag">{{ w }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sw-page { padding: 24px; display: flex; flex-direction: column; gap: 16px; }
.panel-toolbar {
  display: flex; align-items: center; gap: 12px;
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border);
  padding: 14px 20px; font-size: 14px;
}
.spacer { flex: 1; }
.word-panel { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.panel-header { padding: 14px 18px; border-bottom: 1px solid var(--color-border); font-size: 14px; font-weight: 600; }
.panel-body { flex: 1; overflow-y: auto; padding: 16px; }
.word-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.word-tag {
  padding: 4px 12px; border-radius: 6px; background: var(--color-bg-hover);
  font-size: 13px; color: var(--color-text-secondary);
}
</style>
