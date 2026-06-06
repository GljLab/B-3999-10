<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800">🌽 我的农产品管理</h2>
      <el-button type="primary" @click="openAddDialog" round>+ 增加新产品</el-button>
    </div>

    <el-table :data="products" class="w-full flex-1" stripe :loading="loading" v-loading="loading">
      <el-table-column label="产品图片" width="100">
        <template #default="{row}">
          <div class="w-20 h-20 overflow-hidden rounded-lg border border-gray-200 bg-gray-100 flex items-center justify-center">
            <img v-if="row.imageUrl" :src="getImageUrl(row.imageUrl)" class="w-full h-full object-cover" />
            <span v-else class="text-gray-400 text-2xl">🖼️</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{row}"><el-tag>{{row.category}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="origin" label="产地" />
      <el-table-column prop="harvestDate" label="生产/采摘日" width="120" />
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="{row}">
          <el-button size="small" type="success" plain @click="goToSpecManage(row.id)">规格管理</el-button>
          <el-button size="small" type="primary" plain @click="goToBatchManage(row.id)">批次管理</el-button>
          <el-button size="small" type="warning" plain @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="deleteProduct(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑产品' : '登记新产品'" width="700px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-position="top">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品主图" required>
              <el-upload
                class="image-uploader"
                :show-file-list="false"
                :before-upload="beforeImageUpload"
                action="#"
                accept="image/jpeg,image/jpg,image/png"
              >
                <div v-if="form.imageUrl" class="image-preview">
                  <img :src="getImageUrl(form.imageUrl)" class="w-full h-48 object-cover rounded-lg border" />
                  <div class="image-preview-mask" @click.stop>
                    <el-button type="primary" size="small" @click="clearImage">重新上传</el-button>
                  </div>
                </div>
                <div v-else class="upload-placeholder">
                  <el-icon class="text-4xl"><Plus /></el-icon>
                  <div class="text-sm text-gray-500 mt-2">点击上传产品主图</div>
                  <div class="text-xs text-gray-400">支持 JPG、PNG，最大 5MB</div>
                </div>
              </el-upload>
            </el-form-item>
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" />
            </el-form-item>
            <el-form-item label="分类" prop="category">
              <el-input v-model="form.category" placeholder="如: 水果, 蔬菜" />
            </el-form-item>
            <el-form-item label="详细产地" prop="origin">
              <el-input v-model="form.origin" />
            </el-form-item>
            <el-form-item label="采摘日" prop="harvestDate">
              <el-date-picker v-model="form.harvestDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="产品描述">
          <div style="border: 1px solid #dcdfe6; border-radius: 4px;">
            <Toolbar
              style="border-bottom: 1px solid #dcdfe6"
              :editor="editorRef"
              :defaultConfig="toolbarConfig"
              mode="default"
            />
            <Editor
              style="height: 300px; overflow-y: hidden;"
              v-model="form.description"
              :defaultConfig="editorConfig"
              mode="default"
              @onCreated="handleCreated"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm" :disabled="!form.imageUrl">确定保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'

const router = useRouter()
const products = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const editorRef = shallowRef()
const form = ref({
  id: null,
  productName: '',
  category: '',
  origin: '',
  harvestDate: '',
  description: '',
  imageUrl: ''
})

const toolbarConfig = {
  excludeKeys: ['group-video', 'insertImage', 'insertVideo']
}

const editorConfig = {
  placeholder: '请输入产品描述...',
  MENU_CONF: {}
}

const formRules = {
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入产品分类', trigger: 'blur' }],
  origin: [{ required: true, message: '请输入产品产地', trigger: 'blur' }],
  harvestDate: [{ required: true, message: '请选择采摘日期', trigger: 'change' }]
}

const handleCreated = (editor) => {
  editorRef.value = editor
}

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
}

const beforeImageUpload = (file) => {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/jpg' || file.type === 'image/png'
  if (!isImage) {
    ElMessage.error('只支持 JPG、PNG 格式的图片')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  uploadImage(file)
  return false
}

const uploadImage = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await api.post('/farmer/product/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    form.value.imageUrl = res.data
    ElMessage.success('图片上传成功')
  } catch (e) {
    ElMessage.error('图片上传失败')
  }
}

const clearImage = () => {
  form.value.imageUrl = ''
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.get('/farmer/products')
    products.value = res.data
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  form.value = {
    id: null,
    productName: '',
    category: '',
    origin: '',
    harvestDate: '',
    description: '',
    imageUrl: ''
  }
  dialogVisible.value = true
}

const openEditDialog = async (row) => {
  isEdit.value = true
  const res = await api.get(`/farmer/product/${row.id}`)
  form.value = { ...res.data }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!form.value.imageUrl) {
    ElMessage.warning('请上传产品主图')
    return
  }
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value) {
        await api.put('/farmer/product', form.value)
        ElMessage.success('产品更新成功')
      } else {
        await api.post('/farmer/product', form.value)
        ElMessage.success('产品登记成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {}
  })
}

const goToSpecManage = (productId) => {
  router.push({ name: 'SpecManagement', params: { productId } })
}

const goToBatchManage = (productId) => {
  router.push({ name: 'BatchManagement', params: { productId } })
}

const deleteProduct = async (id) => {
  await ElMessageBox.confirm('确定要删除该产品及对应所有溯源数据吗?', '警告')
  await api.delete(`/farmer/product/${id}`)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.image-uploader :deep(.el-upload) {
  display: block;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 200px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-placeholder:hover {
  border-color: #409eff;
  background: #f0f7ff;
}

.image-preview {
  position: relative;
  width: 100%;
}

.image-preview-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.image-preview:hover .image-preview-mask {
  opacity: 1;
}
</style>
