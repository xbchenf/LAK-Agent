<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTicket, claimTicket, processTicket } from '@/api/operator'
import type { TicketVO } from '@/api/operator'

const route = useRoute()
const router = useRouter()
const ticketNo = route.params.ticketNo as string

const ticket = ref<TicketVO | null>(null)
const loading = ref(false)
const handlerNotes = ref('')
const submitting = ref(false)

onMounted(() => load())

async function load() {
  loading.value = true
  try { ticket.value = await getTicket(ticketNo) }
  catch { ElMessage.error('工单不存在') }
  finally { loading.value = false }
}

async function doClaim() {
  try {
    await claimTicket(ticketNo)
    ElMessage.success('已接单')
    load()
  } catch { /* handled */ }
}

async function doSubmit() {
  if (!handlerNotes.value.trim()) { ElMessage.warning('请填写处理意见'); return }
  submitting.value = true
  try {
    await processTicket(ticketNo, handlerNotes.value)
    ElMessage.success('处理完成')
    router.push('/operator')
  } catch { /* handled */ }
  finally { submitting.value = false }
}

function statusBadge(s: string) {
  if (s === 'PENDING') return 'badge-pending'
  if (s === 'PROCESSING') return 'badge-processing'
  if (s === 'COMPLETED') return 'badge-completed'
  return 'badge-pending'
}

function statusText(s: string) {
  const map: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '已关闭' }
  return map[s] || s
}
</script>

<template>
  <div class="op-detail" v-loading="loading">
    <div class="topbar">
      <button class="back-btn" @click="router.push('/operator')">← 返回队列</button>
      <span class="ticket-label" v-if="ticket">工单处理 / <strong>{{ ticket.ticketNo }}</strong></span>
      <span v-if="ticket?.priority === 'URGENT'" class="urgent-tag">⚠ 紧急</span>
    </div>

    <template v-if="ticket">
      <div class="content-layout">
        <!-- Detail column -->
        <div class="detail-col">
          <div class="detail-card">
            <div class="detail-header">
              <h3>{{ ticket.ticketNo }}</h3>
              <span :class="['status-tag', statusBadge(ticket.status)]">{{ statusText(ticket.status) }}</span>
            </div>

            <div class="info-section">
              <div class="section-title">基本信息</div>
              <div class="info-grid">
                <div class="info-item"><span class="info-label">投诉类型</span><span>{{ ticket.complaintType }}</span></div>
                <div class="info-item"><span class="info-label">优先级</span><span :class="ticket.priority === 'URGENT' ? 'text-danger' : ''">{{ ticket.priority === 'URGENT' ? '🔴 紧急' : ticket.priority === 'LOW' ? '低' : '普通' }}</span></div>
                <div class="info-item"><span class="info-label">联系人</span><span>{{ ticket.contactName }}</span></div>
                <div class="info-item"><span class="info-label">联系电话</span><span>{{ ticket.contactPhone }}</span></div>
                <div class="info-item" v-if="ticket.assigneeId"><span class="info-label">处理人</span><span>{{ ticket.status === 'PROCESSING' ? '我' : '—' }}</span></div>
                <div class="info-item"><span class="info-label">创建时间</span><span>{{ ticket.createTime?.substring(0, 19) }}</span></div>
              </div>
            </div>

            <div class="info-section">
              <div class="section-title">投诉内容</div>
              <div class="desc-box">{{ ticket.description }}</div>
            </div>

            <div v-if="ticket.handlerNotes" class="info-section">
              <div class="section-title">处理意见</div>
              <div class="desc-box">{{ ticket.handlerNotes }}</div>
            </div>
          </div>
        </div>

        <!-- Action area: below detail -->
        <div class="action-area">
          <!-- Pending: claim -->
          <div v-if="ticket.status === 'PENDING'" class="action-card">
            <div class="card-title">📋 操作</div>
            <p style="font-size:13px;color:var(--color-text-secondary);margin-bottom:16px">此工单尚未被认领，接单后开始处理</p>
            <button class="btn btn-primary btn-full" @click="doClaim">接单处理</button>
          </div>

          <!-- Processing: complete -->
          <div v-if="ticket.status === 'PROCESSING'" class="action-card">
            <div class="card-title">📝 处理操作</div>
            <label class="field-label">处理意见</label>
            <textarea v-model="handlerNotes" class="notes-input" placeholder="请输入处理意见、调查结果或回复内容…" rows="6"></textarea>
            <div class="btn-row">
              <button class="btn btn-outline" @click="router.push('/operator')">取消</button>
              <button class="btn btn-primary" :disabled="submitting" @click="doSubmit">{{ submitting ? '提交中…' : '✅ 提交处理' }}</button>
            </div>
          </div>

          <!-- Completed: read-only -->
          <div v-if="ticket.status === 'COMPLETED'" class="action-card">
            <div class="card-title">✅ 已办结</div>
            <p style="font-size:13px;color:var(--color-text-secondary)">该工单已处理完成</p>
            <div v-if="ticket.handledAt" style="font-size:12px;color:var(--color-text-muted);margin-top:8px">
              办结时间：{{ ticket.handledAt?.substring(0, 19) }}
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.op-detail { padding: 24px; height: 100%; overflow-y: auto; }
.topbar { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.back-btn {
  padding: 6px 16px; border-radius: 20px; border: 1px solid var(--color-border);
  background: var(--color-bg-white); color: var(--color-text-secondary);
  font-size: 12px; cursor: pointer; transition: all .15s; font-family: inherit;
}
.back-btn:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.ticket-label { font-size: 14px; color: var(--color-text-secondary); }
.ticket-label strong { color: var(--color-text); }
.urgent-tag { font-size: 11px; color: var(--color-danger); font-weight: 500; }

.content-layout { display: flex; flex-direction: column; gap: 20px; }
.detail-col { }
.action-area { }

.detail-card {
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border);
  padding: 28px 30px;
}
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.detail-header h3 { margin: 0; font-size: 18px; font-weight: 700; }

.status-tag { font-size: 12px; padding: 5px 14px; border-radius: 14px; font-weight: 500; }
.badge-pending { background: #fef3c7; color: #92400e; }
.badge-processing { background: var(--color-primary-bg); color: #1d4ed8; }
.badge-completed { background: #dcfce7; color: #16a34a; }

.info-section { margin-bottom: 20px; }
.section-title { font-size: 12px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 1px; font-weight: 600; margin-bottom: 10px; }
.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: 12px; color: var(--color-text-secondary); }
.info-item span:last-child { font-size: 14px; color: var(--color-text); }
.text-danger { color: var(--color-danger) !important; }
.desc-box {
  background: var(--color-bg); border-radius: var(--radius); padding: 14px 16px;
  font-size: 14px; line-height: 1.8; color: var(--color-text-secondary);
}

/* Action card */
.action-card {
  background: var(--color-bg-white); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm); border: 1px solid var(--color-border);
  padding: 22px 24px;
}
.card-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.field-label { display: block; font-size: 13px; font-weight: 500; color: var(--color-text); margin-bottom: 6px; }
.notes-input {
  width: 100%; padding: 10px 14px; border: 1px solid var(--color-border); border-radius: var(--radius);
  font-size: 13px; font-family: inherit; outline: none; resize: vertical; min-height: 100px;
  color: var(--color-text);
}
.notes-input:focus { border-color: var(--color-primary-light); }
.btn-row { display: flex; gap: 10px; margin-top: 16px; }
.btn {
  padding: 10px 18px; border-radius: var(--radius); font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .15s; font-family: inherit; border: 1px solid var(--color-border);
  background: var(--color-bg-white); color: var(--color-text-secondary);
}
.btn:hover { border-color: var(--color-primary-light); color: var(--color-primary); }
.btn-primary { background: var(--color-primary); color: #fff; border: none; flex: 1; }
.btn-primary:hover { background: var(--color-primary-hover); color: #fff; }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }
.btn-outline { flex: 1; }
.btn-full { width: 100%; }
</style>
