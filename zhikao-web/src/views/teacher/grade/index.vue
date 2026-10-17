<template>
  <AppPage title="成绩统计" description="查看考试表现、核对评分进度并导出成绩。">
    <template #actions>
      <el-button :disabled="!examId || loading" @click="load">刷新</el-button>
      <el-button
        :disabled="!grade.items?.length || loading"
        :loading="exporting"
        @click="handleExport"
      >
        导出 Excel
      </el-button>
      <el-button
        type="primary"
        :disabled="!examId"
        @click="router.push({ path: '/teacher/monitor', query: { examId } })"
      >
        成绩发布
      </el-button>
    </template>
    <section class="console-panel console-context">
      <el-select v-model="examId" placeholder="选择考试" filterable @change="load">
        <el-option
          v-for="e in exams"
          :key="e.id"
          :label="e.examName + ' · ' + (e.className || '')"
          :value="e.id"
        />
      </el-select>
      <span class="console-cell-note">{{ grade.examName }}</span>
    </section>
    <el-alert v-if="err" :title="err" type="error" :closable="false" />
    <template v-if="examId">
      <div class="console-metrics">
        <div
          v-for="m in [
            { label: '纳入统计（终评）', value: grade.participantCount },
            { label: '终评平均分', value: grade.avgScore },
            { label: '及格率', value: grade.passRate == null ? '—' : grade.passRate + '%' },
            {
              label: '最高 / 最低',
              value: (grade.maxScore ?? '—') + ' / ' + (grade.minScore ?? '—'),
            },
          ]"
          :key="m.label"
          class="console-metric"
        >
          <span>{{ m.label }}</span>
          <strong>{{ m.value ?? '—' }}</strong>
        </div>
      </div>
      <div class="console-two-columns">
        <section class="console-panel">
          <header class="console-panel-head">
            成绩分布
            <span class="console-cell-note">及格线 {{ grade.passLine ?? '—' }} 分</span>
          </header>
          <div ref="chartRef" style="height: 280px" />
        </section>
        <section class="console-panel">
          <header class="console-panel-head">评分与发布</header>
          <div class="console-panel-body">
            <div class="console-summary-row">
              <span>已交卷</span>
              <strong>{{ grade.submittedCount ?? 0 }} 人</strong>
            </div>
            <div class="console-summary-row">
              <span>已完成评分</span>
              <strong>{{ grade.markedCount ?? 0 }} / {{ grade.submittedCount ?? 0 }}</strong>
            </div>
            <p class="console-form-hint">
              平均分、及格率、分布和排名仅统计已完成评分的答卷；待阅卷成绩不参与排名。
              {{ grade.rosterBasis === 'publication_snapshot' ? '发布时参考名单' : '历史已有参考记录（原始应考名单未知）' }} {{ grade.shouldCount ?? 0 }} 人，转班不会改变该名单。
              主观题评分完成后，前往考试监控页核对并发布成绩。
            </p>
            <el-button
              text
              type="primary"
              @click="router.push({ path: '/teacher/marking', query: { examId } })"
            >
              前往阅卷
            </el-button>
          </div>
        </section>
      </div>
      <section class="console-panel">
        <header class="console-panel-head">
          <h3>学生成绩明细</h3>
          <el-input
            v-model="studentKeyword"
            placeholder="搜索姓名或账号"
            clearable
            style="max-width: 240px"
          />
        </header>
        <DataTable
          :rows="visibleItems"
          :loading="loading"
          :show-pagination="false"
          :error="err"
          @retry="load"
        >
          <el-table-column label="名次" width="70">
            <template #default="{ row }">{{ row.rank ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="username" label="账号" width="120" />
          <el-table-column prop="className" label="班级" min-width="150" />
          <el-table-column prop="objectiveScore" label="客观题" width="90" />
          <el-table-column prop="subjectiveScore" label="主观题" width="90" />
          <el-table-column prop="totalScore" label="总分" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 3 ? 'success' : 'warning'" size="small" effect="plain">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submitTime" label="交卷时间" width="165" />
          <el-table-column label="异常" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.cheatCount > 0" type="danger" size="small" effect="plain">
                {{ row.cheatCount }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </DataTable>
      </section>
    </template>
    <EmptyState v-else title="请选择一场考试" />
  </AppPage>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
// 二期改为按需引入：原来这里是 `import * as echarts from 'echarts'`，
// 会把整个 ECharts 打进包体（约 1MB）。统一走 utils/echarts.js 的按需注册。
import echarts, { baseOption } from '@/utils/echarts'
import { useRouter, useRoute } from 'vue-router'
import { Download, Refresh } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  examGrade,
  exportExamGrade,
  getExportTask,
  pageExams,
  submitGradeExportTask,
} from '@/api/exam'
import { saveBlob } from '@/utils/download'
import { useChartTheme } from '@/utils/chartTheme'

/** 同步导出的条数上限：超过就走异步任务，避免慢请求 */
const SYNC_EXPORT_LIMIT = 100

const router = useRouter()
const studentKeyword = ref('')
const visibleItems = computed(() =>
  (grade.value.items || []).filter(
    (r) =>
      !studentKeyword.value ||
      (r.studentName + ' ' + r.username).toLowerCase().includes(studentKeyword.value.toLowerCase()),
  ),
)
const route = useRoute()
// 颜色必须取 Token 的**实际值**：ECharts 画在 canvas 上，option 里的 var(--zk-x) 不会被 CSS 解析
const { colors } = useChartTheme()

const loading = ref(false)
const exporting = ref(false)
const err = ref('')
const exams = ref([])
const examId = ref(null)
const grade = ref({})
const chartRef = ref(null)
let chart = null
let gradeRequest = 0

async function load() {
  if (!examId.value) {
    grade.value = {}
    return
  }
  loading.value = true
  const request = ++gradeRequest
  grade.value = {}
  err.value = ''
  try {
    const data = await examGrade(examId.value)
    if (request !== gradeRequest) return
    grade.value = data
    await nextTick()
    renderChart()
  } catch (e) {
    if (request === gradeRequest) err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    if (request === gradeRequest) loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) {
    return
  }
  chart = chart || echarts.init(chartRef.value)
  const distribution = grade.value.distribution || []
  chart.setOption({
    ...baseOption(),
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 40, containLabel: true },
    xAxis: {
      type: 'category',
      data: distribution.map((item) => item.label),
      axisLabel: { fontSize: 11 },
    },
    yAxis: { type: 'value', name: '人数', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: distribution.map((item) => item.count),
        barMaxWidth: 42,
        itemStyle: { color: colors.value.primary, borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top' },
      },
    ],
  })
}

/**
 * 导出：超过阈值走异步任务 + 轮询，小规模直接同步下载。
 *
 * 为什么分两条路：一条慢请求占着连接转圈时，用户既不知道还要多久，
 * 也没法取消；异步后至少能给出「生成中」的确定反馈。
 * 但几十人的场次走异步反而要多等一次轮询，所以保留同步链路。
 */
async function handleExport() {
  if (exporting.value) return
  const count = grade.value.items?.length || 0
  exporting.value = true
  try {
    if (count <= SYNC_EXPORT_LIMIT) {
      const blob = await exportExamGrade(examId.value)
      saveBlob(blob, `${grade.value.examName || '考试'}-成绩.xlsx`)
      return
    }
    const task = await submitGradeExportTask(examId.value)
    const done = await pollExportTask(task.id)
    if (done && done.downloadUrl) {
      window.open(done.downloadUrl, '_blank')
      ElMessage.success('导出完成，已打开下载')
    } else {
      ElMessage.error(done?.errorMsg || '导出失败，请重试')
    }
  } finally {
    exporting.value = false
  }
}

/** 轮询导出任务：每 1 秒问一次，最多 30 次；超时给用户明确结论而不是一直转圈 */
async function pollExportTask(taskId) {
  for (let i = 0; i < 30; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1000))
    const task = await getExportTask(taskId)
    if (task.status !== 0) {
      return task
    }
  }
  return { status: 2, errorMsg: '导出超时，请稍后在文件中心查看' }
}

// 容器尺寸变化时重绘，避免图表被压扁。
// 用 ResizeObserver 而不是 window.resize：侧边栏折叠时窗口尺寸不变，但图表容器变窄了。
let observer = null

watch(chartRef, (el) => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (el && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => {
      if (chart) chart.resize()
    })
    observer.observe(el)
  }
})

// 换肤后重绘：柱色是从 Token 读出来的实际色值，主题变了就得重新取一次
watch(colors, () => renderChart())

onMounted(async () => {
  try {
    const res = await pageExams({ pageNum: 1, pageSize: 100 })
    exams.value = res.records || []
    if (route.query.examId) {
      examId.value = Number(route.query.examId)
      await load()
    } else if (exams.value.length) {
      examId.value = exams.value[0].id
      await load()
    }
  } catch (e) {
    err.value = e.msg || e.message || '考试列表加载失败'
  }
  // 这里不再加 window.resize 监听：上面的 ResizeObserver 已经覆盖容器尺寸变化
  // （包括窗口缩放），而匿名监听函数无法 removeEventListener，切页会累积。
})

// 卸载清理：ResizeObserver 与 ECharts 实例都必须释放，否则切页会累积内存
onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>
