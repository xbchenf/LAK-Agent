<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs, getAuditLog } from '@/api/audit'
import { StatusLabels, StatusColors } from '@/types/audit'
import type { AuditLogVO, AuditLogQuery } from '@/types/audit'

// ===== 搜索条件 =====
const query = reactive<AuditLogQuery>({
  page: 1,
  size: 20,
})

const dateRange = ref<[string, string] | null>(null)
const months = ref<string[]>([])

// ===== 列表数据 =====
const loading = ref(false)
const records = ref<AuditLogVO[]>([])
const total = ref(0)

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AuditLogVO | null>(null)

// ===== 初始化 =====
onMounted(() => {
  // 生成最近 12 个月选项
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const m = String(d.getFullYear()) + String(d.getMonth() + 1).padStart(2, '0')
    months.value.push(m)
  }
  fetchList()
})

// ===== 查询 =====
async function fetchList() {
  loading.value = true
  try {
    const q = { ...query }
    if (dateRange.value) {
      q.startDate = dateRange.value[0]
      q.endDate = dateRange.value[1]
    } else {
      q.startDate = undefined
      q.endDate = undefined
    }
    const res = await listAuditLogs(q)
    records.value = res.records
    total.value = res.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  dateRange.value = null
  query.status = undefined
  query.keyword = undefined
  query.month = undefined
  query.page = 1
  fetchList()
}

function handlePageChange(page: number) {
  query.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchList()
}

// ===== 详情 =====
async function openDetail(row: AuditLogVO) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const m = query.month || months.value[0]  // 传递当前查询月份
    detail.value = await getAuditLog(row.id, m)
  } catch {
    // handled by interceptor
  } finally {
    detailLoading.value = false
  }
}

// ===== 格式化工具 =====
function fmtTime(t: string) {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 19)
}

function fmtJson(raw: string | null | undefined): string {
  if (!raw) return '--'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function fmtMonthLabel(m: string) {
  return m.substring(0, 4) + '-' + m.substring(4, 6)
}

function truncate(s: string | null, n: number) {
  if (!s) return '--'
  return s.length > n ? s.substring(0, n) + '...' : s
}
</script>

<template>
  <div class="audit-page">
    <div class="page-header">
      <h3>📋 操作审计</h3>
      <span class="hint">全量操作日志，按月分表，留存六个月</span>
    </div>

    <!-- 搜索栏 -->
    <el-card style="margin-bottom:16px">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:240px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAIL" />
            <el-option label="降级" value="FALLBACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="月份">
          <el-select v-model="query.month" placeholder="当前月" clearable style="width:130px">
            <el-option
              v-for="m in months" :key="m"
              :label="fmtMonthLabel(m)" :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="TraceId / URI" clearable style="width:220px"
            @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card>
      <el-table :data="records" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="operation" label="操作类型" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="StatusColors[row.status] || 'info'" size="small">
              {{ StatusLabels[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="耗时" width="80">
          <template #default="{ row }">{{ row.latencyMs != null ? row.latencyMs + 'ms' : '--' }}</template>
        </el-table-column>
        <el-table-column prop="traceId" label="追踪ID" min-width="160">
          <template #default="{ row }">
            <el-tooltip :content="row.traceId" placement="top" :show-after="300">
              <span style="font-family:monospace;font-size:12px;cursor:default">
                {{ truncate(row.traceId, 16) }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px;text-align:right">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="审计日志详情" width="720px" destroy-on-close>
      <div v-loading="detailLoading" style="max-height:60vh;overflow-y:auto">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="追踪ID">{{ detail.traceId }}</el-descriptions-item>
            <el-descriptions-item label="用户">{{ detail.username || '--' }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ detail.userId || '--' }}</el-descriptions-item>
            <el-descriptions-item label="操作类型">{{ detail.operation || '--' }}</el-descriptions-item>
            <el-descriptions-item label="请求URI">{{ detail.requestUri || '--' }}</el-descriptions-item>
            <el-descriptions-item label="会话ID">{{ detail.sessionId || '--' }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ fmtTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="StatusColors[detail.status] || 'info'" size="small">
                {{ StatusLabels[detail.status] || detail.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="耗时">{{ detail.latencyMs != null ? detail.latencyMs + 'ms' : '--' }}</el-descriptions-item>
            <el-descriptions-item label="意图类型">{{ detail.intentType || '--' }}</el-descriptions-item>
            <el-descriptions-item label="置信度">{{ detail.confidence != null ? detail.confidence : '--' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.errorMessage" style="margin-top:12px">
            <h4 style="color:#F56C6C">错误信息</h4>
            <pre class="json-block error-block">{{ detail.errorMessage }}</pre>
          </div>

          <div v-if="detail.requestBody" style="margin-top:12px">
            <h4>请求内容</h4>
            <pre class="json-block">{{ fmtJson(detail.requestBody) }}</pre>
          </div>

          <div v-if="detail.responseBody" style="margin-top:12px">
            <h4>响应内容</h4>
            <pre class="json-block">{{ fmtJson(detail.responseBody) }}</pre>
          </div>

          <div v-if="detail.modelParams" style="margin-top:12px">
            <h4>模型参数</h4>
            <pre class="json-block">{{ fmtJson(detail.modelParams) }}</pre>
          </div>

          <div v-if="detail.modelResponse" style="margin-top:12px">
            <h4>模型返回</h4>
            <pre class="json-block">{{ fmtJson(detail.modelResponse) }}</pre>
          </div>

          <div v-if="detail.retrievalFragments" style="margin-top:12px">
            <h4>检索召回片段</h4>
            <pre class="json-block">{{ fmtJson(detail.retrievalFragments) }}</pre>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-page { max-width: 1100px; margin: 0 auto; padding: 24px; }

.page-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.page-header h3 { margin: 0; font-size: 20px; }
.hint { color: #909399; font-size: 13px; }

.json-block {
  background: #f5f7fa; border-radius: 6px; padding: 12px;
  font-size: 12px; line-height: 1.6; max-height: 260px; overflow: auto;
  white-space: pre-wrap; word-break: break-all; margin: 0;
}
.error-block { background: #fef0f0; color: #F56C6C; }

:deep(.el-form-item) { margin-bottom: 0; }
</style>
