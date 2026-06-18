<script setup lang="ts">
import { ref } from 'vue'

const ticketNo = ref('')
const showCreate = ref(false)
const form = ref({
  complaintType: '',
  contactName: '',
  contactPhone: '',
  description: '',
})

function submitTicket() {
  // TODO: call createTicket API
  showCreate.value = false
}

function searchTicket() {
  // TODO: call queryTicket API
}
</script>

<template>
  <div class="ticket-page">
    <h3>工单查询</h3>
    <div class="search-row">
      <el-input v-model="ticketNo" placeholder="输入工单编号" @keyup.enter="searchTicket" />
      <el-button type="primary" @click="searchTicket">查询</el-button>
      <el-button @click="showCreate = true">新建工单</el-button>
    </div>

    <el-dialog v-model="showCreate" title="新建工单" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="投诉类型">
          <el-select v-model="form.complaintType">
            <el-option label="执法建议" value="LAW_ENFORCEMENT" />
            <el-option label="服务投诉" value="SERVICE_COMPLAINT" />
            <el-option label="违纪举报" value="DISCIPLINE_REPORT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitTicket">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-page { max-width: 600px; margin: 0 auto; }
.search-row { display: flex; gap: 8px; margin-top: 16px; }
</style>
