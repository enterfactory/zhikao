<template>
  <AppPage title="学情分析" description="从真实作答数据发现薄弱知识点，制定教学安排。">
    <template #actions>
      <el-button :disabled="loading" @click="loadAnalysis">刷新分析</el-button>
    </template>
    <section class="console-panel console-context">
      <el-select v-model="examId" placeholder="选择考试" filterable @change="loadAnalysis">
        <el-option
          v-for="e in exams"
          :key="e.id"
          :label="e.examName + ' · ' + (e.className || '')"
          :value="e.id"
        />
      </el-select>
      <span class="console-cell-note">知识点统计基于已提交答卷</span>
    </section>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <SkeletonBlock v-if="loading" :rows="6" />
    <template v-else-if="analysis">
      <div class="console-two-columns">
        <ChartCard
          title="知识点掌握"
          :option="chartOption"
          :is-empty="!analysis.knowledgeStats?.length"
          :height="320"
        />
        <section class="console-panel">
          <header class="console-panel-head">
            <h3>需要关注的内容</h3>
            <el-tag size="small" type="info">
              {{ analysis.aiGenerated ? 'AI 建议' : '统计建议' }}
            </el-tag>
          </header>
          <div class="console-panel-body">
            <div class="console-section-label">
              薄弱知识点 {{ analysis.weakPoints?.length || 0 }} 个
            </div>
            <el-tag v-for="p in analysis.weakPoints" :key="p" type="warning" class="weak-point">
              {{ p }}
            </el-tag>
            <p class="analysis-suggestion">{{ analysis.suggestion || '暂无建议' }}</p>
          </div>
        </section>
      </div>
      <section class="console-panel">
        <header class="console-panel-head">知识点明细</header>
        <el-table :data="analysis.knowledgeStats || []">
          <el-table-column prop="knowledgePoint" label="知识点" min-width="180" />
          <el-table-column prop="total" label="作答数" width="100" />
          <el-table-column prop="correctCount" label="正确数" width="100" />
          <el-table-column label="正确率" min-width="180">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.min(100, Math.max(0, Number(row.accuracy) || 0))"
                :color="Number(row.accuracy) < 60 ? '#b97719' : '#315ce7'"
              />
            </template>
          </el-table-column>
          <el-table-column label="掌握情况" width="120">
            <template #default="{ row }">
              <el-tag :type="Number(row.accuracy) < 60 ? 'warning' : 'success'" size="small">
                {{ Number(row.accuracy) < 60 ? '需要加强' : '掌握较好' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
    <EmptyState v-else-if="!error" title="请选择考试查看分析" />
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import ChartCard from '@/components/ChartCard.vue'
import StatCard from '@/components/StatCard.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import { pageExams } from '@/api/exam'
import { aiAnalysis } from '@/api/ai'
import { SEMANTIC, WEAK_ACCURACY } from '@/utils/echarts'

/**
 * 学情分析页（补齐一期「后端有错题分析接口、前端无入口」的缺口）。
 *
 * 一个重要的演示价值：**统计部分不依赖大模型**。
 * 未配置 API Key 时，知识点正确率照常从 SQL 聚合算出来，
 * 只有「教学建议」那一栏会退化为规则化文案并明确标注「非 AI 生成」——
 * 这让系统在没模型的机器上依然能演示核心价值，也避免了「为了看起来智能而伪造结果」。
 */
const error = ref('')
const exams = ref([])
const examId = ref(null)
const analysis = ref(null)
const loading = ref(false)

const chartOption = computed(() => {
  const stats = analysis.value?.knowledgeStats || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: {
      type: 'category',
      data: stats.map((item) => item.knowledgePoint),
      axisLabel: { width: 120, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        data: stats.map((item) => ({
          value: Number(item.accuracy),
          // 薄弱知识点用危险色标出来：一眼能看到问题在哪
          itemStyle: {
            color: Number(item.accuracy) < WEAK_ACCURACY ? SEMANTIC.danger : SEMANTIC.primary,
            borderRadius: [0, 4, 4, 0],
          },
        })),
        label: { show: true, position: 'right', formatter: '{c}%' },
      },
    ],
  }
})

async function loadExams() {
  try {
    const data = await pageExams({ pageNum: 1, pageSize: 100 })
    exams.value = data.records || []
    if (!examId.value && exams.value.length) {
      examId.value = exams.value[0].id
      await loadAnalysis()
    }
  } catch (e) {
    error.value = e.msg || e.message || '考试列表加载失败'
  }
}

let analysisRequest = 0
async function loadAnalysis() {
  if (!examId.value) return
  const request = ++analysisRequest
  loading.value = true
  analysis.value = null
  error.value = ''
  try {
    const data = await aiAnalysis(examId.value)
    if (request === analysisRequest) analysis.value = data
  } catch (e) {
    if (request === analysisRequest) error.value = e.msg || e.message || '分析加载失败，请重试'
  } finally {
    if (request === analysisRequest) loading.value = false
  }
}

onMounted(loadExams)
</script>
<style scoped>
.analysis-suggestion {
  white-space: pre-wrap;
  line-height: 1.9;
  margin-top: 20px;
}
.weak-point {
  margin: 0 8px 8px 0;
}
</style>
