<script setup lang="ts">
import { computed } from 'vue'
import { StatusLabels } from '@/types/knowledge'

const props = defineProps<{ status: string }>()

const tagType = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'success'
    case 'EXPIRED': return 'danger'
    default: return 'info'
  }
})
</script>

<template>
  <span :class="['status-tag', `tag-${props.status?.toLowerCase()}`]">
    {{ StatusLabels[status] || status }}
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-block; font-size: 11px; padding: 3px 10px; border-radius: 10px; font-weight: 500;
}
.tag-active { background: #dcfce7; color: #16a34a; }
.tag-expired { background: #fef2f2; color: #dc2626; }
.tag-draft, .tag-draft { background: var(--color-bg-hover); color: var(--color-text-secondary); }
</style>
