<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, createUser, updateUser, resetPassword } from '@/api/user'
import { listRoles } from '@/api/admin'
import { StatusLabels } from '@/types/user'
import type { UserVO } from '@/types/user'
import type { RoleVO } from '@/types/role'

const users = ref<UserVO[]>([])
const roles = ref<RoleVO[]>([])
const loading = ref(false)

// 搜索
const searchUsername = ref('')

// 新增/编辑弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingUser = ref<UserVO | null>(null)
const form = ref({ username: '', password: '', realName: '', roleId: null as number | null, status: 'ACTIVE' as string })

// 重置密码弹窗
const pwdDialogVisible = ref(false)
const pwdUserId = ref<number | null>(null)
const newPassword = ref('')

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    const [u, r] = await Promise.all([
      listUsers({ username: searchUsername.value || undefined }),
      listRoles()
    ])
    users.value = u
    roles.value = r
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  editingUser.value = null
  form.value = { username: '', password: '', realName: '', roleId: null, status: 'ACTIVE' }
  dialogVisible.value = true
}

function openEdit(user: UserVO) {
  isEdit.value = true
  editingUser.value = user
  form.value = {
    username: user.username,
    password: '',
    realName: user.realName || '',
    roleId: user.roleId,
    status: user.status
  }
  dialogVisible.value = true
}

async function save() {
  try {
    if (isEdit.value && editingUser.value) {
      await updateUser(editingUser.value.id, {
        realName: form.value.realName,
        email: form.value.email,
        phone: form.value.phone,
        status: form.value.status,
        roleId: form.value.roleId
      })
      ElMessage.success('已更新')
    } else {
      if (!form.value.username || !form.value.password) {
        ElMessage.warning('用户名和密码必填')
        return
      }
      await createUser({
        username: form.value.username,
        password: form.value.password,
        realName: form.value.realName,
        roleId: form.value.roleId || undefined
      })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    load()
  } catch { /* handled by interceptor */ }
}

function openResetPwd(user: UserVO) {
  pwdUserId.value = user.id
  newPassword.value = ''
  pwdDialogVisible.value = true
}

async function doResetPwd() {
  if (!pwdUserId.value || newPassword.value.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  try {
    await resetPassword(pwdUserId.value, newPassword.value)
    ElMessage.success('密码已重置')
    pwdDialogVisible.value = false
  } catch { /* handled by interceptor */ }
}
</script>

<template>
  <div class="user-page">
    <div class="page-header">
      <h3>用户管理</h3>
      <div class="header-right">
        <el-input v-model="searchUsername" placeholder="搜索用户名" clearable style="width:180px"
          @keyup.enter="load()" @clear="load()">
          <template #append><el-button @click="load()">搜索</el-button></template>
        </el-input>
        <el-button type="primary" @click="openCreate()" style="margin-left:12px">+ 新增用户</el-button>
      </div>
    </div>

    <el-table :data="users" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120">
        <template #default="{ row }">{{ row.realName || '--' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="140">
        <template #default="{ row }">{{ row.roleName || '未分配' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ StatusLabels[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" min-width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="openResetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="用户名" v-if="!isEdit">
          <el-input v-model="form.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="至少6位" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" placeholder="选择角色" style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName || r.roleCode" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" v-if="isEdit">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="重置密码" width="360px">
      <el-input v-model="newPassword" type="password" placeholder="输入新密码（至少6位）" show-password />
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-page { padding: 24px; max-width: 1100px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { margin: 0; font-size: 20px; }
.header-right { display: flex; align-items: center; }
</style>
