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

const searchUsername = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingUser = ref<UserVO | null>(null)
const form = ref({ username: '', password: '', realName: '', roleId: null as number | null, status: 'ACTIVE' as string })

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

const roleClass = (roleName: string) => {
  if (roleName?.includes('ADMIN')) return 'role-admin'
  if (roleName?.includes('OPERATOR')) return 'role-operator'
  return 'role-user'
}
</script>

<template>
  <div class="user-page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <input v-model="searchUsername" class="search-input" placeholder="🔍 搜索用户名…"
          @keyup.enter="load()" />
        <button class="btn btn-outline" @click="load()">搜索</button>
      </div>
      <button class="btn btn-primary" @click="openCreate()">+ 新增用户</button>
    </div>

    <!-- Table -->
    <div class="table-wrap">
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="姓名" width="120">
          <template #default="{ row }">{{ row.realName || '--' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <span :class="['role-badge', roleClass(row.roleName)]">{{ row.roleName || '未分配' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 'ACTIVE' ? 'status-active' : 'status-disabled']">
              {{ StatusLabels[row.status] || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <span class="action-link" @click="openEdit(row)">编辑</span>
            <span class="action-link" @click="openResetPwd(row)">重置密码</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create/Edit Dialog -->
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

    <!-- Reset Password Dialog -->
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
.user-page { padding: 24px; display: flex; flex-direction: column; gap: 16px; }

/* Toolbar */
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--color-bg-white); padding: 14px 20px;
  border-radius: var(--radius-lg); box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}
.toolbar-left { display: flex; gap: 8px; align-items: center; }
.search-input {
  padding: 8px 14px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 13px; outline: none; width: 220px; font-family: inherit; color: var(--color-text);
}
.search-input:focus { border-color: var(--color-primary-light); }
.search-input::placeholder { color: var(--color-text-muted); }

.btn {
  padding: 8px 18px; border-radius: var(--radius); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .15s; font-family: inherit;
}
.btn-outline { border: 1px solid var(--color-border); background: var(--color-bg-white); color: var(--color-text-secondary); }
.btn-outline:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.btn-primary { background: var(--color-primary); color: #fff; border: none; }
.btn-primary:hover { background: var(--color-primary-hover); }

/* Table wrap */
.table-wrap {
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); overflow: hidden;
}

/* Role badges */
.role-badge {
  display: inline-block; font-size: 11px; padding: 3px 12px; border-radius: 12px; font-weight: 500;
}
.role-admin { background: #fef3c7; color: #92400e; }
.role-operator { background: var(--color-primary-bg); color: #1d4ed8; }
.role-user { background: var(--color-bg-hover); color: var(--color-text-secondary); }

/* Status badges */
.status-badge {
  display: inline-block; font-size: 10px; padding: 3px 10px; border-radius: 10px;
}
.status-active { background: #dcfce7; color: #16a34a; }
.status-disabled { background: #fef2f2; color: #dc2626; }

/* Action links */
.action-link { font-size: 12px; color: var(--color-primary); cursor: pointer; margin-right: 12px; }
.action-link:hover { text-decoration: underline; }
</style>
