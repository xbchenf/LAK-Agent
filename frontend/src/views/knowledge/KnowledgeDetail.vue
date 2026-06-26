<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDocument, getChunks, reindexDocument, changeStatus, deleteDocument } from '@/api/knowledge'
import { DocTypeLabels, StatusLabels } from '@/types/knowledge'
import type { DocumentVO, DocumentChunkVO } from '@/types/knowledge'
import DocumentStatusTag from '@/components/knowledge/DocumentStatusTag.vue'

const route = useRoute()
const router = useRouter()
const docId = route.params.docId as string

const doc = ref<DocumentVO | null>(null)
const chunks = ref<DocumentChunkVO[]>([])
const loading = ref(false)
const showChunks = ref(false)

onMounted(() => load())

async function load() {
  const res = await getDocument(docId)
  doc.value = res
}

async function loadChunks() {
  showChunks.value = !showChunks.value
  if (showChunks.value && chunks.value.length === 0) {
    const res = await getChunks(docId)
    chunks.value = res || []
  }
}

async function handleReindex() {
  loading.value = true
  try {
    await reindexDocument(docId)
    ElMessage.success('重新索引完成')
    load()
  } finally { loading.value = false }
}

async function handleStatus(action: 'publish' | 'disable' | 'reactivate') {
  await changeStatus(docId, action)
  ElMessage.success('操作成功')
  load()
}

async function handleDelete() {
  await ElMessageBox.confirm('确认删除该文档？', '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  await deleteDocument(docId)
  ElMessage.success('已删除')
  router.push('/knowledge')
}

function formatFileSize(bytes: number | null): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="detail-page" v-if="doc">
    <div class="page-header">
      <el-button @click="router.push('/knowledge')" :icon="'ArrowLeft'">返回列表</el-button>
      <h2>{{ doc.title }}</h2>
    </div>

    <el-card class="info-card">
      <template #header>文档元信息</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="文档编号">{{ doc.docId }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ DocTypeLabels[doc.docType] }}</el-descriptions-item>
        <el-descriptions-item label="状态"><DocumentStatusTag :status="doc.status" /></el-descriptions-item>
        <el-descriptions-item label="分块数">{{ doc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ doc.effectiveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过期日期">{{ doc.expireDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(doc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ doc.createTime }}</el-descriptions-item>
        <el-descriptions-item label="Qdrant 集合" :span="2">{{ doc.qdrantCollection }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <div class="actions">
      <el-button v-if="doc.status === 'DRAFT'" type="success" @click="handleStatus('publish')">发布</el-button>
      <el-button v-if="doc.status === 'ACTIVE'" type="warning" @click="handleStatus('disable')">停用</el-button>
      <el-button v-if="doc.status === 'EXPIRED'" type="primary" @click="handleStatus('reactivate')">重新启用</el-button>
      <el-button @click="handleReindex" :loading="loading">重新索引</el-button>
      <el-button type="danger" @click="handleDelete">删除</el-button>
    </div>

    <el-card class="chunks-card" style="margin-top:16px">
      <template #header>
        <span @click="loadChunks" style="cursor:pointer">
          分块列表 ({{ doc.chunkCount }})
          <span style="font-size:12px;color:#999"> {{ showChunks ? '▲' : '▼' }}</span>
        </span>
      </template>
      <template v-if="showChunks">
        <div v-for="c in chunks" :key="c.chunkIndex" class="chunk-item">
          <span class="chunk-index">#{{ c.chunkIndex }}</span>
          <span class="chunk-text">{{ c.text }}</span>
          <span class="chunk-len">{{ c.textLength }} 字</span>
        </div>
        <div v-if="chunks.length === 0" style="color:#999">暂无分块数据</div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { padding: 24px; max-width: 960px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
.chunk-item { display: flex; align-items: flex-start; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.chunk-index { font-weight: 600; color: var(--el-color-primary); min-width: 40px; }
.chunk-text { flex: 1; font-size: 13px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.chunk-len { font-size: 12px; color: #999; white-space: nowrap; }
</style>
