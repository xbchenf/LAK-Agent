<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { DocTypeLabels } from '@/types/knowledge'
import type { DocumentVO } from '@/types/knowledge'
import DocumentStatusTag from './DocumentStatusTag.vue'

const props = defineProps<{ documents: DocumentVO[]; loading?: boolean }>()
const emit = defineEmits<{
  (e: 'publish', docId: string): void
  (e: 'disable', docId: string): void
  (e: 'reactivate', docId: string): void
  (e: 'delete', docId: string): void
}>()

const router = useRouter()

function viewDetail(docId: string) {
  router.push(`/knowledge/${docId}`)
}

function actions(doc: DocumentVO) {
  const acts: { label: string; handler: () => void }[] = []
  acts.push({ label: '查看详情', handler: () => viewDetail(doc.docId) })
  if (doc.status === 'DRAFT') {
    acts.push({ label: '发布', handler: () => emit('publish', doc.docId) })
  }
  if (doc.status === 'ACTIVE') {
    acts.push({ label: '停用', handler: () => emit('disable', doc.docId) })
  }
  if (doc.status === 'EXPIRED') {
    acts.push({ label: '重新启用', handler: () => emit('reactivate', doc.docId) })
  }
  acts.push({ label: '删除', handler: () => emit('delete', doc.docId) })
  return acts
}
</script>

<template>
  <el-table :data="documents" v-loading="loading" stripe style="width:100%">
    <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
    <el-table-column prop="docType" label="类型" width="100">
      <template #default="{ row }">
        <el-tag size="small" type="info">{{ DocTypeLabels[row.docType] || row.docType }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <DocumentStatusTag :status="row.status" />
      </template>
    </el-table-column>
    <el-table-column prop="effectiveDate" label="生效日期" width="120" />
    <el-table-column prop="expireDate" label="过期日期" width="120">
      <template #default="{ row }">
        <span :style="{ color: row.expireDate && new Date(row.expireDate) < new Date() ? 'var(--el-color-danger)' : '' }">
          {{ row.expireDate || '-' }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="140" fixed="right">
      <template #default="{ row }">
        <el-dropdown trigger="click">
          <el-button size="small">操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="act in actions(row)" :key="act.label" @click="act.handler">
                {{ act.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </el-table-column>
  </el-table>
</template>
