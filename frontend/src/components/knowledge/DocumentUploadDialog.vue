<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '@/api/knowledge'
import { DocTypeLabels } from '@/types/knowledge'

const emit = defineEmits<{ (e: 'uploaded'): void }>()

const visible = ref(false)
const uploading = ref(false)
const form = ref({
  docType: 'POLICY',
  effectiveDate: '',
  expireDate: '',
})
const file = ref<File | null>(null)

function open() { visible.value = true; file.value = null }
defineExpose({ open })

const docTypes = Object.entries(DocTypeLabels).map(([value, label]) => ({ value, label }))

function fmtDate(d: string | Date | null): string {
  if (!d) return ''
  const dt = typeof d === 'string' ? new Date(d) : d
  return dt.toISOString().slice(0, 10)
}

async function handleUpload() {
  if (!file.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('docType', form.value.docType)
    if (form.value.effectiveDate) fd.append('effectiveDate', fmtDate(form.value.effectiveDate))
    if (form.value.expireDate) fd.append('expireDate', fmtDate(form.value.expireDate))
    await uploadDocument(fd)
    ElMessage.success('上传成功')
    visible.value = false
    emit('uploaded')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] || null
}
</script>

<template>
  <el-dialog v-model="visible" title="上传文档" width="480px" @closed="file = null">
    <el-form label-width="80px">
      <el-form-item label="文件">
        <input type="file" accept=".txt,.pdf,.docx" @change="handleFileChange" />
        <div style="color:#999;font-size:12px">支持 .txt .pdf .docx，≤20MB</div>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.docType" style="width:100%">
          <el-option v-for="dt in docTypes" :key="dt.value" :label="dt.label" :value="dt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期">
        <el-date-picker v-model="form.effectiveDate" type="date" placeholder="选填" style="width:100%" />
      </el-form-item>
      <el-form-item label="过期日期">
        <el-date-picker v-model="form.expireDate" type="date" placeholder="选填" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
    </template>
  </el-dialog>
</template>
