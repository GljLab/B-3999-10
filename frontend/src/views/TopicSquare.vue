<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-800">🏷️ 话题广场</h2>
      <el-button type="primary" round @click="goCommunity">
        🌾 去社区广场
      </el-button>
    </div>

    <div class="bg-white rounded-2xl p-6 shadow-sm border border-gray-100">
      <div class="flex flex-col md:flex-row gap-4 items-center justify-between mb-6">
        <el-tabs v-model="activeTab" class="flex-grow" @tab-change="handleTabChange">
          <el-tab-pane label="全部话题" name="all" />
          <el-tab-pane label="热门话题" name="hot" />
          <el-tab-pane label="活跃话题" name="active" />
          <el-tab-pane v-if="userStore.token" label="我的关注" name="followed" />
        </el-tabs>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索话题..."
          prefix-icon="Search"
          class="w-full md:w-64"
          clearable
          @clear="loadTopics(true)"
          @keyup.enter="loadTopics(true)"
        />
      </div>

      <div v-if="topics.length === 0 && !loading" class="text-center py-12">
        <el-empty description="暂无话题" />
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        <div
          v-for="topic in topics"
          :key="topic.id"
          class="group bg-gradient-to-br from-gray-50 to-white rounded-xl p-5 border border-gray-200 hover:border-green-300 hover:shadow-md transition-all duration-300 cursor-pointer"
          @click="goTopicDetail(topic.id)"
        >
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-center gap-3">
              <span class="text-4xl">{{ topic.icon || '🏷️' }}</span>
              <div>
                <h3 class="font-bold text-gray-800 group-hover:text-green-600 transition-colors">
                  {{ topic.name }}
                </h3>
                <div v-if="topic.isFeatured" class="mt-1">
                  <el-tag type="warning" size="small" effect="light">精选</el-tag>
                </div>
              </div>
            </div>
            <el-button
              v-if="userStore.token"
              :type="topic.followed ? 'success' : 'default'"
              size="small"
              :icon="topic.followed ? 'Check' : 'Plus'"
              @click.stop="toggleFollow(topic)"
              class="!min-w-0 !px-2"
            >
              {{ topic.followed ? '已关注' : '关注' }}
            </el-button>
          </div>
          <p class="text-sm text-gray-500 line-clamp-2 mb-4 min-h-[40px]">
            {{ topic.description }}
          </p>
          <div class="flex items-center gap-4 text-xs text-gray-400">
            <span class="flex items-center gap-1">
              <el-icon><Document /></el-icon>
              {{ topic.postCount }} 篇帖子
            </span>
            <span class="flex items-center gap-1">
              <el-icon><User /></el-icon>
              {{ topic.followCount }} 人关注
            </span>
          </div>
        </div>
      </div>

      <div v-if="loading" class="flex justify-center py-8">
        <el-icon class="is-loading text-3xl text-green-500"><Loading /></el-icon>
      </div>

      <div v-if="!loading && !lastPage && topics.length > 0" ref="sentinel" class="h-1"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { Loading, Search, Document, User, Check, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const topics = ref([])
const loading = ref(false)
const currentPage = ref(0)
const lastPage = ref(false)
const activeTab = ref('all')
const searchKeyword = ref('')
const sentinel = ref(null)
let observer = null

const loadTopics = async (reset = false) => {
  if (loading.value) return
  if (!reset && lastPage.value) return

  loading.value = true
  try {
    const page = reset ? 0 : currentPage.value
    const res = await api.get('/topics', {
      params: {
        type: activeTab.value,
        keyword: searchKeyword.value,
        page,
        size: 20
      }
    })
    const data = res.data
    if (reset) {
      topics.value = data.content
      currentPage.value = 0
    } else {
      topics.value = [...topics.value, ...data.content]
    }
    lastPage.value = data.last
    currentPage.value = page + 1
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  loadTopics(true)
}

const goTopicDetail = (id) => {
  router.push(`/topics/${id}`)
}

const goCommunity = () => {
  router.push('/community')
}

const toggleFollow = async (topic) => {
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const res = await api.post(`/topics/${topic.id}/follow`)
    topic.followed = res.data.followed
    topic.followCount = res.data.followCount
    ElMessage.success(topic.followed ? '关注成功' : '已取消关注')
  } catch (e) {
    // handled by interceptor
  }
}

const setupObserver = () => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting && !loading.value && !lastPage.value) {
        loadTopics()
      }
    },
    { rootMargin: '200px' }
  )
}

onMounted(async () => {
  await loadTopics(true)
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
