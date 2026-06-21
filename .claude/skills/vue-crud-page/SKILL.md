---
name: vue-crud-page
description: Element Plus 表格+分页标准模板。管理后台页面（敏感词管理/知识库管理）自动应用。
---

# Vue CRUD 页面规范（LAK-Agent）

## 触发条件

管理后台的列表页面（敏感词管理、知识库管理、工单列表），自动应用此 Skill。

## 标准模板

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PageResult, PageQuery } from '@/types'
import type { XxxVO } from '@/api/xxxApi'
import { pageXxx, deleteXxx } from '@/api/xxxApi'

const loading = ref(false)
const list = ref<XxxVO[]>([])
const total = ref(0)
const query = ref<PageQuery>({ page: 1, size: 20 })

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const result: PageResult<XxxVO> = await pageXxx(query.value)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  query.value.page = page
  fetchData()
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteXxx(id)
  ElMessage.success('删除成功')
  fetchData()
}
</script>

<template>
  <div>
    <!-- 搜索栏 -->
    <el-form :model="query" inline>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="搜索..." clearable @change="fetchData" />
      </el-form-item>
    </el-form>

    <!-- 表格 -->
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="query.page"
      :page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />
  </div>
</template>
```

## 强制约束

- 列表页统一用 `el-table` + `el-pagination`
- 删除操作必须二次确认（`ElMessageBox.confirm`）
- 搜索条件变更后自动重新请求（`@change="fetchData"`）
- `v-loading` 绑定加载态，避免用户重复点击
- 分页参数统一 `{ page, size }`，后端返回 `PageResult<T>`
