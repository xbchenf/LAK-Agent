---
name: vue-reusable-comp
description: Vue 3 可复用组件规范 — Props/Emits TypeScript 化 + scoped 样式 + LAK 对话组件约定
---

# Vue 可复用组件规范（LAK-Agent）

## 组件分类

| 类型 | 位置 | 可含 API | LAK 示例 |
|------|------|---------|---------|
| 页面组件 | `views/` | ✅ | `ChatView.vue`, `LoginView.vue` |
| 对话组件 | `components/chat/` | ❌ | `ChatWindow`, `MessageBubble`, `SourceCitation`, `StreamingText` |
| 通用组件 | `components/common/` | ❌ | `AppHeader`, `AppSidebar` |
| 工单组件 | `components/ticket/` | ❌ | `TicketForm` |

## 标准模板

```vue
<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  modelValue?: string
  items?: Item[]
  loading?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  items: () => [],
  loading: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'change': [value: string]
  'select': [item: Item]
}>()

const selectedId = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function handleSelect(item: Item) {
  emit('select', item)
}
</script>

<template>
  <div class="xxx-comp" :class="{ 'is-disabled': disabled }">
    <slot name="header" />
    <div v-for="item in items" :key="item.id" @click="handleSelect(item)">
      <slot name="item" :item="item">{{ item.name }}</slot>
    </div>
    <slot name="footer" />
  </div>
</template>

<style scoped>
.xxx-comp { }
.xxx-comp.is-disabled { opacity: 0.5; pointer-events: none; }
</style>
```

## 强制约束

- Props 必须声明完整 TypeScript interface，`withDefaults` 设置默认值
- Emits 必须用 `defineEmits` 声明类型
- 必须 `<style scoped>`，禁止全局样式污染
- 可复用组件禁止调用 API（API 归页面或 store）
- 组件禁止硬编码业务数据
