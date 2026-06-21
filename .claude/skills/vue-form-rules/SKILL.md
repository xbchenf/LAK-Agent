---
name: vue-form-rules
description: Element Plus 表单校验规范 — 登录表单 + 工单表单 + 敏感词前端预检
---

# Vue 表单校验规范（LAK-Agent）

## Element Plus 表单模板

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度 2-32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 调用 API...
}
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
    <el-form-item label="用户名" prop="username">
      <el-input v-model="form.username" />
    </el-form-item>
    <el-form-item label="密码" prop="password">
      <el-input v-model="form.password" type="password" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submit">登录</el-button>
    </el-form-item>
  </el-form>
</template>
```

## LAK-Agent 特有校验

### 工单表单校验

```typescript
const ticketRules: FormRules = {
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请描述您的问题', trigger: 'blur' },
    { min: 10, max: 2000, message: '描述长度 10-2000 字', trigger: 'blur' },
  ],
}
```

### 对话输入校验

```typescript
// 消息长度限制（后端 2000 字符）
const messageRules: FormRules = {
  content: [
    { required: true, message: '请输入消息', trigger: 'submit' },
    { max: 2000, message: '消息超过 2000 字符限制', trigger: 'submit' },
  ],
}
```

## 强制约束

- 所有表单必须用 `el-form` + `FormRules` 声明式校验
- 手机号用正则 `/^1[3-9]\d{9}$/` 前端预校验
- 消息长度 2000 字符前端拦截（双保险: 后端再截断）
- 禁止 `v-model.trim` 等隐式变更（校验失去精度）
