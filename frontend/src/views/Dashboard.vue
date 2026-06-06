<template>
  <div class="space-y-6">
    <div class="bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
      <h2 class="text-2xl font-bold text-gray-800 mb-4">🔍 防伪溯源查询</h2>
      <div class="flex space-x-4">
        <el-input v-model="traceCode" placeholder="请输入溯源码 (如: TRC-AKAPPLE-231015-0001A)" size="large" class="max-w-lg shadow-sm" clearable @keyup.enter="queryTrace" />
        <el-button type="primary" size="large" @click="queryTrace" :loading="tracing">溯源查询</el-button>
      </div>

      <div v-if="traceResult" class="mt-8">
        <div class="mb-6">
          <div class="flex items-start gap-6">
            <div class="w-48 h-48 flex-shrink-0 rounded-xl overflow-hidden border-2 border-gray-200 bg-gray-100">
              <img v-if="traceResult.product.imageUrl" :src="getImageUrl(traceResult.product.imageUrl)" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex items-center justify-center text-gray-400">
                <el-icon class="text-5xl"><Picture /></el-icon>
              </div>
            </div>
            <div class="flex-1">
              <h3 class="text-2xl font-bold text-gray-800">{{ traceResult.product.productName }}</h3>
              <div class="flex gap-2 mt-3">
                <el-tag type="success" size="large">{{ traceResult.product.category }}</el-tag>
                <el-tag type="info" size="large">{{ traceResult.product.origin }}</el-tag>
              </div>
              <div class="mt-4 text-gray-600">
                <span class="text-sm text-gray-500">采摘日期: </span>
                <span class="font-medium">{{ traceResult.product.harvestDate || '暂无' }}</span>
              </div>
              <div class="mt-2 text-gray-600">
                <span class="text-sm text-gray-500">溯源码生成时间: </span>
                <span class="font-medium">{{ new Date(traceResult.traceInfo.generatedAt).toLocaleString() }}</span>
              </div>
              <div v-if="traceResult.spec" class="mt-4 bg-orange-50 inline-block px-4 py-2 rounded-lg">
                <span class="text-sm text-orange-600">规格: {{ traceResult.spec.specName }} ({{ traceResult.spec.weight }})</span>
                <span class="ml-2 text-lg font-bold text-orange-600">¥{{ traceResult.spec.suggestedPrice }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-gray-50 rounded-xl p-5 mb-6">
          <h4 class="font-bold text-gray-800 mb-3">📝 产品介绍</h4>
          <div v-if="traceResult.product.description" class="prose max-w-none text-gray-700" v-html="traceResult.product.description"></div>
          <p v-else class="text-gray-400">暂无产品描述</p>
        </div>

        <el-descriptions v-if="traceResult.batch" title="🏷️ 生产批次信息" bordered class="bg-green-50 p-4 rounded-lg">
          <el-descriptions-item label="批次编号">
            <span class="font-mono font-bold">{{ traceResult.batch.batchNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ traceResult.batch.productionDate }}</el-descriptions-item>
          <el-descriptions-item label="质量等级">
            <el-tag :type="getQualityTagType(traceResult.batch.qualityGrade)">{{ traceResult.batch.qualityGrade }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="批次备注" :span="2">{{ traceResult.batch.remark || '无特殊备注' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-else title="🏷️ 生产批次信息" bordered class="bg-gray-50 p-4 rounded-lg">
          <el-descriptions-item label="批次编号">
            <el-tag type="info" effect="plain">早期批次</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ traceResult.product.harvestDate || '暂无' }}</el-descriptions-item>
          <el-descriptions-item label="质量等级">
            <el-tag type="info" effect="plain">早期批次</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="批次备注" :span="2">该产品为早期数据，暂无详细批次信息</el-descriptions-item>
        </el-descriptions>

        <h3 class="text-lg font-semibold text-gray-800 mt-6 mb-4">🚚 物流流转路径</h3>
        <el-timeline v-if="traceResult.logistics && traceResult.logistics.length > 0">
          <el-timeline-item
            v-for="(log, idx) in traceResult.logistics"
            :key="idx"
            :timestamp="new Date(log.recordedAt).toLocaleString()"
            color="#10b981"
          >
            {{ log.location }} - <span class="font-bold text-green-700">{{ log.statusDesc }}</span>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无物流信息" />
      </div>
    </div>

    <div v-if="userStore.role === 'SYS_ADMIN' && communityStats" class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center">📊 社区数据概览</h2>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="bg-gradient-to-br from-green-50 to-green-100 p-5 rounded-xl border border-green-200">
          <div class="text-sm text-green-600 mb-1">累计分享总量</div>
          <div class="text-3xl font-bold text-green-700">{{ communityStats.totalPosts }}</div>
        </div>
        <div class="bg-gradient-to-br from-blue-50 to-blue-100 p-5 rounded-xl border border-blue-200">
          <div class="text-sm text-blue-600 mb-1">今日新增分享</div>
          <div class="text-3xl font-bold text-blue-700">{{ communityStats.todayPosts }}</div>
        </div>
        <div class="bg-gradient-to-br from-orange-50 to-orange-100 p-5 rounded-xl border border-orange-200">
          <div class="text-sm text-orange-600 mb-1">分享作者数</div>
          <div class="text-3xl font-bold text-orange-700">{{ communityStats.totalAuthors }}</div>
        </div>
      </div>
    </div>

    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <h2 class="text-xl font-bold text-gray-800 mb-4 flex items-center">🔥 热门推荐农产品</h2>
      <el-skeleton :rows="3" animated :loading="hotLoading">
        <template #default>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div v-for="(item, index) in hotProducts" :key="index" class="p-4 border rounded-xl hover:shadow-lg transition flex flex-col">
              <div class="w-full h-32 bg-gray-100 rounded-lg mb-3 overflow-hidden">
                <img v-if="item.imageUrl" :src="getImageUrl(item.imageUrl)" class="w-full h-full object-cover" />
                <div v-else class="w-full h-full flex items-center justify-center text-gray-400">
                  <el-icon class="text-3xl"><Picture /></el-icon>
                </div>
              </div>
              <div class="text-lg font-bold text-gray-800">{{item.productName}}</div>
              <div class="text-sm text-gray-500 mt-2">产地: {{item.origin}}</div>
              <div class="mt-4 flex justify-between items-center bg-gray-50 p-2 rounded">
                <el-tag type="warning" effect="dark" round>被查询 {{item.searchCount}} 次</el-tag>
                <el-tag type="success" size="small">{{item.category}}</el-tag>
              </div>
            </div>
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const traceCode = ref('')
const tracing = ref(false)
const traceResult = ref(null)

const hotProducts = ref([])
const hotLoading = ref(true)

const communityStats = ref(null)

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
}

const getHotProducts = async () => {
  try {
    const res = await api.get('/public/hot')
    hotProducts.value = res.data
  } finally {
    hotLoading.value = false
  }
}

const queryTrace = async () => {
  if (!traceCode.value.trim()) return ElMessage.warning('请输入溯源码')
  tracing.value = true
  traceResult.value = null
  try {
    const res = await api.get(`/public/trace/${traceCode.value.trim()}`)
    traceResult.value = res.data
  } catch(e) {
  } finally {
    tracing.value = false
  }
}

const getQualityTagType = (grade) => {
  const typeMap = {
    '特级': 'danger',
    '一级': 'warning',
    '二级': 'success',
    '普通': 'info'
  }
  return typeMap[grade] || 'info'
}

const getCommunityStats = async () => {
  if (userStore.role !== 'SYS_ADMIN') return
  try {
    const res = await api.get('/admin/community/stats')
    communityStats.value = res.data
  } catch (e) {
    // silently fail
  }
}

onMounted(() => {
  getHotProducts()
  getCommunityStats()
})
</script>

<style>
.prose {
  line-height: 1.8;
}
.prose h1, .prose h2, .prose h3 {
  font-weight: bold;
  margin-top: 1em;
  margin-bottom: 0.5em;
}
.prose h1 { font-size: 1.5em; }
.prose h2 { font-size: 1.25em; }
.prose h3 { font-size: 1.1em; }
.prose p { margin-bottom: 1em; }
.prose ul, .prose ol { margin-left: 1.5em; margin-bottom: 1em; }
.prose li { margin-bottom: 0.25em; }
.prose strong { font-weight: bold; }
.prose em { font-style: italic; }
.prose u { text-decoration: underline; }
.prose a { color: #3b82f6; text-decoration: underline; }
</style>
