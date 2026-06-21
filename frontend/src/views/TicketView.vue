<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/api/request'

interface TicketRecord {
  ticketNo: string
  complaintType: string
  contactName: string
  description: string
  status: string
  createTime: string
}

const tickets = ref<TicketRecord[]>([])
const ticketNo = ref('')
const showCreate = ref(false)
const formRef = ref<FormInstance>()
const form = ref({ complaintType: '', contactName: '', contactPhone: '', description: '' })

const rules: FormRules = {
  complaintType: [{ required: true, message: '请选择投诉类型', trigger: 'change' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请填写问题描述', trigger: 'blur' },
    { min: 10, message: '问题描述至少10个字', trigger: 'blur' },
    { max: 2000, message: '问题描述不超过2000字', trigger: 'blur' }
  ],
}

onMounted(() => loadMyTickets())

async function loadMyTickets() {
  try {
    const data = await request.get('/tickets/mine') as any
    tickets.value = data || []
  } catch {
    // Token可能过期，静默处理，不触发全局登出
  }
}

async function searchTicket() {
  if (!ticketNo.value.trim()) return
  try {
    const data = await request.get(`/tickets/${ticketNo.value}`) as any
    tickets.value = [data]
  } catch { ElMessage.error('工单不存在') }
}

async function submitTicket() {
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    await request.post('/tickets', form.value)
    ElMessage.success('工单创建成功')
    showCreate.value = false
    loadMyTickets()
  } catch (e: any) {
    const detail = e?.response?.data?.data
    const msg = detail ? Object.values(detail).join('；') : '创建失败'
    ElMessage.error(msg)
  }
}

const statusMap: Record<string, { text: string; type: string }> = {
  PENDING:    { text: '待处理', type: 'warning' },
  PROCESSING: { text: '处理中', type: 'primary' },
  COMPLETED:  { text: '已办结', type: 'success' },
  FAILED:     { text: '失败',   type: 'danger' },
}
</script>

<template>
  <div class="ticket-page">
    <div class="page-header">
      <h3>我的工单</h3>
      <div class="header-actions">
        <el-input v-model="ticketNo" placeholder="输入工单编号查询" style="width:240px" @keyup.enter="searchTicket" />
        <el-button type="primary" @click="searchTicket">查询</el-button>
        <el-button @click="showCreate = true">新建工单</el-button>
      </div>
    </div>

    <el-table :data="tickets" stripe style="margin-top:16px" empty-text="暂无工单记录">
      <el-table-column prop="ticketNo" label="工单编号" width="220" />
      <el-table-column label="投诉类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.complaintType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="问题描述" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ statusMap[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
    </el-table>

    <el-dialog v-model="showCreate" title="新建工单" width="500px" @closed="formRef?.resetFields()">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="投诉类型" prop="complaintType">
          <el-select v-model="form.complaintType">
            <el-option label="治安投诉" value="治安投诉" />
            <el-option label="窗口服务投诉" value="窗口服务投诉" />
            <el-option label="派出所/民警投诉" value="派出所/民警投诉" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="问题描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4"
            show-word-limit maxlength="2000" minlength="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitTicket">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-page { max-width: 960px; margin: 0 auto; padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; align-items: center; }
</style>
