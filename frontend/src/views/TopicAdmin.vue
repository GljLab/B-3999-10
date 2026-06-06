<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800">🏷️ 话题管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        + 新建话题
      </el-button>
    </div>

    <div class="flex gap-4 mb-6 flex-wrap items-end">
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">话题名称</span>
        <el-input v-model="keyword" placeholder="输入话题名称或描述搜索" clearable class="w-64" @keyup.enter="loadTopics(true)" />
      </div>
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">状态</span>
        <el-select v-model="statusFilter" placeholder="全部状态" class="w-32" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>
      <el-button type="primary" @click="loadTopics(true)">搜索</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </div>

    <el-table :data="topics" stripe class="w-full flex-1" :loading="loading" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图标" width="80">
        <template #default="{ row }">
          <span class="text-2xl">{{ row.icon || '🏷️' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="话题名称" width="150">
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ row.name }}</span>
            <el-tag v-if="row.isFeatured === 1" type="danger" size="small" effect="dark">精选</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
      <el-table-column label="数据统计" width="220">
        <template #default="{ row }">
          <div class="text-sm space-y-1">
            <div class="flex gap-4">
              <span class="text-gray-500">帖子：</span>
              <span class="font-medium">{{ row.postCount || 0 }}</span>
            </div>
            <div class="flex gap-4">
              <span class="text-gray-500">关注：</span>
              <span class="font-medium">{{ row.followCount || 0 }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="viewStats(row)">数据</el-button>
          <el-button size="small" type="primary" plain @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" plain @click="deleteTopic(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-end mt-4">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="20"
        :total="totalElements"
        layout="prev, pager, next, total"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑话题' : '新建话题'" width="600px" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top" :model="form" label-width="100px">
        <el-form-item label="话题名称" required>
          <el-input v-model="form.name" placeholder="请输入话题名称（2-20字）" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="话题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入话题描述，说明该话题的适用范围" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="话题图标">
          <div class="flex items-center gap-3">
            <el-input v-model="form.icon" placeholder="输入emoji或图标URL" maxlength="100" />
            <span class="text-3xl">{{ form.icon || '🏷️' }}</span>
          </div>
          <div class="mt-2 flex flex-wrap gap-2">
            <span v-for="emoji in suggestedEmojis" :key="emoji" class="cursor-pointer text-xl hover:scale-125 transition-transform" @click="form.icon = emoji">{{ emoji }}</span>
          </div>
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <span class="text-xs text-gray-500 ml-2">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="设为精选">
          <el-switch v-model="form.isFeatured" :active-value="1" :inactive-value="0" />
          <span class="text-xs text-gray-500 ml-2">精选话题会在话题广场置顶展示</span>
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statsVisible" title="话题数据统计" width="700px" destroy-on-close>
      <div v-if="currentStats" class="space-y-6">
        <div class="text-center mb-6">
          <span class="text-4xl">{{ currentTopic.icon }}</span>
          <h3 class="text-xl font-bold mt-2">{{ currentTopic.name }}</h3>
        </div>

        <div class="grid grid-cols-4 gap-4">
          <div class="bg-blue-50 rounded-xl p-4 text-center">
            <div class="text-3xl font-bold text-blue-600">{{ currentStats.postCount }}</div>
            <div class="text-sm text-gray-500 mt-1">帖子总数</div>
          </div>
          <div class="bg-green-50 rounded-xl p-4 text-center">
            <div class="text-3xl font-bold text-green-600">{{ currentStats.followCount }}</div>
            <div class="text-sm text-gray-500 mt-1">关注人数</div>
          </div>
          <div class="bg-purple-50 rounded-xl p-4 text-center">
            <div class="text-3xl font-bold text-purple-600">{{ currentStats.authorCount }}</div>
            <div class="text-sm text-gray-500 mt-1">参与用户</div>
          </div>
          <div class="bg-amber-50 rounded-xl p-4 text-center">
            <div class="text-3xl font-bold text-amber-600">{{ currentStats.engagementRate }}%</div>
            <div class="text-sm text-gray-500 mt-1">参与率</div>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-4">
          <div class="bg-rose-50 rounded-xl p-4 text-center">
            <div class="text-2xl font-bold text-rose-600">{{ currentStats.totalLikes }}</div>
            <div class="text-sm text-gray-500 mt-1">总点赞数</div>
            <div class="text-xs text-gray-400 mt-1">平均 {{ currentStats.avgLikes }} / 帖</div>
          </div>
          <div class="bg-sky-50 rounded-xl p-4 text-center">
            <div class="text-2xl font-bold text-sky-600">{{ currentStats.totalComments }}</div>
            <div class="text-sm text-gray-500 mt-1">总评论数</div>
            <div class="text-xs text-gray-400 mt-1">平均 {{ currentStats.avgComments }} / 帖</div>
          </div>
          <div class="bg-emerald-50 rounded-xl p-4 text-center">
            <div class="text-2xl font-bold text-emerald-600">{{ currentStats.totalBookmarks }}</div>
            <div class="text-sm text-gray-500 mt-1">总收藏数</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="statsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const topics = ref([])
const currentPage = ref(1)
const totalElements = ref(0)

const keyword = ref('')
const statusFilter = ref(null)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const form = ref({
  name: '',
  description: '',
  icon: '',
  sortOrder: 0,
  isFeatured: 0,
  status: 1
})

const statsVisible = ref(false)
const currentTopic = ref(null)
const currentStats = ref(null)

const suggestedEmojis = ['🌱', '🌿', '🐛', '🐄', '🚜', '📈', '📋', '💪', '🤖', '🏭', '🌍', '🌸', '🌾', '🍎', '🥬', '🌽', '🐔', '🐑']

const loadTopics = async (reset = false) => {
  if (reset) currentPage.value = 1
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: 20
    }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== null) params.status = statusFilter.value

    const res = await api.get('/admin/topics', { params })
    topics.value = res.data.content
    totalElements.value = res.data.totalElements
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  keyword.value = ''
  statusFilter.value = null
  loadTopics(true)
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadTopics()
}

const openCreateDialog = () => {
  isEdit.value = false
  form.value = {
    name: '',
    description: '',
    icon: '',
    sortOrder: 0,
    isFeatured: 0,
    status: 1
  }
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  form.value = {
    id: row.id,
    name: row.name,
    description: row.description,
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0,
    isFeatured: row.isFeatured || 0,
    status: row.status
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!form.value.name || form.value.name.trim().length < 2) {
    ElMessage.error('话题名称至少2个字符')
    return
  }
  if (!form.value.description || !form.value.description.trim()) {
    ElMessage.error('请输入话题描述')
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await api.put(`/admin/topics/${form.value.id}`, form.value)
      ElMessage.success('话题更新成功')
    } else {
      await api.post('/admin/topics', form.value)
      ElMessage.success('话题创建成功')
    }
    dialogVisible.value = false
    await loadTopics(true)
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}话题「${row.name}」吗？${newStatus === 0 ? '禁用后用户将无法选择该话题，但已关联的帖子不受影响。' : ''}`, `确认${action}`, {
      type: 'warning',
      confirmButtonText: `确认${action}`,
      cancelButtonText: '取消'
    })
    await api.put(`/admin/topics/${row.id}`, { status: newStatus })
    ElMessage.success(`话题已${action}`)
    await loadTopics()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const deleteTopic = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除话题「${row.name}」吗？\n该话题下有 ${row.postCount || 0} 篇帖子，${row.followCount || 0} 位用户关注。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/admin/topics/${row.id}`)
    ElMessage.success('话题删除成功')
    await loadTopics()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const viewStats = async (row) => {
  currentTopic.value = row
  try {
    const res = await api.get(`/admin/topics/${row.id}/stats`)
    currentStats.value = res.data
    statsVisible.value = true
  } catch (e) {}
}

onMounted(() => {
  loadTopics()
})
</script>
