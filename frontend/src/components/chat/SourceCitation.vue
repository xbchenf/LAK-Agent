<script setup lang="ts">
import type { SourceDoc } from '@/types'

defineProps<{ sources: SourceDoc[] }>()
</script>

<template>
  <div class="sources">
    <div class="source-title">答复依据</div>
    <div v-for="(doc, i) in sources" :key="i" class="source-item">
      <div class="source-header">
        <el-tag size="small" type="info">{{ doc.sourceNo }}</el-tag>
        <span class="source-name">{{ doc.title }}</span>
        <span v-if="doc.effectiveDate" class="source-date">生效: {{ doc.effectiveDate }}</span>
      </div>
      <blockquote>{{ doc.fragment?.substring(0, 150) }}{{ (doc.fragment?.length || 0) > 150 ? '...' : '' }}</blockquote>
    </div>
  </div>
</template>

<style scoped>
.sources { margin-top: 12px; border-top: 1px solid #e4e7ed; padding-top: 8px; }
.source-title { font-size: 12px; color: #909399; margin-bottom: 4px; }
.source-item { margin-bottom: 8px; }
.source-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.source-name { font-weight: 500; font-size: 13px; }
.source-date { font-size: 11px; color: #c0c4cc; }
blockquote { margin: 4px 0 0; padding: 4px 8px; background: #fafafa; border-left: 3px solid #dcdfe6; font-size: 12px; color: #606266; }
</style>
