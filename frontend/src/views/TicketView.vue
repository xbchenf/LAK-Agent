<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
const page = ref(1)
const pageSize = ref(10)
const showCreate = ref(false)
const showDetail = ref(false)
const detailTicket = ref<TicketRecord | null>(null)
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

onMounted(() => {
  loadMyTickets()
  const raw = sessionStorage.getItem('ticket_prefill')
  if (raw) {
    try {
      const prefill = JSON.parse(raw)
      if (prefill.complaintType) form.value.complaintType = prefill.complaintType
      if (prefill.contactName) form.value.contactName = prefill.contactName
      if (prefill.contactPhone) form.value.contactPhone = prefill.contactPhone
      if (prefill.description) form.value.description = prefill.description
      showCreate.value = true
    } catch { /* ignore */ }
    sessionStorage.removeItem('ticket_prefill')
  }
})

async function loadMyTickets() {
  try {
    const data = await request.get('/tickets/mine') as any
    tickets.value = data || []
    page.value = 1
  } catch { /* ignore */ }
}

const paginatedTickets = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return tickets.value.slice(start, start + pageSize.value)
})

async function searchTicket() {
  if (!ticketNo.value.trim()) return
  try {
    const data = await request.get(`/tickets/${ticketNo.value}`) as any
    tickets.value = [data]
  } catch { /* interceptor already shows error */ }
}

async function viewTicket(ticketNo: string) {
  try {
    detailTicket.value = await request.get(`/tickets/${ticketNo}`) as any
    showDetail.value = true
  } catch { /* interceptor already shows error */ }
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

const statusMap: Record<string, { text: string; cls: string }> = {
  PENDING:    { text: '待处理', cls: 'status-pending' },
  PROCESSING: { text: '处理中', cls: 'status-processing' },
  COMPLETED:  { text: '已办结', cls: 'status-completed' },
  FAILED:     { text: '已关闭', cls: 'status-failed' },
}
</script>

<template>
  <div class="ticket-page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <input v-model="ticketNo" class="search-input" placeholder="🔍 搜索工单编号…" @keyup.enter="searchTicket" />
        <button class="btn btn-outline" @click="searchTicket">查询</button>
      </div>
      <button class="btn btn-primary" @click="showCreate = true">+ 新建工单</button>
    </div>

    <!-- Table -->
    <div class="table-wrap">
      <el-table :data="paginatedTickets" stripe empty-text="暂无工单记录">
        <el-table-column label="工单编号" width="220">
          <template #default="{ row }">
            <span class="ticket-no-link" @click="viewTicket(row.ticketNo)">{{ row.ticketNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="投诉类型" width="120">
          <template #default="{ row }">
            <span class="type-tag">{{ row.complaintType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="问题描述" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="desc-text">{{ row.description }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系人" width="100" prop="contactName" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span :class="['status-badge', statusMap[row.status]?.cls || 'status-pending']">
              {{ statusMap[row.status]?.text || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" prop="createTime" />
      </el-table>
      <div v-if="tickets.length > pageSize" style="padding:12px 0;text-align:right">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
          :total="tickets.length" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" />
      </div>
    </div>

    <!-- Create Dialog -->
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
          <el-input v-model="form.description" type="textarea" :rows="4" show-word-limit maxlength="2000" minlength="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitTicket">提交</el-button>
      </template>
    </el-dialog>

    <!-- Ticket Detail Dialog -->
    <el-dialog v-model="showDetail" title="工单详情" width="560px">
      <template v-if="detailTicket">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单编号">{{ detailTicket.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="投诉类型">{{ detailTicket.complaintType }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detailTicket.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detailTicket.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="['status-badge', statusMap[detailTicket.status]?.cls || 'status-pending']">
              {{ statusMap[detailTicket.status]?.text || detailTicket.status }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailTicket.createTime }}</el-descriptions-item>
          <el-descriptions-item label="问题描述">{{ detailTicket.description }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-page { padding: 24px; }

/* Toolbar */
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--color-bg-white); padding: 14px 20px;
  border-radius: var(--radius-lg); box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border); margin-bottom: 16px;
}
.toolbar-left { display: flex; gap: 8px; align-items: center; }
.search-input {
  padding: 8px 14px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 13px; outline: none; width: 260px; font-family: inherit;
  color: var(--color-text);
}
.search-input:focus { border-color: var(--color-primary-light); }
.search-input::placeholder { color: var(--color-text-muted); }

.btn {
  padding: 8px 18px; border-radius: var(--radius); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .15s; font-family: inherit;
}
.btn-outline {
  border: 1px solid var(--color-border); background: var(--color-bg-white); color: var(--color-text-secondary);
}
.btn-outline:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.btn-primary {
  background: var(--color-primary); color: #fff; border: none;
}
.btn-primary:hover { background: var(--color-primary-hover); }

/* Table wrap */
.table-wrap {
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); overflow: hidden;
}

/* Table cells */
.ticket-no-link { color: var(--color-primary); font-weight: 600; cursor: pointer; font-size: 13px; }
.ticket-no-link:hover { text-decoration: underline; }
.type-tag { font-size: 12px; color: var(--color-text-secondary); }
.desc-text { color: var(--color-text-secondary); line-height: 1.5; }

/* Status badges */
.status-badge {
  display: inline-block; font-size: 11px; padding: 4px 12px; border-radius: 12px; font-weight: 500;
}
.status-pending { background: #fef3c7; color: #92400e; }
.status-processing { background: var(--color-primary-bg); color: #1d4ed8; }
.status-completed { background: #dcfce7; color: #16a34a; }
.status-failed { background: #fef2f2; color: #dc2626; }
</style>
