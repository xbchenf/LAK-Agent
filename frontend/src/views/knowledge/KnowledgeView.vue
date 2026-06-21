<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, changeStatus, deleteDocument } from '@/api/knowledge'
import { DocTypeLabels } from '@/types/knowledge'
import type { DocumentVO, DocumentQueryDTO } from '@/types/knowledge'
import DocumentTable from '@/components/knowledge/DocumentTable.vue'
import DocumentUploadDialog from '@/components/knowledge/DocumentUploadDialog.vue'

const documents = ref<DocumentVO[]>([])
const loading = ref(false)
const total = ref(0)
const query = ref<DocumentQueryDTO>({ page: 1, size: 10 })
const keyword = ref('')
const activeType = ref('')

const typeTabs = [
  { label: '全部', value: '' },
  ...Object.entries(DocTypeLabels).map(([value, label]) => ({ label, value })),
]

const uploadDlg = ref<InstanceType<typeof DocumentUploadDialog>>()

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const params: DocumentQueryDTO = { ...query.value, keyword: keyword.value || undefined }
    if (activeType.value) params.docType = activeType.value
    const res = await listDocuments(params)
    documents.value = res.records
    total.value = res.total
  } catch { /* handled by request interceptor */ }
  finally { loading.value = false }
}

function onSearch() { query.value.page = 1; loadList() }
function onTypeChange(type: string) { activeType.value = type; query.value.page = 1; loadList() }
function onPageChange(page: number) { query.value.page = page; loadList() }
function onSizeChange(size: number) { query.value.size = size; query.value.page = 1; loadList() }

async function handlePublish(docId: string) {
  await changeStatus(docId, 'publish')
  ElMessage.success('已发布')
  loadList()
}

async function handleDisable(docId: string) {
  await changeStatus(docId, 'disable')
  ElMessage.success('已停用')
  loadList()
}

async function handleReactivate(docId: string) {
  await changeStatus(docId, 'reactivate')
  ElMessage.success('已重新启用')
  loadList()
}

async function handleDelete(docId: string) {
  await ElMessageBox.confirm('确认删除该文档？此操作不可恢复。', '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  await deleteDocument(docId)
  ElMessage.success('已删除')
  loadList()
}
</script>

<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="uploadDlg?.open()">+ 上传文档</el-button>
    </div>

    <div class="filter-bar">
      <div class="type-tabs">
        <el-button v-for="t in typeTabs" :key="t.value"
          :type="activeType === t.value ? 'primary' : ''" size="small"
          @click="onTypeChange(t.value)">{{ t.label }}</el-button>
      </div>
      <el-input v-model="keyword" placeholder="搜索标题..." style="width:240px" clearable
        @keyup.enter="onSearch" @clear="onSearch">
        <template #append><el-button @click="onSearch">搜索</el-button></template>
      </el-input>
    </div>

    <DocumentTable
      :documents="documents" :loading="loading"
      @publish="handlePublish" @disable="handleDisable"
      @reactivate="handleReactivate" @delete="handleDelete"
    />

    <el-pagination
      v-if="total > 0"
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <DocumentUploadDialog ref="uploadDlg" @uploaded="loadList" />
  </div>
</template>

<style scoped>
.knowledge-page { padding: 24px; max-width: 1200px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.type-tabs { display: flex; gap: 6px; }
</style>
