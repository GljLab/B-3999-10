<template>
  <div class="space-y-6">
    <div class="flex items-center gap-3 mb-2">
      <el-button text @click="goBack" class="text-gray-500 hover:text-green-600">
        <el-icon class="mr-1"><ArrowLeft /></el-icon> 返回话题广场
      </el-button>
    </div>

    <div v-if="!topic" class="bg-white rounded-2xl p-12 shadow-sm border border-gray-100">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else class="bg-gradient-to-br from-green-50 to-white rounded-2xl shadow-sm border border-green-100 overflow-hidden">
      <div class="p-8">
        <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-6">
          <div class="flex items-start gap-4">
            <div class="w-20 h-20 rounded-2xl bg-white shadow-md flex items-center justify-center text-5xl">
              {{ topic.icon || '🏷️' }}
            </div>
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-2">
                <h1 class="text-2xl font-bold text-gray-800">{{ topic.name }}</h1>
                <el-tag v-if="topic.isFeatured" type="warning" effect="dark">精选话题</el-tag>
              </div>
              <p class="text-gray-600 mb-4">{{ topic.description }}</p>
              <div class="flex flex-wrap items-center gap-6 text-sm text-gray-500">
                <span class="flex items-center gap-1">
                  <el-icon class="text-green-500"><Document /></el-icon>
                  <strong class="text-gray-700">{{ topic.postCount }}</strong> 篇帖子
                </span>
                <span class="flex items-center gap-1">
                  <el-icon class="text-green-500"><User /></el-icon>
                  <strong class="text-gray-700">{{ topic.followCount }}</strong> 人关注
                </span>
                <span v-if="topic.userPostCount > 0" class="flex items-center gap-1">
                  <el-icon class="text-green-500"><Edit /></el-icon>
                  我发布了 <strong class="text-gray-700">{{ topic.userPostCount }}</strong> 篇
                </span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <el-button
              v-if="userStore.token"
              :type="topic.followed ? 'success' : 'primary'"
              size="large"
              round
              @click="toggleFollow"
            >
              <el-icon class="mr-1">{{ topic.followed ? 'Check' : 'Plus' }}</el-icon>
              {{ topic.followed ? '已关注' : '关注话题' }}
            </el-button>
            <el-button
              v-else
              type="primary"
              size="large"
              round
              @click="goLogin"
            >
              登录后关注
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
      <div class="p-4 border-b border-gray-100">
        <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <h3 class="text-lg font-bold text-gray-800">话题帖子</h3>
          <el-radio-group v-model="sortType" size="small" @change="loadPosts(true)">
            <el-radio-button value="smart">智能排序</el-radio-button>
            <el-radio-button value="latest">最新发布</el-radio-button>
            <el-radio-button value="popular">最多互动</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div v-if="posts.length === 0 && !loading" class="p-12 text-center">
        <el-empty description="该话题下暂无帖子" />
      </div>

      <div class="divide-y divide-gray-100">
        <div
          v-for="post in posts"
          :key="post.id"
          class="p-6 hover:bg-gray-50 transition-colors cursor-pointer"
          @click="goPostDetail(post.id)"
        >
          <div class="flex gap-4">
            <div class="w-24 h-24 rounded-lg bg-gray-100 flex-shrink-0 overflow-hidden">
              <img
                v-if="post.coverImage"
                :src="getImageUrl(post.coverImage)"
                class="w-full h-full object-cover"
                alt=""
              />
              <div v-else class="w-full h-full flex items-center justify-center bg-green-50">
                <span class="text-3xl">🌱</span>
              </div>
            </div>
            <div class="flex-1 min-w-0">
              <h4 class="font-bold text-gray-800 mb-2 hover:text-green-600 transition-colors truncate">
                {{ post.title }}
              </h4>
              <p class="text-sm text-gray-500 line-clamp-2 mb-3">
                {{ truncateDesc(post.description) }}
              </p>
              <div class="flex flex-wrap items-center gap-2 mb-3">
                <el-tag
                  v-for="t in post.topics"
                  :key="t.id"
                  type="success"
                  size="small"
                  effect="light"
                  class="cursor-pointer hover:bg-green-100"
                  @click.stop="goTopicDetail(t.id)"
                >
                  {{ t.icon }} {{ t.name }}
                </el-tag>
              </div>
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3 text-xs text-gray-400">
                  <span class="font-medium text-gray-600">{{ post.authorName }}</span>
                  <span>{{ formatTime(post.createdAt) }}</span>
                </div>
                <div class="flex items-center gap-4 text-xs text-gray-500">
                  <span class="flex items-center gap-1">👁 {{ post.viewCount }}</span>
                  <span class="flex items-center gap-1" :class="{ 'text-rose-500': post.liked }">👍 {{ post.likeCount }}</span>
                  <span class="flex items-center gap-1" :class="{ 'text-amber-500': post.bookmarked }">⭐ {{ post.bookmarkCount }}</span>
                  <span class="flex items-center gap-1">💬 {{ post.commentCount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-8">
        <el-icon class="is-loading text-3xl text-green-500"><Loading /></el-icon>
      </div>

      <div v-if="!loading && !lastPage && posts.length > 0" ref="sentinel" class="h-1"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Loading, Document, User, Edit, Check, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const topic = ref(null)
const posts = ref([])
const loading = ref(false)
const currentPage = ref(0)
const lastPage = ref(false)
const sortType = ref('smart')
const sentinel = ref(null)
let observer = null

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

const loadTopic = async () => {
  try {
    const res = await api.get(`/topics/${route.params.id}`)
    topic.value = res.data
  } catch (e) {
    ElMessage.error('话题加载失败')
  }
}

const loadPosts = async (reset = false) => {
  if (loading.value) return
  if (!reset && lastPage.value) return

  loading.value = true
  try {
    const page = reset ? 0 : currentPage.value
    const res = await api.get(`/topics/${route.params.id}/posts`, {
      params: {
        sort: sortType.value,
        page,
        size: 20
      }
    })
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

const toggleFollow = async () => {
  if (!userStore.token) {
    goLogin()
    return
  }
  try {
    const res = await api.post(`/topics/${route.params.id}/follow`)
    topic.value.followed = res.data.followed
    topic.value.followCount = res.data.followCount
    ElMessage.success(topic.value.followed ? '关注成功' : '已取消关注')
  } catch (e) {
    // handled by interceptor
  }
}

const goBack = () => {
  router.push('/topics')
}

const goLogin = () => {
  ElMessage.warning('请先登录')
  router.push('/login')
}

const goPostDetail = (id) => {
  router.push(`/community/${id}`)
}

const goTopicDetail = (id) => {
  router.push(`/topics/${id}`)
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
  await loadTopic()
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
