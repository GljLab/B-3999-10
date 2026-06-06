<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="flex justify-between items-center mb-6">
      <div>
        <el-button type="primary" text @click="goBack">← 返回产品列表</el-button>
        <h2 class="text-xl font-bold text-gray-800 mt-2">📦 批次管理 - {{ productName }}</h2>
      </div>
      <el-button type="primary" @click="openAddDialog" round>+ 新增批次</el-button>
    </div>

    <el-table :data="batches" class="w-full flex-1" stripe :loading="loading" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="batchNo" label="批次编号" width="150">
        <template #default="{row}">
          <span class="font-mono font-bold">{{ row.batchNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="规格" width="120">
        <template #default="{row}">
          <el-tag v-if="row.specName" type="success">{{ row.specName }}</el-tag>
          <el-tag v-else type="info">未指定</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="productionDate" label="生产日期" width="120" />
      <el-table-column prop="qualityGrade" label="质量等级" width="100">
        <template #default="{row}">
          <el-tag :type="getQualityTagType(row.qualityGrade)">{{ row.qualityGrade }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="批次备注" show-overflow-tooltip />
      <el-table-column prop="currentTraceCode" label="溯源码" width="180">
        <template #default="{row}">
          <span v-if="row.hasTracingCode" class="font-mono text-green-600">{{ row.currentTraceCode }}</span>
          <el-tag v-else type="info" effect="plain">未生成</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{row}">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{row}">
          <el-button size="small" type="success" plain @click="genTraceCode(row)">
            {{ row.hasTracingCode ? '重新生成' : '生成溯源码' }}
          </el-button>
          <el-button size="small" type="primary" plain @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="deleteBatch(row)" :disabled="row.hasTracingCode">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && batches.length === 0" description="暂无批次，请点击右上角新增批次" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑批次' : '新增批次'" width="500px">
      <el-form :model="batchForm" :rules="formRules" ref="batchFormRef" label-position="top">
        <el-form-item label="产品规格">
          <el-select v-model="batchForm.specId" placeholder="请选择产品规格" style="width:100%" clearable>
            <el-option v-for="spec in specs" :key="spec.id" :label="`${spec.specName} (${spec.weight}, ¥${spec.suggestedPrice})`" :value="spec.id" />
            <el-option :value="null" label="不选择规格" />
          </el-select>
          <div v-if="specs.length === 0" class="text-xs text-orange-500 mt-1">
            ⚠️ 该产品尚未定义规格，请先在产品管理中定义规格
          </div>
        </el-form-item>
        <el-form-item label="批次编号" prop="batchNo">
          <el-input v-model="batchForm.batchNo" placeholder="如: 20231015-A" maxlength="50" />
        </el-form-item>
        <el-form-item label="生产日期" prop="productionDate">
          <el-date-picker v-model="batchForm.productionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled-date="disabledDate" />
        </el-form-item>
        <el-form-item label="质量等级" prop="qualityGrade">
          <el-select v-model="batchForm.qualityGrade" placeholder="请选择质量等级" style="width:100%">
            <el-option label="特级" value="特级" />
            <el-option label="一级" value="一级" />
            <el-option label="二级" value="二级" />
            <el-option label="普通" value="普通" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次备注" prop="remark">
          <el-input type="textarea" v-model="batchForm.remark" placeholder="如: 头茬果、霜降后采摘等" maxlength="500" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const productId = computed(() => Number(route.params.productId))
const productName = ref('')
const batches = ref([])
const specs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const batchFormRef = ref(null)
const batchForm = ref({
  id: null,
  productId: null,
  specId: null,
  batchNo: '',
  productionDate: '',
  qualityGrade: '',
  remark: ''
})

const formRules = {
  batchNo: [{ required: true, message: '请输入批次编号', trigger: 'blur' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }],
  qualityGrade: [{ required: true, message: '请选择质量等级', trigger: 'change' }]
}

const loadSpecs = async () => {
  try {
    const res = await api.get(`/farmer/spec/list/${productId.value}`)
    specs.value = res.data
  } catch (e) {
    specs.value = []
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.get(`/farmer/batch/list/${productId.value}`)
    batches.value = res.data
    if (batches.value.length > 0) {
      productName.value = batches.value[0].productName
    } else {
      const productRes = await api.get(`/farmer/product/${productId.value}`)
      productName.value = productRes.data.productName
    }
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push({ name: 'Products' })
}

const openAddDialog = () => {
  isEdit.value = false
  batchForm.value = {
    id: null,
    productId: productId.value,
    specId: null,
    batchNo: '',
    productionDate: '',
    qualityGrade: '',
    remark: ''
  }
  loadSpecs()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  batchForm.value = {
    id: row.id,
    productId: productId.value,
    specId: row.specId || null,
    batchNo: row.batchNo,
    productionDate: row.productionDate,
    qualityGrade: row.qualityGrade,
    remark: row.remark || ''
  }
  loadSpecs()
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!batchFormRef.value) return

  await batchFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      if (isEdit.value) {
        await api.put('/farmer/batch', batchForm.value)
        ElMessage.success('批次更新成功')
      } else {
        await api.post('/farmer/batch', batchForm.value)
        ElMessage.success('批次创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {}
  })
}

const deleteBatch = async (row) => {
  if (row.hasTracingCode) {
    ElMessage.warning('该批次已生成溯源码，无法删除')
    return
  }

  await ElMessageBox.confirm('确定要删除该批次吗？', '确认删除')
  try {
    await api.delete(`/farmer/batch/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

const genTraceCode = async (row) => {
  const confirmMsg = row.hasTracingCode
    ? '确定要重新生成溯源码吗？原溯源码将作废。'
    : '确定要为该批次生成溯源码吗？'

  await ElMessageBox.confirm(confirmMsg, '确认生成')
  try {
    const res = await api.post(`/farmer/batch/trace_code/${row.id}`)
    ElMessageBox.alert(
      `您的溯源码为：<br/><strong class="text-xl text-green-600">${res.data.traceCode}</strong><br/>请打印并粘贴至包装盒上。`,
      '生成溯源码成功',
      { dangerouslyUseHTMLString: true }
    )
    loadData()
  } catch (e) {}
}

const getQualityTagType = (grade) => {
  const typeMap = {
    '特级': 'danger',
    '一级': 'warning',
    '二级': 'success',
    '普通': 'info'
  }
  return typeMap[grade] || 'info'
}

const disabledDate = (time) => {
  return time.getTime() > Date.now()
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  if (!productId.value) {
    ElMessage.error('参数错误')
    router.push({ name: 'Products' })
    return
  }
  loadData()
})
</script>
