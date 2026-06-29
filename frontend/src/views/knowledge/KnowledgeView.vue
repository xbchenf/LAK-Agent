<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, changeStatus, deleteDocument } from '@/api/knowledge'
import { DocTypeLabels } from '@/types/knowledge'
import type { DocumentVO, DocumentQueryDTO } from '@/types/knowledge'
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
    <!-- Stats mini cards -->
    <div class="stats-row">
      <div class="stat-mini">
        <div class="stat-icon" style="background:#eff6ff;color:#2563eb;">📄</div>
        <div><div class="stat-num">{{ total }}</div><div class="stat-label">文档总数</div></div>
      </div>
      <div class="stat-mini">
        <div class="stat-icon" style="background:#dcfce7;color:#16a34a;">✅</div>
        <div><div class="stat-num">{{ documents.filter(d => d.status === 'ACTIVE').length }}</div><div class="stat-label">已发布</div></div>
      </div>
      <div class="stat-mini">
        <div class="stat-icon" style="background:#fef3c7;color:#ea580c;">📝</div>
        <div><div class="stat-num">{{ documents.filter(d => d.status === 'DRAFT').length }}</div><div class="stat-label">草稿</div></div>
      </div>
      <div class="stat-mini">
        <div class="stat-icon" style="background:#fef2f2;color:#dc2626;">⏱</div>
        <div><div class="stat-num">{{ documents.filter(d => d.status === 'EXPIRED').length }}</div><div class="stat-label">已过期</div></div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <select v-model="activeType" class="toolbar-select" @change="onTypeChange(activeType)">
          <option v-for="t in typeTabs" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </div>
      <div class="toolbar-right">
        <input v-model="keyword" class="search-input" placeholder="🔍 搜索标题、内容…" @keyup.enter="onSearch" />
        <button class="btn btn-primary" @click="uploadDlg?.open()">+ 上传文档</button>
      </div>
    </div>

    <!-- Card Grid -->
    <div v-loading="loading" class="doc-cards">
      <div v-for="doc in documents" :key="doc.docId" class="doc-card" @click="router.push(`/knowledge/${doc.docId}`)">
        <div class="doc-card-top">
          <span class="doc-type-tag">{{ DocTypeLabels[doc.docType] || doc.docType }}</span>
          <span :class="['doc-status-tag', doc.status === 'ACTIVE' ? 'st-active' : doc.status === 'EXPIRED' ? 'st-expired' : 'st-draft']">
            {{ doc.status === 'ACTIVE' ? '已发布' : doc.status === 'EXPIRED' ? '已过期' : '草稿' }}
          </span>
        </div>
        <div class="doc-card-title">{{ doc.title }}</div>
        <div class="doc-card-meta">
          <span>📅 {{ doc.effectiveDate || '-' }} 生效</span>
          <span>📊 {{ doc.chunkCount }} 分块</span>
        </div>
        <div class="doc-card-actions" @click.stop>
          <template v-if="doc.status === 'DRAFT'">
            <button class="act-btn act-publish" @click="handlePublish(doc.docId)">发布</button>
          </template>
          <template v-if="doc.status === 'ACTIVE'">
            <button class="act-btn act-disable" @click="handleDisable(doc.docId)">停用</button>
          </template>
          <template v-if="doc.status === 'EXPIRED'">
            <button class="act-btn act-reactivate" @click="handleReactivate(doc.docId)">启用</button>
          </template>
          <button class="act-btn act-delete" @click="handleDelete(doc.docId)">删除</button>
        </div>
      </div>
      <div v-if="documents.length === 0 && !loading" class="doc-empty">暂无文档</div>
    </div>

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
.knowledge-page { padding: 24px; display: flex; flex-direction: column; gap: 16px; }

/* Stats */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.stat-mini {
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  padding: 16px 20px; box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border); display: flex; align-items: center; gap: 14px;
}
.stat-icon {
  width: 44px; height: 44px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0;
}
.stat-num { font-size: 22px; font-weight: 700; color: var(--color-text); }
.stat-label { font-size: 12px; color: var(--color-text-secondary); }

/* Toolbar */
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--color-bg-white); padding: 14px 20px;
  border-radius: var(--radius-lg); box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}
.toolbar-left, .toolbar-right { display: flex; gap: 8px; align-items: center; }
.toolbar-select {
  padding: 8px 12px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 13px; outline: none; color: var(--color-text); background: var(--color-bg-white);
  font-family: inherit;
}
.toolbar-select:focus { border-color: var(--color-primary-light); }
.search-input {
  padding: 8px 14px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 13px; outline: none; width: 240px; font-family: inherit; color: var(--color-text);
}
.search-input:focus { border-color: var(--color-primary-light); }
.search-input::placeholder { color: var(--color-text-muted); }

.btn {
  padding: 8px 18px; border-radius: var(--radius); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .15s; font-family: inherit; white-space: nowrap;
}
.btn-gold {
  border: 1px solid var(--color-accent-light); background: var(--color-bg-white);
  color: var(--color-accent);
}
.btn-gold:hover { background: var(--color-accent-light); }
.btn-primary {
  background: var(--color-primary); color: #fff; border: none;
}
.btn-primary:hover { background: var(--color-primary-hover); }

/* Card Grid */
.doc-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.doc-card { background: var(--color-bg-white); border-radius: var(--radius-lg); padding: 20px 22px; box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); cursor: pointer; transition: all .2s; display: flex; flex-direction: column; gap: 10px; }
.doc-card:hover { box-shadow: var(--shadow-md); border-color: var(--color-primary-bg-hover); transform: translateY(-1px); }
.doc-card-top { display: flex; justify-content: space-between; align-items: center; }
.doc-type-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: var(--color-bg-hover); color: var(--color-text-secondary); }
.doc-status-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; }
.st-active { background: #dcfce7; color: var(--color-success); }
.st-expired { background: #fef2f2; color: var(--color-danger); }
.st-draft { background: var(--color-bg-hover); color: var(--color-text-muted); }
.doc-card-title { font-size: 14px; font-weight: 600; color: var(--color-text); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.doc-card-meta { display: flex; gap: 16px; font-size: 12px; color: var(--color-text-secondary); }
.doc-card-actions { display: flex; gap: 6px; flex-wrap: wrap; padding-top: 6px; border-top: 1px solid var(--color-border-light); }
.act-btn { padding: 4px 12px; border-radius: 6px; font-size: 11px; cursor: pointer; border: 1px solid var(--color-border); background: var(--color-bg-white); color: var(--color-text-secondary); transition: all .15s; font-family: inherit; }
.act-btn:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.act-delete:hover { border-color: #fecaca; color: var(--color-danger); }
.act-publish { color: var(--color-success); border-color: #bbf7d0; }
.act-publish:hover { background: #dcfce7; }
.doc-empty { grid-column: 1 / -1; text-align: center; padding: 60px; color: var(--color-text-muted); }
</style>
