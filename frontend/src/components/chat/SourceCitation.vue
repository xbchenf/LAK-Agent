<script setup lang="ts">
import type { SourceDoc } from '@/types'

defineProps<{ sources: SourceDoc[] }>()
</script>

<template>
  <div class="sources">
    <div class="source-title">📄 答复依据</div>
    <div v-for="(doc, i) in sources" :key="i" class="source-item">
      <div class="source-header">
        <span class="source-no-tag">{{ doc.sourceNo }}</span>
        <span class="source-name">{{ doc.title }}</span>
        <span v-if="doc.effectiveDate" class="source-date">生效: {{ doc.effectiveDate }}</span>
      </div>
      <blockquote>{{ doc.fragment?.substring(0, 150) }}{{ (doc.fragment?.length || 0) > 150 ? '...' : '' }}</blockquote>
    </div>
  </div>
</template>

<style scoped>
.sources { margin-top: 12px; border-top: 1px solid var(--color-border-light); padding-top: 10px; }
.source-title { font-size: 12px; color: var(--color-text-secondary); margin-bottom: 6px; font-weight: 500; }
.source-item { margin-bottom: 8px; }
.source-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.source-no-tag {
  display: inline-block; font-size: 10px; padding: 2px 8px; border-radius: 4px;
  background: var(--color-primary-bg); color: var(--color-primary); font-weight: 500;
}
.source-name { font-weight: 500; font-size: 13px; color: var(--color-text); }
.source-date { font-size: 11px; color: var(--color-text-muted); }
blockquote {
  margin: 4px 0 0; padding: 6px 10px;
  background: var(--color-primary-bg); border-left: 3px solid var(--color-primary-light);
  font-size: 12px; color: var(--color-text-secondary); border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
</style>
