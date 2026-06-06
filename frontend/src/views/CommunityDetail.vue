<template>
  <div class="space-y-6">
    <div class="flex items-center gap-3 mb-2">
      <el-button text @click="goBack" class="text-gray-500 hover:text-green-600">
        <el-icon class="mr-1"><ArrowLeft /></el-icon> 返回社区广场
      </el-button>
    </div>

    <div v-if="!post" class="bg-white rounded-2xl p-12 shadow-sm border border-gray-100 text-center">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
      <div class="p-8">
        <div class="flex items-center justify-between mb-6">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center text-green-600 text-xl font-bold overflow-hidden">
              <img v-if="post.authorAvatar" :src="getImageUrl(post.authorAvatar)" class="w-full h-full object-cover" />
              <span v-else>{{ post.authorName ? post.authorName.charAt(0) : '?' }}</span>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-lg font-bold text-gray-800">{{ post.authorName }}</span>
                <el-tag v-if="post.authorRole === 'FARMER'" type="warning" size="small" effect="dark">农场主</el-tag>
                <el-tag v-else-if="post.authorRole === 'SYS_ADMIN'" type="danger" size="small" effect="dark">管理者</el-tag>
              </div>
              <span class="text-sm text-gray-400">{{ formatTime(post.createdAt) }}</span>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <el-button
              v-if="canDelete"
              type="danger"
              plain
              size="small"
              @click="confirmDelete"
            >
              移除此内容
            </el-button>
          </div>
        </div>

        <h1 class="text-2xl font-bold text-gray-800 mb-6">{{ post.title }}</h1>

        <div class="prose max-w-none text-gray-700 whitespace-pre-wrap leading-relaxed text-base mb-6">
          {{ post.description }}
        </div>

        <div v-if="post.images && post.images.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-6">
          <div
            v-for="(img, idx) in post.images"
            :key="idx"
            class="rounded-xl overflow-hidden border border-gray-200 cursor-pointer hover:shadow-lg transition-shadow"
            @click="openPreview(idx)"
          >
            <img :src="getImageUrl(img)" class="w-full h-56 object-cover" alt="" />
          </div>
        </div>

        <div class="flex items-center justify-between mt-8 pt-6 border-t border-gray-100">
          <div class="flex items-center gap-5 text-sm text-gray-500">
            <span>👁 {{ post.viewCount }} 次查看</span>
            <span :class="{ 'text-rose-500 font-bold': post.liked }">👍 {{ post.likeCount }} 人认可</span>
            <span :class="{ 'text-amber-500 font-bold': post.bookmarked }">⭐ {{ post.bookmarkCount }} 人收藏</span>
            <span>💬 {{ post.commentCount }} 条讨论</span>
          </div>
          <div class="flex items-center gap-2" v-if="userStore.token && String(post.authorId) !== String(userStore.userId)">
            <el-button
              :type="post.liked ? 'danger' : 'default'"
              round
              @click="toggleLike"
              class="transition-all duration-200"
              :class="{ 'scale-110': likeAnimating }"
            >
              👍 {{ post.liked ? '已认可' : '认可' }}
            </el-button>
            <el-button
              :type="post.bookmarked ? 'warning' : 'default'"
              round
              @click="toggleBookmark"
              class="transition-all duration-200"
              :class="{ 'scale-110': bookmarkAnimating }"
            >
              ⭐ {{ post.bookmarked ? '已收藏' : '收藏' }}
            </el-button>
          </div>
          <div v-else-if="!userStore.token" class="flex items-center gap-2">
            <el-button round @click="goLogin">👍 认可</el-button>
            <el-button round @click="goLogin">⭐ 收藏</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="post" class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
      <div class="p-6">
        <h3 class="text-lg font-bold text-gray-800 mb-6">💬 讨论区 ({{ post.commentCount }})</h3>

        <div v-if="userStore.token" class="mb-6 p-4 bg-gray-50 rounded-xl">
          <el-input
            v-model="newComment"
            type="textarea"
            placeholder="分享你的看法、经验或疑问..."
            :rows="3"
            maxlength="500"
            show-word-limit
          />
          <div class="flex justify-end mt-2">
            <el-button type="primary" :loading="submittingComment" @click="submitComment(null)">发表评论</el-button>
          </div>
        </div>
        <div v-else class="mb-6 p-4 bg-gray-50 rounded-xl text-center">
          <span class="text-gray-500">登录后即可参与讨论</span>
          <el-button type="primary" size="small" class="ml-3" @click="goLogin">去登录</el-button>
        </div>

        <div v-if="comments.length === 0 && !commentsLoading" class="py-8 text-center text-gray-400">
          暂无讨论，来发表第一条评论吧！
        </div>

        <div v-for="comment in comments" :key="comment.id" class="mb-4">
          <div class="p-4 rounded-xl bg-gray-50 hover:bg-gray-100 transition-colors">
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-full bg-green-100 flex items-center justify-center text-green-600 text-sm font-bold">
                  {{ comment.userName ? comment.userName.charAt(0) : '?' }}
                </div>
                <span class="font-medium text-gray-800 text-sm">{{ comment.userName }}</span>
                <el-tag v-if="comment.userRole === 'FARMER'" type="warning" size="small">农场主</el-tag>
                <el-tag v-else-if="comment.userRole === 'SYS_ADMIN'" type="danger" size="small">管理者</el-tag>
                <span class="text-xs text-gray-400">{{ formatTime(comment.createdAt) }}</span>
              </div>
              <div class="flex items-center gap-1">
                <el-button
                  v-if="userStore.token"
                  size="small"
                  text
                  type="primary"
                  @click="startReply(comment)"
                >
                  回复
                </el-button>
                <el-button
                  v-if="canDeleteComment(comment)"
                  size="small"
                  text
                  type="danger"
                  @click="confirmDeleteComment(comment)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <p v-if="comment.deleted" class="text-gray-400 italic text-sm">该评论已删除</p>
            <p v-else class="text-gray-700 text-sm leading-relaxed ml-10">{{ comment.content }}</p>

            <div v-if="replyTarget && replyTarget.id === comment.id" class="mt-3 ml-10 p-3 bg-white rounded-lg border border-gray-200">
              <div class="text-xs text-gray-500 mb-1">回复 {{ comment.userName }}：</div>
              <el-input
                v-model="replyContent"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="输入你的回复..."
              />
              <div class="flex justify-end gap-2 mt-2">
                <el-button size="small" @click="cancelReply">取消</el-button>
                <el-button size="small" type="primary" :loading="submittingComment" @click="submitComment(comment.id)">回复</el-button>
              </div>
            </div>
          </div>

          <div v-if="comment.replies && comment.replies.length > 0" class="ml-10 mt-2">
            <div v-for="reply in comment.replies" :key="reply.id" class="p-3 rounded-lg bg-gray-50/70 hover:bg-gray-100 transition-colors mb-2 border-l-3 border-green-300">
              <div class="flex items-center justify-between mb-1">
                <div class="flex items-center gap-2">
                  <div class="w-6 h-6 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold">
                    {{ reply.userName ? reply.userName.charAt(0) : '?' }}
                  </div>
                  <span class="font-medium text-gray-800 text-xs">{{ reply.userName }}</span>
                  <el-tag v-if="reply.userRole === 'FARMER'" type="warning" size="small">农场主</el-tag>
                  <el-tag v-else-if="reply.userRole === 'SYS_ADMIN'" type="danger" size="small">管理者</el-tag>
                  <template v-if="reply.parentUserName">
                    <span class="text-xs text-gray-400">回复</span>
                    <span class="text-xs text-green-600 font-medium">@{{ reply.parentUserName }}</span>
                  </template>
                  <span class="text-xs text-gray-400">{{ formatTime(reply.createdAt) }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <el-button
                    v-if="userStore.token"
                    size="small"
                    text
                    type="primary"
                    @click="startReply(reply)"
                  >
                    回复
                  </el-button>
                  <el-button
                    v-if="canDeleteComment(reply)"
                    size="small"
                    text
                    type="danger"
                    @click="confirmDeleteComment(reply)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
              <p v-if="reply.deleted" class="text-gray-400 italic text-xs ml-8">该评论已删除</p>
              <p v-else class="text-gray-700 text-xs leading-relaxed ml-8">{{ reply.content }}</p>

              <div v-if="replyTarget && replyTarget.id === reply.id" class="mt-2 ml-8 p-3 bg-white rounded-lg border border-gray-200">
                <div class="text-xs text-gray-500 mb-1">回复 {{ reply.userName }}：</div>
                <el-input
                  v-model="replyContent"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="输入你的回复..."
                />
                <div class="flex justify-end gap-2 mt-2">
                  <el-button size="small" @click="cancelReply">取消</el-button>
                  <el-button size="small" type="primary" :loading="submittingComment" @click="submitComment(reply.id)">回复</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="commentsLoading" class="flex justify-center py-4">
          <el-icon class="is-loading text-2xl text-green-500"><Loading /></el-icon>
        </div>

        <div v-if="!commentsLoading && !commentsLastPage && comments.length > 0" class="flex justify-center mt-4">
          <el-button text type="primary" @click="loadMoreComments">加载更多评论</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="previewVisible" width="80%" :show-close="true" destroy-on-close>
      <div class="flex items-center justify-center">
        <img v-if="post && post.images && post.images[previewIndex]" :src="getImageUrl(post.images[previewIndex])" class="max-w-full max-h-[70vh] object-contain" alt="" />
      </div>
      <div class="flex justify-center gap-4 mt-4">
        <el-button :disabled="previewIndex <= 0" @click="previewIndex--">上一张</el-button>
        <span class="flex items-center text-gray-500">{{ previewIndex + 1 }} / {{ post?.images?.length || 0 }}</span>
        <el-button :disabled="!post?.images || previewIndex >= post.images.length - 1" @click="previewIndex++">下一张</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const previewVisible = ref(false)
const previewIndex = ref(0)
const comments = ref([])
const commentsPage = ref(0)
const commentsLastPage = ref(false)
const commentsLoading = ref(false)
const newComment = ref('')
const submittingComment = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const likeAnimating = ref(false)
const bookmarkAnimating = ref(false)

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
}

const formatTime = (createdAt) => {
  if (!createdAt) return ''
  return new Date(createdAt).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const canDelete = computed(() => {
  if (!userStore.token || !post.value) return false
  if (userStore.role === 'SYS_ADMIN') return true
  if (post.value.authorId && String(post.value.authorId) === String(userStore.userId)) return true
  return false
})

const canDeleteComment = (comment) => {
  if (!userStore.token || !post.value) return false
  if (userStore.role === 'SYS_ADMIN') return true
  if (post.value.authorId && String(post.value.authorId) === String(userStore.userId)) return true
  if (comment.userId && String(comment.userId) === String(userStore.userId)) return true
  return false
}

const loadPost = async () => {
  try {
    const res = await api.get(`/community/posts/${route.params.id}`)
    post.value = res.data
  } catch (e) {
    ElMessage.error('内容加载失败')
  }
}

const loadComments = async (reset = false) => {
  if (commentsLoading.value) return
  const page = reset ? 0 : commentsPage.value
  commentsLoading.value = true
  try {
    const res = await api.get(`/community/posts/${route.params.id}/comments`, {
      params: { page, size: 20 }
    })
    const data = res.data
    if (reset) {
      comments.value = data.content
    } else {
      comments.value = [...comments.value, ...data.content]
    }
    commentsPage.value = page + 1
    commentsLastPage.value = data.last
  } catch (e) {
    // handled by interceptor
  } finally {
    commentsLoading.value = false
  }
}

const loadMoreComments = () => {
  loadComments()
}

const goBack = () => {
  router.push('/community')
}

const goLogin = () => {
  ElMessage.warning('请先登录后再操作')
  router.push('/login')
}

const openPreview = (idx) => {
  previewIndex.value = idx
  previewVisible.value = true
}

const toggleLike = async () => {
  if (!userStore.token) { goLogin(); return }
  try {
    const res = await api.post(`/community/posts/${post.value.id}/like`)
    post.value.liked = res.data.liked
    post.value.likeCount = res.data.likeCount
    likeAnimating.value = true
    setTimeout(() => { likeAnimating.value = false }, 300)
  } catch (e) {}
}

const toggleBookmark = async () => {
  if (!userStore.token) { goLogin(); return }
  try {
    const res = await api.post(`/community/posts/${post.value.id}/bookmark`)
    post.value.bookmarked = res.data.bookmarked
    post.value.bookmarkCount = res.data.bookmarkCount
    bookmarkAnimating.value = true
    setTimeout(() => { bookmarkAnimating.value = false }, 300)
  } catch (e) {}
}

const startReply = (comment) => {
  if (!userStore.token) { goLogin(); return }
  replyTarget.value = comment
  replyContent.value = ''
}

const cancelReply = () => {
  replyTarget.value = null
  replyContent.value = ''
}

const submitComment = async (parentId) => {
  if (!userStore.token) { goLogin(); return }
  const content = parentId ? replyContent.value : newComment.value
  if (!content || !content.trim()) {
    ElMessage.error('请输入评论内容')
    return
  }

  submittingComment.value = true
  try {
    await api.post(`/community/posts/${post.value.id}/comments`, {
      parentId: parentId,
      content: content.trim()
    })
    ElMessage.success('评论发表成功')
    newComment.value = ''
    replyTarget.value = null
    replyContent.value = ''
    post.value.commentCount += 1
    await loadComments(true)
  } catch (e) {
    // handled by interceptor
  } finally {
    submittingComment.value = false
  }
}

const confirmDeleteComment = async (comment) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '确认删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await api.delete(`/community/comments/${comment.id}`)
    ElMessage.success('评论已删除')
    await loadComments(true)
    await loadPost()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      // handled by interceptor
    }
  }
}

const confirmDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要移除此内容吗？移除后不可恢复。', '确认移除', {
      type: 'warning',
      confirmButtonText: '确认移除',
      cancelButtonText: '取消'
    })
    await api.delete(`/community/posts/${post.value.id}`)
    ElMessage.success('内容已移除')
    router.push('/community')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      // api interceptor handles error message
    }
  }
}

onMounted(() => {
  loadPost()
  loadComments(true)
})
</script>

<style scoped>
.border-l-3 {
  border-left-width: 3px;
}
</style>
