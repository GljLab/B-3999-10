<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-800">🌾 社区广场</h2>
      <el-button type="primary" round @click="openEditor">
        ✏️ 我要分享
      </el-button>
    </div>

    <div v-if="posts.length === 0 && !loading" class="bg-white rounded-2xl p-12 shadow-sm border border-gray-100 text-center">
      <el-empty description="还没有人分享内容，快来成为第一个吧！" />
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div
        v-for="post in posts"
        :key="post.id"
        class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-lg transition-shadow duration-300"
      >
        <div class="w-full h-48 bg-gray-100 overflow-hidden cursor-pointer" @click="goDetail(post.id)">
          <img
            v-if="post.coverImage"
            :src="getImageUrl(post.coverImage)"
            class="w-full h-full object-cover"
            alt=""
          />
          <div v-else class="w-full h-full flex items-center justify-center bg-green-50">
            <span class="text-6xl">🌱</span>
          </div>
        </div>
        <div class="p-5">
          <h3 class="text-lg font-bold text-gray-800 mb-2 truncate cursor-pointer hover:text-green-600 transition-colors" @click="goDetail(post.id)">{{ post.title }}</h3>
          <p class="text-sm text-gray-500 line-clamp-2 mb-3">
            {{ truncateDesc(post.description) }}
          </p>
          <div class="flex items-center justify-between text-sm mb-3">
            <div class="flex items-center gap-2">
              <span class="text-gray-700 font-medium">{{ post.authorName }}</span>
              <el-tag v-if="post.authorRole === 'FARMER'" type="warning" size="small" effect="dark">农场主</el-tag>
              <el-tag v-else-if="post.authorRole === 'SYS_ADMIN'" type="danger" size="small" effect="dark">管理者</el-tag>
            </div>
            <span class="text-gray-400">{{ formatTime(post.createdAt) }}</span>
          </div>
          <div class="flex items-center justify-between border-t border-gray-100 pt-3">
            <div class="flex items-center gap-4 text-xs text-gray-500">
              <span class="flex items-center gap-1">👁 {{ post.viewCount }}</span>
              <span class="flex items-center gap-1" :class="{ 'text-rose-500 font-medium': post.liked }">👍 {{ post.likeCount }}</span>
              <span class="flex items-center gap-1" :class="{ 'text-amber-500 font-medium': post.bookmarked }">⭐ {{ post.bookmarkCount }}</span>
              <span class="flex items-center gap-1">💬 {{ post.commentCount }}</span>
            </div>
            <div class="flex items-center gap-1" v-if="userStore.token && String(post.authorId) !== String(userStore.userId)">
              <el-button
                :type="post.liked ? 'danger' : 'default'"
                size="small"
                circle
                @click.stop="toggleLike(post)"
                class="!w-8 !h-8"
              >
                👍
              </el-button>
              <el-button
                :type="post.bookmarked ? 'warning' : 'default'"
                size="small"
                circle
                @click.stop="toggleBookmark(post)"
                class="!w-8 !h-8"
              >
                ⭐
              </el-button>
            </div>
            <div v-else-if="!userStore.token" class="flex items-center gap-1">
              <el-button size="small" circle class="!w-8 !h-8" @click.stop="goLogin">👍</el-button>
              <el-button size="small" circle class="!w-8 !h-8" @click.stop="goLogin">⭐</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-8">
      <el-icon class="is-loading text-3xl text-green-500"><Loading /></el-icon>
    </div>

    <div v-if="!loading && !lastPage && posts.length > 0" ref="sentinel" class="h-1"></div>

    <el-dialog v-model="editorVisible" title="分享你的故事" width="600px" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top" :model="editorForm">
        <el-form-item label="主题" required>
          <el-input v-model="editorForm.title" placeholder="请输入分享主题（50字以内）" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="详细描述" required>
          <el-input
            v-model="editorForm.description"
            type="textarea"
            placeholder="分享你的故事、感受或生活记录..."
            :rows="6"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="配图（最多3张，支持JPG/PNG，单张不超过5MB）">
          <div class="flex gap-3 flex-wrap">
            <div v-for="(img, idx) in editorForm.imageList" :key="idx" class="relative w-24 h-24 rounded-lg overflow-hidden border border-gray-200">
              <img :src="img.url" class="w-full h-full object-cover" />
              <div
                class="absolute top-1 right-1 w-5 h-5 bg-red-500 rounded-full flex items-center justify-center cursor-pointer text-white text-xs"
                @click="removeImage(idx)"
              >✕</div>
            </div>
            <div
              v-if="editorForm.imageList.length < 3"
              class="w-24 h-24 border-2 border-dashed border-gray-300 rounded-lg flex items-center justify-center cursor-pointer hover:border-green-400 transition-colors"
              @click="triggerUpload"
            >
              <el-icon class="text-2xl text-gray-400"><Plus /></el-icon>
            </div>
          </div>
          <input ref="fileInput" type="file" accept=".jpg,.jpeg,.png" class="hidden" @change="handleFileChange" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { Loading, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const posts = ref([])
const loading = ref(false)
const currentPage = ref(0)
const lastPage = ref(false)
const sentinel = ref(null)
let observer = null

const editorVisible = ref(false)
const submitting = ref(false)
const fileInput = ref(null)
const editorForm = ref({
  title: '',
  description: '',
  imageList: []
})

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
}

const formatTime = (createdAt) => {
  if (!createdAt) return ''
  const now = new Date()
  const date = new Date(createdAt)
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`

  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return '昨天'

  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const truncateDesc = (desc) => {
  if (!desc) return ''
  if (desc.length <= 100) return desc
  return desc.substring(0, 100) + '...'
}

const loadPosts = async (reset = false) => {
  if (loading.value) return
  if (!reset && lastPage.value) return

  loading.value = true
  try {
    const page = reset ? 0 : currentPage.value
    const res = await api.get('/community/posts', { params: { page, size: 20 } })
    const data = res.data
    if (reset) {
      posts.value = data.content
      currentPage.value = 0
    } else {
      posts.value = [...posts.value, ...data.content]
    }
    lastPage.value = data.last
    currentPage.value = page + 1
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => {
  router.push(`/community/${id}`)
}

const goLogin = () => {
  ElMessage.warning('请先登录后再操作')
  router.push('/login')
}

const toggleLike = async (post) => {
  if (!userStore.token) {
    goLogin()
    return
  }
  try {
    const res = await api.post(`/community/posts/${post.id}/like`)
    post.liked = res.data.liked
    post.likeCount = res.data.likeCount
  } catch (e) {
    // error handled by interceptor
  }
}

const toggleBookmark = async (post) => {
  if (!userStore.token) {
    goLogin()
    return
  }
  try {
    const res = await api.post(`/community/posts/${post.id}/bookmark`)
    post.bookmarked = res.data.bookmarked
    post.bookmarkCount = res.data.bookmarkCount
  } catch (e) {
    // error handled by interceptor
  }
}

const openEditor = () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录后再分享内容')
    router.push('/login')
    return
  }
  editorForm.value = { title: '', description: '', imageList: [] }
  editorVisible.value = true
}

const triggerUpload = () => {
  fileInput.value.click()
}

const handleFileChange = async (e) => {
  const file = e.target.files[0]
  if (!file) return

  const validTypes = ['image/jpeg', 'image/jpg', 'image/png']
  if (!validTypes.includes(file.type)) {
    ElMessage.error('仅支持JPG和PNG格式的图片')
    e.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('单张图片不能超过5MB')
    e.target.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await api.post('/community/posts/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })
    editorForm.value.imageList.push({
      url: getImageUrl(res.data),
      path: res.data
    })
  } catch (err) {
    ElMessage.error('图片上传失败')
  }
  e.target.value = ''
}

const removeImage = (idx) => {
  editorForm.value.imageList.splice(idx, 1)
}

const submitPost = async () => {
  if (!editorForm.value.title.trim()) {
    ElMessage.error('请输入分享主题')
    return
  }
  if (!editorForm.value.description.trim()) {
    ElMessage.error('请输入详细描述')
    return
  }

  submitting.value = true
  try {
    const payload = {
      title: editorForm.value.title.trim(),
      description: editorForm.value.description.trim(),
      images: editorForm.value.imageList.map(img => img.path).join(',') || null
    }
    await api.post('/community/posts', payload)
    ElMessage.success('分享发布成功！')
    editorVisible.value = false
    await loadPosts(true)
  } finally {
    submitting.value = false
  }
}

const setupObserver = () => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && !loading.value && !lastPage.value) {
        loadPosts()
      }
    },
    { rootMargin: '200px' }
  )
}

onMounted(async () => {
  await loadPosts(true)
  setupObserver()
  await nextTick()
  if (sentinel.value && observer) {
    observer.observe(sentinel.value)
  }
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
})
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
