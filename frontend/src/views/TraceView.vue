<template>
  <div class="min-h-screen bg-gray-50 py-8 px-4">
    <div class="max-w-4xl mx-auto">
      <div v-if="loading" class="text-center py-20">
        <el-icon class="text-4xl text-green-500 animate-spin"><Loading /></el-icon>
        <p class="mt-4 text-gray-500">正在查询溯源信息...</p>
      </div>

      <div v-else-if="error" class="text-center py-20">
        <el-icon class="text-6xl text-red-400"><Warning /></el-icon>
        <p class="mt-4 text-lg text-gray-600">{{ error }}</p>
        <el-button class="mt-4" type="primary" @click="goHome">返回首页</el-button>
      </div>

      <div v-else-if="traceResult" class="space-y-6">
        <div class="bg-white rounded-2xl shadow-lg overflow-hidden">
          <div class="relative">
            <div class="w-full h-64 md:h-80 bg-gray-100 flex items-center justify-center overflow-hidden">
              <img v-if="traceResult.product.imageUrl" :src="getImageUrl(traceResult.product.imageUrl)" class="w-full h-full object-cover" />
              <div v-else class="text-gray-400 text-center">
                <el-icon class="text-6xl"><Picture /></el-icon>
                <p class="mt-2">暂无产品图片</p>
              </div>
            </div>
            <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent p-6">
              <h1 class="text-3xl font-bold text-white">{{ traceResult.product.productName }}</h1>
              <p class="text-white/80 mt-2">{{ traceResult.product.origin }}</p>
            </div>
          </div>

          <div class="p-6">
            <div class="flex flex-wrap gap-3 mb-6">
              <el-tag type="success" size="large">{{ traceResult.product.category }}</el-tag>
              <el-tag type="warning" size="large">采摘日期: {{ traceResult.product.harvestDate || '暂无' }}</el-tag>
            </div>

            <div v-if="traceResult.spec || traceResult.batch" class="bg-gradient-to-r from-orange-50 to-yellow-50 rounded-xl p-5 mb-6 border border-orange-100">
              <h3 class="text-lg font-bold text-orange-800 mb-3 flex items-center">
                <el-icon class="mr-2"><Money /></el-icon>
                规格信息
              </h3>
              <div v-if="traceResult.spec" class="grid grid-cols-3 gap-4">
                <div class="text-center">
                  <div class="text-sm text-gray-500">规格名称</div>
                  <div class="text-lg font-bold text-gray-800">{{ traceResult.spec.specName }}</div>
                </div>
                <div class="text-center">
                  <div class="text-sm text-gray-500">重量</div>
                  <div class="text-lg font-bold text-gray-800">{{ traceResult.spec.weight }}</div>
                </div>
                <div class="text-center">
                  <div class="text-sm text-gray-500">市场建议价</div>
                  <div class="text-2xl font-bold text-orange-600">¥{{ traceResult.spec.suggestedPrice }}</div>
                </div>
              </div>
              <div v-else class="text-center text-gray-500">
                <el-icon class="text-2xl mb-1"><InfoFilled /></el-icon>
                <p>未指定规格</p>
              </div>
            </div>

            <div class="mb-6">
              <h3 class="text-lg font-bold text-gray-800 mb-3 flex items-center">
                <el-icon class="mr-2 text-green-500"><Document /></el-icon>
                产品介绍
              </h3>
              <div v-if="traceResult.product.description" class="prose max-w-none text-gray-700" v-html="traceResult.product.description"></div>
              <p v-else class="text-gray-400">暂无产品描述</p>
            </div>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-lg p-6">
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center">
            <el-icon class="mr-2 text-blue-500"><Tickets /></el-icon>
            生产批次信息
          </h3>
          <div v-if="traceResult.batch" class="bg-blue-50 rounded-xl p-4">
            <el-row :gutter="20">
              <el-col :span="8">
                <div class="text-sm text-gray-500">批次编号</div>
                <div class="font-mono font-bold text-lg text-gray-800">{{ traceResult.batch.batchNo }}</div>
              </el-col>
              <el-col :span="8">
                <div class="text-sm text-gray-500">生产日期</div>
                <div class="font-bold text-lg text-gray-800">{{ traceResult.batch.productionDate }}</div>
              </el-col>
              <el-col :span="8">
                <div class="text-sm text-gray-500">质量等级</div>
                <el-tag :type="getQualityTagType(traceResult.batch.qualityGrade)" size="large">{{ traceResult.batch.qualityGrade }}</el-tag>
              </el-col>
            </el-row>
            <div v-if="traceResult.batch.remark" class="mt-4 pt-4 border-t border-blue-200">
              <div class="text-sm text-gray-500">批次备注</div>
              <div class="text-gray-700">{{ traceResult.batch.remark }}</div>
            </div>
          </div>
          <div v-else class="text-center text-gray-400 py-8">
            <el-icon class="text-4xl mb-2"><InfoFilled /></el-icon>
            <p>该产品为早期数据，暂无详细批次信息</p>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-lg p-6">
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center">
            <el-icon class="mr-2 text-green-500"><Van /></el-icon>
            物流流转路径
          </h3>
          <el-timeline v-if="traceResult.logistics && traceResult.logistics.length > 0">
            <el-timeline-item
              v-for="(log, idx) in traceResult.logistics"
              :key="idx"
              :timestamp="new Date(log.recordedAt).toLocaleString()"
              color="#10b981"
              size="large"
            >
              <div class="font-semibold text-gray-800">{{ log.location }}</div>
              <div class="text-green-600">{{ log.statusDesc }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="text-center text-gray-400 py-8">
            <el-icon class="text-4xl mb-2"><InfoFilled /></el-icon>
            <p>暂无物流信息</p>
          </div>
        </div>

        <div class="text-center text-sm text-gray-400 pt-4">
          <p>溯源码: {{ traceCode }} | 查询时间: {{ queryTime }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { Loading, Warning, Picture, Money, InfoFilled, Document, Tickets, Van } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const traceCode = computed(() => route.params.code)
const loading = ref(true)
const error = ref('')
const traceResult = ref(null)
const queryTime = ref('')

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8000' + url
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

const goHome = () => {
  router.push('/')
}

const queryTrace = async () => {
  loading.value = true
  error.value = ''
  queryTime.value = new Date().toLocaleString()
  try {
    const res = await api.get(`/public/trace/${traceCode.value}`)
    traceResult.value = res.data
  } catch (e) {
    error.value = e.response?.data?.message || '溯源码无效或查询失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!traceCode.value) {
    error.value = '缺少溯源码参数'
    loading.value = false
    return
  }
  queryTrace()
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
