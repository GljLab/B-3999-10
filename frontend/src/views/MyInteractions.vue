<template>
  <div class="bg-white rounded-2xl shadow-sm p-6 min-h-full">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">📋 我的互动中心</h2>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="👍 我的认可" name="likes">
        <div v-if="likes.length === 0 && !likesLoading" class="py-8 text-center text-gray-400">
          暂无认可的帖子
        </div>
        <el-table v-else :data="likes" stripe v-loading="likesLoading">
          <el-table-column label="帖子标题" min-width="200">
            <template #default="{ row }">
              <template v-if="row.postDeleted">
                <span class="text-gray-400 line-through">{{ row.postTitle }}</span>
                <el-tag type="info" size="small" class="ml-2">已删除</el-tag>
              </template>
              <router-link v-else :to="`/community/${row.postId}`" class="text-green-600 hover:text-green-800 hover:underline">{{ row.postTitle }}</router-link>
            </template>
          </el-table-column>
          <el-table-column prop="postAuthorName" label="帖子作者" width="120" />
          <el-table-column label="认可时间" width="170">
            <template #default="{ row }">{{ formatDate(row.likedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" text @click="removeLike(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="likesTotal > 20" class="flex justify-end mt-4">
          <el-pagination
            v-model:current-page="likesPage"
            :page-size="20"
            :total="likesTotal"
            layout="prev, pager, next, total"
            @current-change="loadLikes"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="⭐ 我的收藏" name="bookmarks">
        <div v-if="bookmarks.length === 0 && !bookmarksLoading" class="py-8 text-center text-gray-400">
          暂无收藏的帖子
        </div>
        <el-table v-else :data="bookmarks" stripe v-loading="bookmarksLoading">
          <el-table-column label="帖子标题" min-width="200">
            <template #default="{ row }">
              <template v-if="row.postDeleted">
                <span class="text-gray-400 line-through">{{ row.postTitle }}</span>
                <el-tag type="info" size="small" class="ml-2">已删除</el-tag>
              </template>
              <router-link v-else :to="`/community/${row.postId}`" class="text-green-600 hover:text-green-800 hover:underline">{{ row.postTitle }}</router-link>
            </template>
          </el-table-column>
          <el-table-column prop="postAuthorName" label="帖子作者" width="120" />
          <el-table-column label="收藏时间" width="170">
            <template #default="{ row }">{{ formatDate(row.bookmarkedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.postDeleted" size="small" type="info" text @click="removeBookmark(row)">清理</el-button>
              <el-button v-else size="small" type="danger" text @click="removeBookmark(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="bookmarksTotal > 20" class="flex justify-end mt-4">
          <el-pagination
            v-model:current-page="bookmarksPage"
            :page-size="20"
            :total="bookmarksTotal"
            layout="prev, pager, next, total"
            @current-change="loadBookmarks"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="💬 我的评论" name="comments">
        <div v-if="comments.length === 0 && !commentsLoading" class="py-8 text-center text-gray-400">
          暂无评论记录
        </div>
        <el-table v-else :data="comments" stripe v-loading="commentsLoading">
          <el-table-column label="帖子标题" min-width="180">
            <template #default="{ row }">
              <template v-if="row.postDeleted">
                <span class="text-gray-400 line-through">{{ row.postTitle }}</span>
                <el-tag type="info" size="small" class="ml-2">已删除</el-tag>
              </template>
              <router-link v-else :to="`/community/${row.postId}`" class="text-green-600 hover:text-green-800 hover:underline">{{ row.postTitle }}</router-link>
            </template>
          </el-table-column>
          <el-table-column label="评论内容" min-width="250">
            <template #default="{ row }">
              <div>
                <span v-if="row.parentUserName" class="text-xs text-green-600 mr-1">@{{ row.parentUserName }}</span>
                <span class="text-gray-700">{{ row.content }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发表时间" width="170">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" text @click="removeComment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="commentsTotal > 20" class="flex justify-end mt-4">
          <el-pagination
            v-model:current-page="commentsPage"
            :page-size="20"
            :total="commentsTotal"
            layout="prev, pager, next, total"
            @current-change="loadComments"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('likes')

const likes = ref([])
const likesLoading = ref(false)
const likesPage = ref(1)
const likesTotal = ref(0)

const bookmarks = ref([])
const bookmarksLoading = ref(false)
const bookmarksPage = ref(1)
const bookmarksTotal = ref(0)

const comments = ref([])
const commentsLoading = ref(false)
const commentsPage = ref(1)
const commentsTotal = ref(0)

const formatDate = (value) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const loadLikes = async () => {
  likesLoading.value = true
  try {
    const res = await api.get('/community/my/likes', {
      params: { page: likesPage.value - 1, size: 20 }
    })
    likes.value = res.data.content
    likesTotal.value = res.data.totalElements
  } finally {
    likesLoading.value = false
  }
}

const loadBookmarks = async () => {
  bookmarksLoading.value = true
  try {
    const res = await api.get('/community/my/bookmarks', {
      params: { page: bookmarksPage.value - 1, size: 20 }
    })
    bookmarks.value = res.data.content
    bookmarksTotal.value = res.data.totalElements
  } finally {
    bookmarksLoading.value = false
  }
}

const loadComments = async () => {
  commentsLoading.value = true
  try {
    const res = await api.get('/community/my/comments', {
      params: { page: commentsPage.value - 1, size: 20 }
    })
    comments.value = res.data.content
    commentsTotal.value = res.data.totalElements
  } finally {
    commentsLoading.value = false
  }
}

const handleTabChange = (tab) => {
  if (tab === 'likes') loadLikes()
  else if (tab === 'bookmarks') loadBookmarks()
  else if (tab === 'comments') loadComments()
}

const removeLike = async (row) => {
  try {
    await ElMessageBox.confirm('确定要移除这条认可记录吗？', '确认移除', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await api.delete(`/community/my/likes/${row.id}`)
    ElMessage.success('已移除认可')
    await loadLikes()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const removeBookmark = async (row) => {
  const msg = row.postDeleted ? '确定要清理这条失效的书签吗？' : '确定要移除这条收藏记录吗？'
  try {
    await ElMessageBox.confirm(msg, '确认操作', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await api.delete(`/community/my/bookmarks/${row.id}`)
    ElMessage.success(row.postDeleted ? '已清理书签' : '已移除收藏')
    await loadBookmarks()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

const removeComment = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '确认删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/community/my/comments/${row.id}`)
    ElMessage.success('评论已删除')
    await loadComments()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {}
  }
}

onMounted(() => {
  loadLikes()
})
</script>
