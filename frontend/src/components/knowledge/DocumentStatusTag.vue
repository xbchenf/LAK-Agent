<script setup lang="ts">
import { computed } from 'vue'
import { StatusLabels } from '@/types/knowledge'

const props = defineProps<{ status: string }>()

const tagType = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'success'
    case 'EXPIRED': return 'warning'
    default: return 'info'
  }
})
</script>

<template>
  <el-tag :type="tagType" size="small">{{ StatusLabels[status] || status }}</el-tag>
</template>
