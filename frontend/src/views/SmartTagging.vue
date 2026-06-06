<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800">🤖 智能标注</h2>
      <div class="flex items-center gap-3">
        <span class="text-sm text-gray-500">已选择 {{ selectedPostIds.length }} 篇帖子</span>
        <el-button type="primary" :disabled="selectedPostIds.length === 0 || loading" @click="startSmartTag">
          开始智能标注
        </el-button>
      </div>
    </div>

    <div class="flex gap-4 mb-6 flex-wrap items-end">
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">作者账号</span>
        <el-input v-model="filterUsername" placeholder="输入用户名搜索" clearable class="w-48" />
      </div>
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">开始日期</span>
        <el-date-picker v-model="filterStartDate" type="date" placeholder="选择开始日期" value-format="YYYY-MM-DD" class="w-44" />
      </div>
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">结束日期</span>
        <el-date-picker v-model="filterEndDate" type="date" placeholder="选择结束日期" value-format="YYYY-MM-DD" class="w-44" />
      </div>
      <div class="flex flex-col">
        <span class="text-xs text-gray-500 mb-1">标签状态</span>
        <el-select v-model="filterTagged" placeholder="全部帖子" class="w-36" clearable>
          <el-option label="未打标签" :value="0" />
          <el-option label="已打标签" :value="1" />
        </el-select>
      </div>
      <el-button type="primary" @click="loadPosts(true)">搜索</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </div>

    <div class="flex items-center gap-4 mb-4">
      <el-checkbox :indeterminate="isIndeterminate" v-model="checkAll" @change="handleCheckAllChange">
        全选当前页
      </el-checkbox>
      <el-button size="small" type="primary" link @click="selectAllUntagged">一键选择所有未打标签</el-button>
      <el-button size="small" type="warning" link @click="clearSelection">清空选择</el-button>
    </div>

    <el-table
      ref="postTableRef"
      :data="posts"
      stripe
      class="w-full flex-1"
      :loading="loading"
      v-loading="loading"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="主题" min-width="200" show-overflow-tooltip />
      <el-table-column label="话题标签" min-width="200">
        <template #default="{ row }">
          <div class="flex flex-wrap gap-1">
            <el-tag
              v-for="t in row.topics || []"
              :key="t.id"
              type="success"
              size="small"
              effect="light"
            >
              {{ t.icon }} {{ t.name }}
            </el-tag>
            <span v-if="!row.topics || row.topics.length === 0" class="text-xs text-amber-500">未打标签</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="authorName" label="作者账号" width="140" />
      <el-table-column label="发表时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
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

    <el-dialog v-model="resultVisible" title="智能标注结果" width="900px" :close-on-click-modal="false" destroy-on-close>
      <div v-if="recommendations" class="space-y-4">
        <div class="bg-green-50 rounded-lg p-4">
          <p class="text-green-700">
            已处理 <span class="font-bold">{{ recommendations.processed }}</span> 篇帖子，
            其中 <span class="font-bold">{{ Object.keys(recommendations.recommendations).length }}</span> 篇匹配到合适的话题
          </p>
        </div>

        <div v-if="Object.keys(recommendations.recommendations).length > 0">
          <div class="flex justify-between items-center mb-4">
            <h4 class="font-bold text-gray-700">推荐标签预览</h4>
            <div class="flex gap-2">
              <el-button size="small" @click="acceptAllRecommendations">全部接受</el-button>
              <el-button size="small" type="primary" :loading="applying" @click="applyTags">应用选中的标签</el-button>
            </div>
          </div>

          <div class="max-h-[500px] overflow-y-auto space-y-3">
            <div
              v-for="[postId, topicIds] in Object.entries(recommendations.recommendations)"
              :key="postId"
              class="border rounded-lg p-4"
            >
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="flex items-center gap-2 mb-2">
                    <el-checkbox v-model="selectedRecommendations[postId]" />
                    <span class="font-medium text-gray-800">{{ getPostTitle(Number(postId)) }}</span>
                  </div>
                  <div class="text-sm text-gray-500 mb-2">
                    帖子ID: {{ postId }}
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <span class="text-xs text-gray-500">推荐标签：</span>
                    <el-select
                      v-model="recommendationTags[postId]"
                      multiple
                      size="small"
                      placeholder="选择话题标签"
                      style="min-width: 300px"
                    >
                      <el-option
                        v-for="topic in recommendations.topics"
                        :key="topic.id"
                        :label="topic.icon + ' ' + topic.name"
                        :value="topic.id"
                      />
                    </el-select>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="py-8 text-center text-gray-500">
          <p>没有匹配到合适的话题标签，请尝试选择更多帖子</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="resultVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const posts = ref([])
const currentPage = ref(1)
const totalElements = ref(0)

const filterUsername = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')
const filterTagged = ref(null)

const selectedPostIds = ref([])
const checkAll = ref(false)
const isIndeterminate = ref(false)
const postTableRef = ref(null)

const resultVisible = ref(false)
const recommendations = ref(null)
const selectedRecommendations = ref({})
const recommendationTags = ref({})
const applying = ref(false)

const formatDate = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const loadPosts = async (reset = false) => {
  if (reset) {
    currentPage.value = 1
    selectedPostIds.value = []
    checkAll.value = false
    isIndeterminate.value = false
  }
  loading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: 20
    }
    if (filterUsername.value) params.username = filterUsername.value
    if (filterStartDate.value) params.startDate = filterStartDate.value
    if (filterEndDate.value) params.endDate = filterEndDate.value
    if (filterTagged.value !== null) params.tagged = filterTagged.value

    const res = await api.get('/admin/community/posts', { params })
    posts.value = res.data.content
    totalElements.value = res.data.totalElements
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  filterUsername.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  filterTagged.value = null
  loadPosts(true)
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadPosts()
}

const handleSelectionChange = (selection) => {
  selectedPostIds.value = selection.map(p => p.id)
  checkAll.value = selection.length === posts.value.length && posts.value.length > 0
  isIndeterminate.value = selection.length > 0 && selection.length < posts.value.length
}

const handleCheckAllChange = (val) => {
  if (val) {
    posts.value.forEach(row => {
      if (!selectedPostIds.value.includes(row.id)) {
        selectedPostIds.value.push(row.id)
      }
    })
  } else {
    const currentPageIds = posts.value.map(p => p.id)
    selectedPostIds.value = selectedPostIds.value.filter(id => !currentPageIds.includes(id))
  }
  isIndeterminate.value = false
}

const selectAllUntagged = async () => {
  try {
    loading.value = true
    const res = await api.get('/admin/community/posts', {
      params: { tagged: 0, page: 0, size: 1000 }
    })
    selectedPostIds.value = res.data.content.map(p => p.id)
    ElMessage.success(`已选择 ${selectedPostIds.value.length} 篇未打标签的帖子`)
  } finally {
    loading.value = false
  }
}

const clearSelection = () => {
  selectedPostIds.value = []
  checkAll.value = false
  isIndeterminate.value = false
}

const getPostTitle = (postId) => {
  const post = posts.value.find(p => p.id === postId)
  return post ? post.title : `帖子 #${postId}`
}

const startSmartTag = async () => {
  if (selectedPostIds.value.length === 0) {
    ElMessage.warning('请先选择要标注的帖子')
    return
  }

  loading.value = true
  try {
    const res = await api.post('/admin/posts/smart-tag', {
      postIds: selectedPostIds.value
    })
    recommendations.value = res.data

    selectedRecommendations.value = {}
    recommendationTags.value = {}

    for (const [postId, topicIds] of Object.entries(res.data.recommendations)) {
      selectedRecommendations.value[postId] = true
      recommendationTags.value[postId] = [...topicIds]
    }

    resultVisible.value = true
  } finally {
    loading.value = false
  }
}

const acceptAllRecommendations = () => {
  if (!recommendations.value) return
  for (const postId of Object.keys(recommendations.value.recommendations)) {
    selectedRecommendations.value[postId] = true
  }
}

const applyTags = async () => {
  if (!recommendations.value) return

  const tagMap = {}
  for (const [postId, selected] of Object.entries(selectedRecommendations.value)) {
    if (selected && recommendationTags.value[postId] && recommendationTags.value[postId].length > 0) {
      tagMap[postId] = recommendationTags.value[postId]
    }
  }

  if (Object.keys(tagMap).length === 0) {
    ElMessage.warning('请至少选择一篇帖子并选择标签')
    return
  }

  applying.value = true
  try {
    await api.post('/admin/posts/apply-tags', { tagMap })
    ElMessage.success(`已成功为 ${Object.keys(tagMap).length} 篇帖子应用标签`)
    resultVisible.value = false
    selectedPostIds.value = []
    await loadPosts(true)
  } finally {
    applying.value = false
  }
}

onMounted(() => {
  loadPosts()
})
</script>
