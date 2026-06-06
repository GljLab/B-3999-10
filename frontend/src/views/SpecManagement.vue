<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="mb-4">
      <el-breadcrumb separator-class="el-icon-arrow-right">
        <el-breadcrumb-item :to="{ name: 'Products' }">产品管理</el-breadcrumb-item>
        <el-breadcrumb-item>规格管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ productName }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="flex justify-between items-center mb-6">
      <div>
        <el-button type="primary" text @click="goBack">← 返回产品列表</el-button>
        <h2 class="text-xl font-bold text-gray-800 mt-2">📦 规格管理 - {{ productName }}</h2>
      </div>
      <el-button type="primary" @click="openAddDialog" round>+ 新增规格</el-button>
    </div>

    <el-table :data="specs" class="w-full flex-1" stripe :loading="loading" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="specName" label="规格名称" />
      <el-table-column prop="weight" label="重量" width="120" />
      <el-table-column prop="suggestedPrice" label="市场建议价(元)" width="150">
        <template #default="{row}">
          <span class="text-orange-500 font-bold">¥{{ row.suggestedPrice }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{row}">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{row}">
          <el-button size="small" type="primary" plain @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="deleteSpec(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && specs.length === 0" description="暂无规格，请点击右上角新增规格" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规格' : '新增规格'" width="500px">
      <el-form :model="specForm" :rules="formRules" ref="specFormRef" label-position="top">
        <el-form-item label="规格名称" prop="specName">
          <el-input v-model="specForm.specName" placeholder="如: 大果装、中果装" maxlength="50" />
        </el-form-item>
        <el-form-item label="单位重量" prop="weight">
          <el-input v-model="specForm.weight" placeholder="如: 500g、400g" maxlength="50" />
        </el-form-item>
        <el-form-item label="市场建议价(元)" prop="suggestedPrice">
          <el-input-number v-model="specForm.suggestedPrice" :min="0.01" :step="0.1" :precision="2" style="width: 100%" />
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
const specs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const specFormRef = ref(null)
const specForm = ref({
  id: null,
  productId: null,
  specName: '',
  weight: '',
  suggestedPrice: 0
})

const formRules = {
  specName: [{ required: true, message: '请输入规格名称', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入单位重量', trigger: 'blur' }],
  suggestedPrice: [{ required: true, message: '请输入市场建议价', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.get(`/farmer/spec/list/${productId.value}`)
    specs.value = res.data
    if (specs.value.length > 0) {
      const productRes = await api.get(`/farmer/product/${productId.value}`)
      productName.value = productRes.data.productName
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
  specForm.value = {
    id: null,
    productId: productId.value,
    specName: '',
    weight: '',
    suggestedPrice: 0
  }
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  specForm.value = {
    id: row.id,
    productId: productId.value,
    specName: row.specName,
    weight: row.weight,
    suggestedPrice: row.suggestedPrice
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!specFormRef.value) return

  await specFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      if (isEdit.value) {
        await api.put('/farmer/spec', specForm.value)
        ElMessage.success('规格更新成功')
      } else {
        await api.post('/farmer/spec', specForm.value)
        ElMessage.success('规格创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {}
  })
}

const deleteSpec = async (row) => {
  await ElMessageBox.confirm('确定要删除该规格吗？', '确认删除')
  try {
    await api.delete(`/farmer/spec/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
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
