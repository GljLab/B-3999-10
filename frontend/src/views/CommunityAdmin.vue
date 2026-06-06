<template>
  <div class="bg-white p-6 rounded-2xl shadow-sm min-h-full flex flex-col">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800">📋 社区内容管理</h2>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="📝 帖子管理" name="posts">
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
          <el-button type="primary" @click="loadPosts(true)">搜索</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>

        <el-table :data="posts" stripe class="w-full flex-1" :loading="postsLoading" v-loading="postsLoading">
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
                <span v-if="!row.topics || row.topics.length === 0" class="text-xs text-gray-400">无标签</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="authorName" label="作者账号" width="140" />
          <el-table-column label="发表时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="viewCount" label="查看次数" width="100" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="viewPost(row)">查看完整信息</el-button>
              <el-button size="small" type="danger" plain @click="deletePost(row)">移除</el-button>
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

        <el-dialog v-model="detailVisible" title="分享内容详情" width="700px" destroy-on-close>
          <div v-if="detailPost">
            <div class="mb-4">
              <span class="text-gray-500 text-sm">主题：</span>
              <span class="font-bold text-lg">{{ detailPost.title }}</span>
            </div>
            <div class="mb-4">
              <span class="text-gray-500 text-sm">作者：</span>
              <span>{{ detailPost.authorName }}</span>
              <el-tag v-if="detailPost.authorRole === 'FARMER'" type="warning" size="small" class="ml-2">农场主</el-tag>
              <el-tag v-else-if="detailPost.authorRole === 'SYS_ADMIN'" type="danger" size="small" class="ml-2">管理者</el-tag>
            </div>
            <div class="mb-4">
              <span class="text-gray-500 text-sm">发表时间：</span>
              <span>{{ formatDate(detailPost.createdAt) }}</span>
            </div>
            <div class="mb-4">
              <span class="text-gray-500 text-sm">查看次数：</span>
              <span>{{ detailPost.viewCount }}</span>
            </div>
            <div class="mb-4">
              <span class="text-gray-500 text-sm block mb-1">话题标签：</span>
              <div class="flex flex-wrap gap-1">
                <el-tag
                  v-for="t in detailPost.topics || []"
                  :key="t.id"
                  type="success"
                  size="small"
                  effect="light"
                >
                  {{ t.icon }} {{ t.name }}
                </el-tag>
                <span v-if="!detailPost.topics || detailPost.topics.length === 0" class="text-sm text-gray-400">无标签</span>
              </div>
            </div>
            <div class="mb-4">
              <span class="text-gray-500 text-sm block mb-1">详细描述：</span>
              <div class="bg-gray-50 rounded-lg p-4 text-gray-700 whitespace-pre-wrap">{{ detailPost.description }}</div>
            </div>
            <div v-if="detailPost.images && detailPost.images.length > 0">
              <span class="text-gray-500 text-sm block mb-2">配图：</span>
              <div class="flex gap-3 flex-wrap">
                <img v-for="(img, idx) in detailPost.images" :key="idx" :src="getImageUrl(img)" class="w-32 h-32 object-cover rounded-lg border" />
              </div>
            </div>
          </div>
          <template #footer>
            <el-button @click="detailVisible = false">关闭</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="💬 评论审核" name="comments">
        <div class="flex gap-4 mb-6 flex-wrap items-end">
          <div class="flex flex-col">
            <span class="text-xs text-gray-500 mb-1">用户名</span>
            <el-input v-model="commentFilterUsername" placeholder="输入用户名搜索" clearable class="w-48" />
          </div>
          <div class="flex flex-col">
            <span class="text-xs text-gray-500 mb-1">帖子标题</span>
            <el-input v-model="commentFilterPostTitle" placeholder="输入帖子标题搜索" clearable class="w-48" />
          </div>
          <div class="flex flex-col">
            <span class="text-xs text-gray-500 mb-1">开始日期</span>
            <el-date-picker v-model="commentFilterStartDate" type="date" placeholder="选择开始日期" value-format="YYYY-MM-DD" class="w-44" />
          </div>
          <div class="flex flex-col">
            <span class="text-xs text-gray-500 mb-1">结束日期</span>
            <el-date-picker v-model="commentFilterEndDate" type="date" placeholder="选择结束日期" value-format="YYYY-MM-DD" class="w-44" />
          </div>
          <el-button type="primary" @click="loadComments(true)">搜索</el-button>
          <el-button @click="resetCommentFilter">重置</el-button>
        </div>

        <el-table :data="adminComments" stripe class="w-full" :loading="commentsLoading" v-loading="commentsLoading">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="评论用户" width="160">
            <template #default="{ row }">
              <span>{{ row.userName }}</span>
              <el-tag v-if="row.userRole === 'FARMER'" type="warning" size="small" class="ml-1">农场主</el-tag>
              <el-tag v-else-if="row.userRole === 'SYS_ADMIN'" type="danger" size="small" class="ml-1">管理员</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="所属帖子" min-width="180">
            <template #default="{ row }">
              <router-link :to="`/community/${row.postId}`" class="text-green-600 hover:text-green-800 hover:underline">{{ row.postTitle }}</router-link>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="评论内容" min-width="250" show-overflow-tooltip />
          <el-table-column label="发表时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="goToPost(row)">查看上下文</el-button>
              <el-button size="small" type="danger" plain @click="deleteComment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="flex justify-end mt-4">
          <el-pagination
            v-model:current-page="commentCurrentPage"
            :page-size="20"
            :total="commentTotalElements"
            layout="prev, pager, next, total"
            @current-change="handleCommentPageChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const activeTab = ref('posts')

const postsLoading = ref(false)
const posts = ref([])
const currentPage = ref(1)
const totalElements = ref(0)

const filterUsername = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')

const detailVisible = ref(false)
const detailPost = ref(null)

const adminComments = ref([])
const commentsLoading = ref(false)
const commentCurrentPage = ref(1)
const commentTotalElements = ref(0)

const commentFilterUsername = ref('')
const commentFilterPostTitle = ref('')
const commentFilterStartDate = ref('')
const commentFilterEndDate = ref('')

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
}

const formatDate = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const loadPosts = async (reset = false) => {
  if (reset) currentPage.value = 1
  postsLoading.value = true
  try {
    const params = {
      page: currentPage.value - 1,
      size: 20
    }
    if (filterUsername.value) params.username = filterUsername.value
    if (filterStartDate.value) params.startDate = filterStartDate.value
    if (filterEndDate.value) params.endDate = filterEndDate.value

    const res = await api.get('/admin/community/posts', { params })
    posts.value = res.data.content
    totalElements.value = res.data.totalElements
  } finally {
    postsLoading.value = false
  }
}

const resetFilter = () => {
  filterUsername.value = ''
  filterStartDate.value = ''
  filterEndDate.value = ''
  loadPosts(true)
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadPosts()
}

const viewPost = (row) => {
  detailPost.value = row
  detailVisible.value = true
}

const deletePost = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要移除「${row.title}」吗？移除后将从数据库中彻底清除，不可恢复。`, '确认移除', {
      type: 'warning',
      confirmButtonText: '确认移除',
      cancelButtonText: '取消'
    })
    await api.delete(`/admin/community/posts/${row.id}`)
    ElMessage.success('内容已移除')
    await loadPosts()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const loadComments = async (reset = false) => {
  if (reset) commentCurrentPage.value = 1
  commentsLoading.value = true
  try {
    const params = {
      page: commentCurrentPage.value - 1,
      size: 20
    }
    if (commentFilterUsername.value) params.username = commentFilterUsername.value
    if (commentFilterPostTitle.value) params.postTitle = commentFilterPostTitle.value
    if (commentFilterStartDate.value) params.startDate = commentFilterStartDate.value
    if (commentFilterEndDate.value) params.endDate = commentFilterEndDate.value

    const res = await api.get('/admin/community/comments', { params })
    adminComments.value = res.data.content
    commentTotalElements.value = res.data.totalElements
  } finally {
    commentsLoading.value = false
  }
}

const resetCommentFilter = () => {
  commentFilterUsername.value = ''
  commentFilterPostTitle.value = ''
  commentFilterStartDate.value = ''
  commentFilterEndDate.value = ''
  loadComments(true)
}

const handleCommentPageChange = (page) => {
  commentCurrentPage.value = page
  loadComments()
}

const goToPost = (row) => {
  router.push(`/community/${row.postId}`)
}

const deleteComment = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？删除后在帖子详情页也会同步消失。', '确认删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/admin/community/comments/${row.id}`)
    ElMessage.success('评论已删除')
    await loadComments()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const handleTabChange = (tab) => {
  if (tab === 'posts') loadPosts()
  else if (tab === 'comments') loadComments(true)
}

onMounted(() => {
  loadPosts()
})
</script>
