<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPending, listMyProcessing, listMyAll, listAllTickets, claimTicket, listWaitingSessions, takeoverSession, listMySessions } from '@/api/operator'
import type { TicketVO, WaitingSessionVO } from '@/api/operator'

const router = useRouter()

const pendingTickets = ref<TicketVO[]>([])
const myProcessing = ref<TicketVO[]>([])
const allTickets = ref<TicketVO[]>([])
const myAll = ref<TicketVO[]>([])
const waitingSessions = ref<WaitingSessionVO[]>([])
const mySessions = ref<WaitingSessionVO[]>([])
const loading = ref(false)
const claiming = ref<string | null>(null)
const ticketTab = ref('all')

let timer: number | null = null

onMounted(() => {
  load()
  timer = window.setInterval(loadWaiting, 5000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })

async function load() {
  loading.value = true
  try {
    const [pending, processing, all, aTickets] = await Promise.all([
      listPending().catch(() => [] as TicketVO[]),
      listMyProcessing().catch(() => [] as TicketVO[]),
      listMyAll().catch(() => [] as TicketVO[]),
      listAllTickets().catch(() => [] as TicketVO[]),
    ])
    pendingTickets.value = pending
    myProcessing.value = processing
    myAll.value = all
    allTickets.value = aTickets
  } finally { loading.value = false }
  loadWaiting()
}

async function loadWaiting() {
  try {
    waitingSessions.value = await listWaitingSessions()
    mySessions.value = await listMySessions()
  } catch { /* ignore */ }
}

async function doTakeover(sessionId: string) {
  try {
    await takeoverSession(sessionId)
    router.push(`/operator/chat/${sessionId}`)
  } catch (e: any) {
    const msg = e?.response?.data?.message || ''
    if (msg.includes('不在等待队列')) {
      router.push(`/operator/chat/${sessionId}`)
    } else {
      ElMessage.error(msg || '接入失败')
    }
    loadWaiting()
  }
}

async function doClaim(ticketNo: string) {
  claiming.value = ticketNo
  try {
    await claimTicket(ticketNo)
    ElMessage.success('已接单')
    load()
  } catch { /* handled */ }
  finally { claiming.value = null }
}

function goDetail(ticketNo: string) {
  router.push(`/operator/tickets/${ticketNo}`)
}

const filteredTickets = computed(() => {
  const all = allTickets.value
  if (ticketTab.value === 'pending') return all.filter(t => t.status === 'PENDING')
  if (ticketTab.value === 'processing') return all.filter(t => t.status === 'PROCESSING')
  if (ticketTab.value === 'done') return all.filter(t => t.status === 'COMPLETED')
  return all
})

const statusText = (s: string) => ({ PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已办结', FAILED: '已关闭' } as Record<string, string>)[s] || s
const statusBadge = (s: string) => ({ PENDING: 'tag-pending', PROCESSING: 'tag-processing', COMPLETED: 'tag-done', FAILED: 'tag-failed' } as Record<string, string>)[s] || 'tag-pending'

const today = new Date().toISOString().substring(0, 10)  // yyyy-MM-dd
const todayCount = (tickets: TicketVO[], status?: string) => {
  return tickets.filter(t => {
    const match = status ? t.status === status : true
    return match && t.createTime?.startsWith(today)
  }).length
}

</script>

<template>
  <div class="op-dashboard">
    <!-- Stats Row 1 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-top">
          <span class="stat-label">待处理工单</span>
          <span class="stat-icon icon-orange">📋</span>
        </div>
        <div class="stat-value">{{ pendingTickets.length }}</div>
        <div class="stat-sub">今日新增 <b>{{ todayCount(pendingTickets) }}</b></div>
      </div>
      <div class="stat-card">
        <div class="stat-top">
          <span class="stat-label">我已认领</span>
          <span class="stat-icon icon-blue">🔄</span>
        </div>
        <div class="stat-value">{{ myAll.length }}</div>
        <div class="stat-sub">今日认领 <b>{{ todayCount(myAll) }}</b></div>
      </div>
      <div class="stat-card">
        <div class="stat-top">
          <span class="stat-label">今日已办结</span>
          <span class="stat-icon icon-green">✅</span>
        </div>
        <div class="stat-value">{{ todayCount(myAll, 'COMPLETED') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-top">
          <span class="stat-label">等待人工接入</span>
          <span class="stat-icon icon-purple">👤</span>
        </div>
        <div class="stat-value">{{ waitingSessions.length }}</div>
      </div>
    </div>

    <!-- Sessions Row -->
    <div class="sessions-row">
      <div class="panel session-half">
        <div class="panel-header">
          💬 等待人工接入
          <span class="count">({{ waitingSessions.length }})</span>
        </div>
        <div class="panel-body">
          <div v-if="waitingSessions.length === 0" class="empty-state">暂无数据</div>
          <div v-for="s in waitingSessions" :key="s.sessionId" class="session-item">
            <div class="s-avatar">{{ (s.lastMessage || '?').charAt(0) }}</div>
            <div class="s-body">
              <div class="s-header">
                <span class="s-id">{{ s.sessionId?.substring(0, 12) }}...</span>
                <span class="s-tag s-tag-wait">排队中</span>
              </div>
              <div class="s-desc">{{ s.summary?.coreQuestion || s.lastMessage || '(无消息)' }}</div>
              <div class="s-time">{{ s.createTime?.substring(11, 19) || '' }} · {{ s.transferReason }}</div>
            </div>
            <button class="takeover-btn" @click="doTakeover(s.sessionId)">接入</button>
          </div>
        </div>
      </div>

      <div class="panel session-half">
        <div class="panel-header">
          📌 我的会话
          <span class="count">({{ mySessions.length }})</span>
        </div>
        <div class="panel-body">
          <div v-if="mySessions.length === 0" class="empty-state">暂无数据</div>
          <div v-for="s in mySessions" :key="s.sessionId" class="session-item" style="cursor:pointer" @click="router.push(`/operator/chat/${s.sessionId}`)">
            <div class="s-avatar">{{ (s.lastMessage || '?').charAt(0) }}</div>
            <div class="s-body">
              <div class="s-header">
                <span class="s-id">{{ s.sessionId?.substring(0, 12) }}...</span>
                <span class="s-tag s-tag-active">进行中</span>
              </div>
              <div class="s-desc">{{ s.lastMessage || '(无消息)' }}</div>
              <div class="s-time">{{ s.createTime?.substring(11, 19) || '' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Ticket Table -->
    <div class="panel ticket-full">
      <div class="panel-header">
        📋 工单列表
        <span class="count">({{ filteredTickets.length }}条)</span>
        <span class="spacer"></span>
        <button :class="['tab-btn', { active: ticketTab === 'all' }]" @click="ticketTab = 'all'">全部</button>
        <button :class="['tab-btn', { active: ticketTab === 'pending' }]" @click="ticketTab = 'pending'">待处理</button>
        <button :class="['tab-btn', { active: ticketTab === 'processing' }]" @click="ticketTab = 'processing'">处理中</button>
        <button :class="['tab-btn', { active: ticketTab === 'done' }]" @click="ticketTab = 'done'">已办结</button>
      </div>
      <div class="panel-body">
        <table v-if="filteredTickets.length > 0" class="ticket-table">
          <thead>
            <tr>
              <th style="width:200px">工单编号</th>
              <th style="width:90px">类型</th>
              <th>内容描述</th>
              <th style="width:72px">状态</th>
              <th style="width:140px">创建时间</th>
              <th style="width:80px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in filteredTickets" :key="t.ticketNo">
              <td><span class="ticket-link" @click="goDetail(t.ticketNo)">{{ t.ticketNo }}</span></td>
              <td>{{ t.complaintType }}</td>
              <td><span class="desc-cell">{{ t.description }}</span></td>
              <td><span :class="['status-tag', statusBadge(t.status)]">{{ statusText(t.status) }}</span></td>
              <td>{{ t.createTime?.substring(5, 16) || '' }}</td>
              <td>
                <button v-if="t.status === 'PENDING'" class="btn-sm btn-claim" :disabled="claiming === t.ticketNo" @click="doClaim(t.ticketNo)">{{ claiming === t.ticketNo ? '...' : '接单' }}</button>
                <button v-else-if="t.status === 'PROCESSING'" class="btn-sm btn-outline-sm" @click="goDetail(t.ticketNo)">处理</button>
                <span v-else class="status-tag tag-done">已办结</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">暂无工单</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.op-dashboard { padding: 24px; display: flex; flex-direction: column; gap: 20px; }

/* Stats Row 1 */
.stats-row { display: grid; grid-template-columns: repeat(4,1fr); gap: 16px; flex-shrink: 0 }
.stat-card { background: var(--color-bg-white); border-radius: var(--radius-lg); padding: 20px 22px; box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); transition: all .2s }
.stat-card:hover { box-shadow: var(--shadow-md); transform: translateY(-1px) }
.stat-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px }
.stat-label { font-size: 13px; color: var(--color-text-secondary); font-weight: 500 }
.stat-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px }
.stat-value { font-size: 30px; font-weight: 700; letter-spacing: -.5px; color: var(--color-text) }
.stat-bar { height: 4px; border-radius: 2px; margin-top: 10px; background: var(--color-bg-hover) }
.stat-bar-fill { height: 100%; border-radius: 2px; transition: width .3s }
.icon-blue{background:var(--color-primary-bg);color:var(--color-primary)}
.icon-orange{background:#fff7ed;color:var(--color-warning)}
.icon-green{background:#dcfce7;color:var(--color-success)}
.icon-purple{background:#f5f0ff;color:#7c3aed}

/* Sessions Row */
.sessions-row { display: flex; gap: 16px; flex-shrink: 0 }
.session-half { flex: 1; max-height: 260px }

/* Panels */
.panel { background: var(--color-bg-white); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); display: flex; flex-direction: column }
.ticket-full { flex: 1; min-height: 300px; overflow: hidden }
.panel-header { padding: 12px 16px; border-bottom: 1px solid var(--color-border); font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 6px; flex-shrink: 0 }
.panel-header .count { font-size: 12px; color: var(--color-text-secondary); font-weight: 400 }
.panel-header .spacer { flex: 1 }
.tab-btn { padding: 3px 10px; border-radius: 12px; font-size: 11px; cursor: pointer; border: 1px solid var(--color-border); background: var(--color-bg-white); color: var(--color-text-secondary); transition: all .15s; font-family: inherit }
.tab-btn:hover { border-color: var(--color-primary-light); color: var(--color-primary) }
.tab-btn.active { background: var(--color-primary-bg); color: var(--color-primary); border-color: var(--color-primary) }
.panel-body { flex: 1; overflow-y: auto }

/* Ticket Table */
.ticket-table { width: 100%; border-collapse: collapse }
.ticket-table th { padding: 10px 14px; text-align: left; font-size: 11px; font-weight: 600; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: .5px; background: var(--color-bg); border-bottom: 1px solid var(--color-border) }
.ticket-table td { padding: 12px 14px; font-size: 13px; border-bottom: 1px solid var(--color-border-light); vertical-align: middle }
.ticket-table tr:hover td { background: var(--color-bg) }
.ticket-link { color: var(--color-primary); font-weight: 600; cursor: pointer }
.ticket-link:hover { text-decoration: underline }
.desc-cell { max-width: 280px; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; color: var(--color-text-secondary) }
.status-tag { font-size: 11px; padding: 3px 10px; border-radius: 10px; font-weight: 500; white-space: nowrap }
.tag-pending { background: #fef3c7; color: #92400e }
.tag-processing { background: var(--color-primary-bg); color: #1d4ed8 }
.tag-done { background: #dcfce7; color: var(--color-success) }
.tag-failed { background: var(--color-bg-hover); color: var(--color-text-muted) }
.btn-sm { padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: 500; cursor: pointer; border: none; font-family: inherit; white-space: nowrap; transition: all .15s }
.btn-claim { background: var(--color-primary); color: #fff }
.btn-claim:hover:not(:disabled) { background: var(--color-primary-light) }
.btn-claim:disabled { opacity: .5; cursor: not-allowed }
.btn-outline-sm { background: var(--color-bg-white); color: var(--color-primary); border: 1px solid var(--color-primary) }
.btn-outline-sm:hover { background: var(--color-primary-bg) }

/* Session Items */
.session-item { display: flex; gap: 10px; padding: 12px 16px; border-bottom: 1px solid var(--color-border-light); align-items: center; cursor: default; transition: all .15s }
.session-item:hover { background: var(--color-bg) }
.s-avatar { width: 32px; height: 32px; border-radius: 50%; background: var(--color-bg-hover); display: flex; align-items: center; justify-content: center; font-size: 13px; color: var(--color-text-secondary); flex-shrink: 0 }
.s-body { flex: 1; min-width: 0 }
.s-header { display: flex; justify-content: space-between; margin-bottom: 2px }
.s-id { font-size: 12px; font-weight: 600; color: var(--color-primary) }
.s-tag { font-size: 10px; padding: 1px 8px; border-radius: 10px; font-weight: 500 }
.s-tag-wait { background: #fef3c7; color: #92400e }
.s-tag-active { background: var(--color-primary-bg); color: var(--color-primary) }
.s-desc { font-size: 12px; color: var(--color-text-secondary); display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden }
.s-time { font-size: 10px; color: var(--color-text-muted); margin-top: 2px }
.takeover-btn { padding: 5px 14px; border-radius: 14px; font-size: 11px; font-weight: 500; cursor: pointer; border: none; background: var(--color-primary); color: #fff; white-space: nowrap; font-family: inherit }
.takeover-btn:hover { background: var(--color-primary-light) }

.empty { padding: 32px; text-align: center; color: var(--color-text-muted); font-size: 13px }

@media(max-width:1200px) {
  .stats-row { grid-template-columns: repeat(2,1fr) }
  .breakdown-row { grid-template-columns: repeat(2,1fr) }
}
</style>
