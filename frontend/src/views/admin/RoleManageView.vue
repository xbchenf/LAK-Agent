<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoles, listMenus, updateRoleMenus } from '@/api/admin'
import type { RoleVO, MenuVO } from '@/types/role'

const roles = ref<RoleVO[]>([])
const menus = ref<MenuVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingRole = ref<RoleVO | null>(null)
const checkedIds = ref<number[]>([])

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    const [r, m] = await Promise.all([listRoles(), listMenus()])
    roles.value = r
    menus.value = m
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}

function menuName(id: number): string {
  for (const m of menus.value) {
    if (m.id === id) return m.menuName
    if (m.children) {
      for (const c of m.children) if (c.id === id) return c.menuName
    }
  }
  return ''
}

function openEdit(role: RoleVO) {
  editingRole.value = role
  checkedIds.value = [...role.menuIds]
  dialogVisible.value = true
}

function isParentChecked(menu: MenuVO): boolean {
  return checkedIds.value.includes(menu.id)
}

function isParentIndeterminate(menu: MenuVO): boolean {
  if (!menu.children) return false
  const childIds = menu.children.map(c => c.id)
  const checked = childIds.filter(id => checkedIds.value.includes(id)).length
  return checked > 0 && checked < childIds.length
}

function handleParentChange(menu: MenuVO, checked: boolean) {
  if (!menu.children || menu.children.length === 0) {
    // 无子菜单：直接切换自身
    if (checked) {
      checkedIds.value = [...new Set([...checkedIds.value, menu.id])]
    } else {
      checkedIds.value = checkedIds.value.filter(id => id !== menu.id)
    }
    return
  }
  const childIds = menu.children.map(c => c.id)
  if (checked) {
    checkedIds.value = [...new Set([...checkedIds.value, menu.id, ...childIds])]
  } else {
    checkedIds.value = checkedIds.value.filter(id => id !== menu.id && !childIds.includes(id))
  }
}

function handleChildChange(parent: MenuVO, child: MenuVO, checked: boolean) {
  if (checked) {
    checkedIds.value = [...new Set([...checkedIds.value, child.id, parent.id])]
  } else {
    checkedIds.value = checkedIds.value.filter(id => id !== child.id)
    // 所有子节点都取消→父节点也取消
    if (parent.children && !parent.children.some(c => checkedIds.value.includes(c.id))) {
      checkedIds.value = checkedIds.value.filter(id => id !== parent.id)
    }
  }
}

async function savePermissions() {
  if (!editingRole.value) return
  // 只保存子节点对应的 menuIds（父节点由子节点推出来，或者也保存都没关系——后端只认 menu_ids）
  try {
    await updateRoleMenus(editingRole.value.id, checkedIds.value)
    ElMessage.success('权限已更新')
    dialogVisible.value = false
    load()
  } catch { /* handled by interceptor */ }
}
</script>

<template>
  <div class="role-page">
    <div class="page-header">
      <h3>角色管理</h3>
      <span class="hint">共 {{ roles.length }} 个角色 | 勾选功能菜单分配权限</span>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="role in roles" :key="role.id" :span="8" style="margin-bottom:16px">
        <el-card>
          <h4>{{ role.roleName || role.roleCode }}</h4>
          <p class="role-code">{{ role.roleCode }}</p>
          <div class="perm-tags">
            <el-tag v-for="id in role.menuIds" :key="id" size="small" style="margin:2px">
              {{ menuName(id) }}
            </el-tag>
            <span v-if="!role.menuIds.length" style="color:#c0c4cc;font-size:12px">无权限</span>
          </div>
          <el-button size="small" style="margin-top:12px" @click="openEdit(role)">编辑权限</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="编辑权限" width="420px">
      <p style="color:#909399;margin-bottom:16px">
        角色: {{ editingRole?.roleName || editingRole?.roleCode }} | 勾选可访问的功能菜单
      </p>

      <div v-for="menu in menus" :key="menu.id" style="margin-bottom:12px">
        <el-checkbox
          :model-value="isParentChecked(menu)"
          :indeterminate="isParentIndeterminate(menu)"
          @change="(val: boolean) => handleParentChange(menu, val)"
        >
          <strong>{{ menu.menuName }}</strong>
        </el-checkbox>
        <div v-if="menu.children" style="margin-left:24px;margin-top:4px">
          <el-checkbox
            v-for="child in menu.children" :key="child.id"
            :model-value="checkedIds.includes(child.id)"
            @change="(val: boolean) => handleChildChange(menu, child, val)"
          >
            {{ child.menuName }}
          </el-checkbox>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-page { padding: 24px; max-width: 960px; }
.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.page-header h3 { margin: 0; }
.hint { color: #909399; font-size: 13px; }
h4 { margin: 0 0 4px; font-size: 15px; }
.role-code { margin: 0 0 8px; color: var(--el-color-primary); font-size: 13px; font-weight: 600; }
.perm-tags { min-height: 24px; }
</style>
