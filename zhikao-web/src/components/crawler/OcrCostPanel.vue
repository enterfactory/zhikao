<template>
  <div class="ocr-cost">
    <!-- ===== 工具条：时间窗 + 刷新 + 口径脚注 ===== -->
    <div class="ocr-cost__bar">
      <el-radio-group v-model="days" size="small" @change="load()">
        <el-radio-button v-for="item in WINDOWS" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="ocr-cost__bar-right">
        <span v-if="updatedAt" class="ocr-cost__stamp zk-text-sm">更新于 {{ updatedAt }}</span>
        <el-button text size="small" :icon="Refresh" :loading="loading" @click="load()">刷新</el-button>
      </div>
    </div>

    <SkeletonBlock v-if="loading && !summary" :rows="6" />

    <p v-else-if="error" class="ocr-cost__error">
      {{ error }}
      <el-button text size="small" @click="load()">重试</el-button>
    </p>

    <EmptyState
      v-else-if="isEmpty"
      title="窗口内没有采集文档"
      description="先上传或导入一份文档，识别通道、级联逐层与云端成本的数据才会出现在这里"
    />

    <template v-else>
      <!-- ===== 关键读数：一眼看出「这份面板在说什么」 ===== -->
      <section class="ocr-metrics">
        <div v-for="metric in metrics" :key="metric.key" class="ocr-metric" :class="`is-${metric.tone}`">
          <div class="ocr-metric__label">{{ metric.label }}</div>
          <div class="ocr-metric__value">
            {{ metric.value }}<span v-if="metric.unit" class="ocr-metric__unit">{{ metric.unit }}</span>
          </div>
          <div class="ocr-metric__sub">{{ metric.sub }}</div>
        </div>
      </section>

      <div class="ocr-cost__row">
        <!-- ===== 级联：候选 → 本地 → 升级云端 → 仍未认出 ===== -->
        <section class="ocr-panel">
          <header class="ocr-panel__head">
            <div>
              <h3 class="ocr-panel__title">级联逐层</h3>
              <p class="ocr-panel__desc">每一层只处理上一层兜不住的片段；条长按候选总数归一</p>
            </div>
          </header>

          <ul class="ocr-funnel">
            <li v-for="layer in layers" :key="layer.key" class="ocr-layer">
              <div class="ocr-layer__head">
                <span class="ocr-layer__name">{{ layer.name }}</span>
                <span class="ocr-layer__count">{{ layer.count }}</span>
                <span class="ocr-layer__rate">{{ layer.rateText }}</span>
              </div>
              <div class="ocr-layer__track">
                <div class="ocr-layer__fill" :class="`is-${layer.tone}`" :style="{ width: layer.width }" />
              </div>
              <p class="ocr-layer__note">{{ layer.note }}</p>
            </li>
          </ul>

          <p class="ocr-panel__foot">
            <el-icon><InfoFilled /></el-icon>
            {{ summary.thresholdNote || '口径：升级阈值与成本闸读自后端当前生效配置' }}
          </p>
        </section>

        <!-- ===== 取字来源 × 通道分布 ===== -->
        <section class="ocr-panel">
          <header class="ocr-panel__head">
            <div>
              <h3 class="ocr-panel__title">取字来源与通道</h3>
              <p class="ocr-panel__desc">文本层直取不花 OCR 成本；扫描件与片段级重认才会走识别通道</p>
            </div>
          </header>

          <ul class="ocr-channels">
            <li v-for="bucket in channelRows" :key="bucket.key" class="ocr-channel">
              <div class="ocr-channel__head">
                <span class="ocr-channel__label">{{ bucket.label }}</span>
                <span class="ocr-channel__sub zk-text-sm">{{ bucket.subLabel }}</span>
              </div>
              <div class="ocr-channel__track">
                <div class="ocr-channel__fill" :class="`is-${bucket.tone}`" :style="{ width: bucket.width }" />
              </div>
              <div class="ocr-channel__count">{{ bucket.docs }} 份 · {{ bucket.ratioText }}</div>
            </li>
          </ul>

          <div class="ocr-quality">
            <div class="ocr-quality__title">自动质量分</div>
            <div class="ocr-quality__bars">
              <div
                v-for="band in qualityBands"
                :key="band.key"
                class="ocr-quality__band"
                :class="`is-${band.tone}`"
                :style="{ flexGrow: band.docs || 0.02 }"
                :title="`${band.name} ${band.docs} 份`"
              >
                <span class="ocr-quality__num">{{ band.docs }}</span>
              </div>
            </div>
            <div class="ocr-quality__legend zk-text-sm">
              <span v-for="band in qualityBands" :key="band.key">
                <i :class="`is-${band.tone}`" />{{ band.name }} {{ band.docs }}
              </span>
              <span class="ocr-quality__avg">均值 {{ avgQualityText }}</span>
            </div>
          </div>
        </section>
      </div>

      <!-- ===== 走势 ===== -->
      <ChartCard
        class="ocr-cost__chart"
        title="级联走势"
        description="按天：文档数（柱）· 公式片段插回率（左轴 %）· 云端调用次数（右轴）"
        :option="trendOption"
        :height="288"
        :is-empty="!hasTrend"
        empty-title="窗口内没有可做趋势的按天数据"
        empty-description="至少采集一天以上、且做过公式片段识别，走势才有意义"
      />

      <!-- ===== 待人工关注 ===== -->
      <section class="ocr-panel ocr-cost__watch">
        <header class="ocr-panel__head">
          <div>
            <h3 class="ocr-panel__title">最需要人工看的文档</h3>
            <p class="ocr-panel__desc">
              按「未认出片段数」倒序。标了<span class="ocr-watch__capflag">额度用满</span>的文档，
              剩下的未认出是没继续升级云端（成本闸），不是认不出——要不要补采由你决定
            </p>
          </div>
        </header>

        <el-table
          :data="summary.watchlist || []"
          size="small"
          class="ocr-watch"
          @row-click="(row) => inspect(row)"
        >
          <el-table-column label="文档" min-width="220">
            <template #default="{ row }">
              <span class="ocr-watch__name">{{ row.docName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="候选" prop="candidates" width="72" align="right" />
          <el-table-column label="插回" width="88" align="right">
            <template #default="{ row }">
              <span class="ocr-watch__num">{{ row.recovered }}</span>
              <span class="ocr-watch__split">本地 {{ row.localRecovered || 0 }} · 云端 {{ row.cloudRecovered || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="未认出" width="104" align="right">
            <template #default="{ row }">
              <span class="ocr-watch__miss">{{ row.unresolved }}</span>
              <span v-if="row.capHit" class="ocr-watch__capflag">额度用满</span>
            </template>
          </el-table-column>
          <el-table-column label="云端调用" width="96" align="right">
            <template #default="{ row }">{{ row.cloudCalls || 0 }} / {{ summary.cloudCallCap }}</template>
          </el-table-column>
          <el-table-column label="质量分" width="88" align="right">
            <template #default="{ row }">{{ row.qualityScore ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="解析" width="120">
            <template #default="{ row }">{{ row.parserName || '未认领' }}</template>
          </el-table-column>
          <el-table-column label="结果说明" min-width="200">
            <template #default="{ row }">
              <span class="ocr-watch__note zk-ellipsis" :title="row.parseNote || ''">
                {{ row.parseNote || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" align="right" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="inspect(row)">
                详情
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <span class="zk-text-sm zk-text-secondary">窗口内没有做过公式片段识别的文档</span>
          </template>
        </el-table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import { crawlerOcrSummary } from '@/api/crawler'
import ChartCard from '@/components/ChartCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { useChartTheme } from '@/utils/chartTheme'

/**
 * 「识别与成本」概览（S3）。
 *
 * 这块面板的存在理由只有一个：S2 把逐层计数落到了每一份文档上，
 * 单文档看数字、全局看比率，两者对不上就说明留痕有问题。所以面板上的每个数字
 * 都能回到解析日志的某一行，而不是另算一套口径。
 *
 * 图表颜色必须走 useChartTheme：ECharts 画在 canvas 上，option 里写 var(--zk-primary)
 * 不会经 CSS 解析（三期首页就是这么静默失效的）。
 */
const emit = defineEmits(['inspect'])

/**
 * 交给父页打开「文档解析详情」弹窗。
 * 只传 id 与文档名：本面板的 WatchDoc 字段名与解析日志的 CrawlDocumentVO 不是同一套
 * （candidates vs formulaCandidates），把整行塞过去会在详情加载完成前闪一屏「未留痕」。
 */
function inspect(row) {
  emit('inspect', { id: row.id, docName: row.docName })
}

const WINDOWS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
  { label: '全部', value: 0 }
]

const { colors, axis } = useChartTheme()

const days = ref(30)
const summary = ref(null)
const loading = ref(false)
const error = ref('')
const updatedAt = ref('')

async function load() {
  loading.value = true
  try {
    summary.value = await crawlerOcrSummary(days.value)
    error.value = ''
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e) {
    error.value = e?.message || '识别与成本统计暂不可用'
  } finally {
    loading.value = false
  }
}

onMounted(load)

defineExpose({ reload: load })

// ==================== 读数 ====================

/** 比率（0~1）→ 百分比文本；分母为 0 时后端给 null，这里必须显示「—」而不是 0% */
function pct(value, digits = 1) {
  if (value === null || value === undefined) return '—'
  return `${(Number(value) * 100).toFixed(digits)}%`
}

const isEmpty = computed(() => !summary.value || summary.value.empty === true)

const metrics = computed(() => {
  const s = summary.value
  if (!s) return []
  const traced = (s.candidates || 0) > 0
  return [
    {
      key: 'docs',
      label: '窗口内文档',
      value: s.docTotal ?? 0,
      unit: ' 份',
      sub: `文本层 ${s.textLayerDocs ?? 0} · 扫描件 ${s.scanDocs ?? 0}`,
      tone: 'primary'
    },
    {
      key: 'formula-docs',
      label: '做过公式片段重认',
      value: s.formulaDocs ?? 0,
      unit: ' 份',
      sub: `候选片段 ${s.candidates ?? 0} 处`,
      tone: 'primary'
    },
    {
      key: 'recovery',
      label: '总插回率',
      value: traced ? Number(((s.recoveryRate || 0) * 100).toFixed(1)) : '—',
      unit: traced ? '%' : '',
      sub: `插回 ${s.recovered ?? 0} / 候选 ${s.candidates ?? 0}`,
      tone: traced && (s.recoveryRate || 0) >= 0.7 ? 'success' : 'warning'
    },
    {
      key: 'local',
      label: '本地层命中',
      value: traced ? Number(((s.localRate || 0) * 100).toFixed(1)) : '—',
      unit: traced ? '%' : '',
      sub: `本地认出 ${s.localRecovered ?? 0} 处（免费）`,
      tone: 'info'
    },
    {
      key: 'escalate',
      label: '升级率',
      value: traced ? Number(((s.escalateRate || 0) * 100).toFixed(1)) : '—',
      unit: traced ? '%' : '',
      sub: `云端调用 ${s.cloudCalls ?? 0} 次 · 顶满成本闸 ${s.capHitDocs ?? 0} 份`,
      tone: 'warning'
    },
    {
      key: 'unresolved',
      label: '仍未认出',
      value: s.unresolved ?? 0,
      unit: ' 处',
      sub: `云端层命中 ${pct(s.cloudRate)}`,
      tone: (s.unresolved || 0) > 0 ? 'danger' : 'success'
    }
  ]
})

// ==================== 级联漏斗 ====================

const layers = computed(() => {
  const s = summary.value
  const base = Math.max(1, Number(s?.candidates) || 0)
  const width = (n) => `${(Math.min(1, (Number(n) || 0) / base) * 100).toFixed(1)}%`
  return [
    {
      key: 'candidates',
      name: '发现候选',
      count: s?.candidates ?? 0,
      rateText: `${s?.formulaDocs ?? 0} 份文档`,
      width: '100%',
      tone: 'neutral',
      note: `尺寸窗内的内联小图，${s?.docTotal ?? 0} 份文档里共 ${s?.formulaDocs ?? 0} 份有`
    },
    {
      key: 'local',
      name: 'L1 本地 RapidOCR',
      count: s?.localRecovered ?? 0,
      rateText: pct(s?.localRate),
      width: width(s?.localRecovered),
      tone: 'info',
      note: '免费、离线；公式上偏弱，多数片段会因为框分不足升级'
    },
    {
      key: 'escalate',
      name: 'L2 升级云端',
      count: s?.cloudCalls ?? 0,
      rateText: pct(s?.escalateRate),
      width: width(s?.cloudCalls),
      tone: 'warning',
      note: `实际调用次数（按次计费），单文档上限 ${s?.cloudCallCap ?? '—'} 次`
    },
    {
      key: 'cloud',
      name: 'L2 云端认出',
      count: s?.cloudRecovered ?? 0,
      rateText: pct(s?.cloudRate),
      width: width(s?.cloudRecovered),
      tone: 'success',
      note: '命中数 / 调用数：这段差值就是花了钱没认出的部分'
    },
    {
      key: 'unresolved',
      name: '仍未认出',
      count: s?.unresolved ?? 0,
      rateText: `${s?.capHitDocs ?? 0} 份额度用满`,
      width: width(s?.unresolved),
      tone: 'danger',
      note: '正文里仍是空槽，靠「空槽嫌疑」与下方文档榜定位到人'
    }
  ]
})

// ==================== 通道分布 ====================

const channelRows = computed(() => {
  const buckets = summary.value?.channels || []
  const max = Math.max(1, ...buckets.map((b) => Number(b.docs) || 0))
  return buckets.map((bucket) => ({
    key: bucket.key,
    label: bucket.label,
    subLabel: bucket.subLabel,
    docs: bucket.docs ?? 0,
    ratioText: pct(bucket.ratio, 0),
    width: `${((Number(bucket.docs) || 0) / max) * 100}%`,
    // 「本地 → 云端」是唯一一条既花钱又出效果的链路，给它单独上色
    tone: bucket.subLabel?.includes('云端')
      ? 'warning'
      : bucket.subLabel?.includes('仅本地')
        ? 'info'
        : // 后端文案是「文本层直取 / 扫描件整页 OCR」，按前缀判：写死全名会让配色静默退成灰
          (bucket.label || '').startsWith('扫描件') ? 'info' : 'neutral'
  }))
})

const qualityBands = computed(() => {
  const q = summary.value?.quality || {}
  return [
    { key: 'high', name: '≥80', docs: q.highDocs || 0, tone: 'success' },
    { key: 'mid', name: '60-79', docs: q.midDocs || 0, tone: 'warning' },
    { key: 'low', name: '<60', docs: q.lowDocs || 0, tone: 'danger' },
    { key: 'unscored', name: '未打分', docs: q.unscoredDocs || 0, tone: 'neutral' }
  ]
})

const avgQualityText = computed(() => {
  const v = summary.value?.qualityAvg
  return v === null || v === undefined ? '—' : Number(v).toFixed(1)
})

// ==================== 走势 ====================

const hasTrend = computed(() => (summary.value?.daily || []).length > 0)

const trendOption = computed(() => {
  const daily = summary.value?.daily || []
  const dates = daily.map((item) => item.date)
  const rateSeries = daily.map((item) =>
    item.recoveryRate === null || item.recoveryRate === undefined
      ? null
      : Number((item.recoveryRate * 100).toFixed(1))
  )
  const grid = { left: 44, right: 48, top: 36, bottom: 30 }
  const axisLabel = { color: axis.value.labelColor, fontSize: 11 }
  return {
    color: [colors.value.primary, colors.value.success, colors.value.warning],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: ['文档数', '插回率', '云端调用'],
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11, color: colors.value.textSecondary }
    },
    grid,
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: axis.value.lineColor } },
      axisTick: { show: false },
      axisLabel
    },
    yAxis: [
      {
        type: 'value',
        name: '份 / 次',
        nameTextStyle: { fontSize: 11, color: colors.value.textSecondary },
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel,
        splitLine: { lineStyle: { color: axis.value.splitColor, type: 'dashed' } }
      },
      {
        type: 'value',
        name: '插回率 %',
        max: 100,
        nameTextStyle: { fontSize: 11, color: colors.value.textSecondary },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { ...axisLabel, formatter: '{value}%' },
        splitLine: { show: false }
      }
    ],
    series: [
      { name: '文档数', type: 'bar', barMaxWidth: 18, data: daily.map((d) => d.docs || 0) },
      {
        name: '插回率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 6,
        connectNulls: false,
        data: rateSeries
      },
      {
        name: '云端调用',
        type: 'bar',
        barMaxWidth: 10,
        itemStyle: { color: colors.value.warning, opacity: 0.75 },
        data: daily.map((d) => d.cloudCalls || 0)
      }
    ]
  }
})
</script>

<style scoped>
.ocr-cost {
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-4);
}

.ocr-cost__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
  flex-wrap: wrap;
}

.ocr-cost__bar-right {
  display: flex;
  align-items: center;
  gap: var(--zk-space-3);
}

.ocr-cost__stamp {
  color: var(--zk-text-placeholder);
}

.ocr-cost__error {
  margin: 0;
  font-size: var(--zk-font-sm);
  color: var(--zk-danger);
}

/* ===== 指标读数 ===== */
.ocr-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(196px, 1fr));
  gap: var(--zk-space-3);
}

.ocr-metric {
  position: relative;
  padding: var(--zk-space-4);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  overflow: hidden;
}

.ocr-metric::after {
  content: '';
  position: absolute;
  right: var(--zk-space-4);
  top: var(--zk-space-4);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zk-text-placeholder);
}

.ocr-metric.is-primary::after {
  background: var(--zk-primary);
}

.ocr-metric.is-success::after {
  background: var(--zk-success);
}

.ocr-metric.is-warning::after {
  background: var(--zk-warning);
}

.ocr-metric.is-danger::after {
  background: var(--zk-danger);
}

.ocr-metric.is-info::after {
  background: var(--zk-info);
}

.ocr-metric__label {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-secondary);
}

.ocr-metric__value {
  margin-top: 2px;
  font-size: var(--zk-font-xl);
  font-weight: 600;
  line-height: 1.35;
  color: var(--zk-text-primary);
  font-variant-numeric: tabular-nums;
}

.ocr-metric__unit {
  font-size: var(--zk-font-sm);
  font-weight: 500;
  color: var(--zk-text-secondary);
}

.ocr-metric__sub {
  margin-top: 2px;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

/* ===== 面板 ===== */
.ocr-cost__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--zk-space-4);
}

@media (max-width: 1100px) {
  .ocr-cost__row {
    grid-template-columns: minmax(0, 1fr);
  }
}

.ocr-panel {
  padding: var(--zk-space-4) var(--zk-space-5);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
}

.ocr-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zk-space-3);
  margin-bottom: var(--zk-space-4);
}

.ocr-panel__title {
  margin: 0;
  font-size: var(--zk-font-md);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.ocr-panel__desc {
  margin: 2px 0 0;
  font-size: var(--zk-font-xs);
  line-height: 1.6;
  color: var(--zk-text-placeholder);
}

.ocr-panel__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: var(--zk-space-4) 0 0;
  padding-top: var(--zk-space-3);
  border-top: 1px dashed var(--zk-border-light);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
}

/* ===== 级联条 ===== */
.ocr-funnel,
.ocr-channels {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-3);
}

.ocr-layer__head,
.ocr-channel__head {
  display: flex;
  align-items: baseline;
  gap: var(--zk-space-2);
  margin-bottom: 4px;
}

.ocr-layer__name,
.ocr-channel__label {
  font-size: var(--zk-font-sm);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.ocr-channel__sub {
  color: var(--zk-text-placeholder);
}

.ocr-layer__count {
  margin-left: auto;
  font-size: var(--zk-font-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ocr-layer__rate,
.ocr-channel__count {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
  font-variant-numeric: tabular-nums;
}

.ocr-layer__rate {
  min-width: 52px;
  text-align: right;
}

.ocr-layer__track,
.ocr-channel__track {
  height: 6px;
  border-radius: var(--zk-radius-round);
  background: var(--zk-bg-page);
  overflow: hidden;
}

.ocr-layer__fill,
.ocr-channel__fill {
  height: 100%;
  border-radius: var(--zk-radius-round);
  transition: width var(--zk-duration-slow) var(--zk-ease);
}

.ocr-layer__fill.is-neutral,
.ocr-channel__fill.is-neutral {
  background: var(--zk-text-placeholder);
}

.ocr-layer__fill.is-info,
.ocr-channel__fill.is-info,
.ocr-quality__band.is-info {
  background: var(--zk-info);
}

.ocr-layer__fill.is-success,
.ocr-channel__fill.is-success,
.ocr-quality__band.is-success {
  background: var(--zk-success);
}

.ocr-layer__fill.is-warning,
.ocr-channel__fill.is-warning,
.ocr-quality__band.is-warning {
  background: var(--zk-warning);
}

.ocr-layer__fill.is-danger,
.ocr-channel__fill.is-danger,
.ocr-quality__band.is-danger {
  background: var(--zk-danger);
}

.ocr-channel__fill.is-neutral {
  background: var(--zk-primary-light);
}

.ocr-channel__count {
  margin-top: 4px;
  text-align: right;
}

.ocr-layer__note {
  margin: 4px 0 0;
  font-size: var(--zk-font-xs);
  line-height: 1.6;
  color: var(--zk-text-placeholder);
}

/* ===== 质量分档 ===== */
.ocr-quality {
  margin-top: var(--zk-space-5);
  padding-top: var(--zk-space-4);
  border-top: 1px dashed var(--zk-border-light);
}

.ocr-quality__title {
  font-size: var(--zk-font-sm);
  font-weight: 600;
  color: var(--zk-text-primary);
  margin-bottom: var(--zk-space-2);
}

.ocr-quality__bars {
  display: flex;
  gap: 3px;
  height: 26px;
}

.ocr-quality__band {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  border-radius: var(--zk-radius-sm);
  color: var(--zk-text-inverse);
  opacity: 0.88;
}

.ocr-quality__band.is-neutral {
  color: var(--zk-text-regular);
  background: var(--zk-bg-page);
  border: 1px dashed var(--zk-border);
}

.ocr-quality__num {
  font-size: var(--zk-font-xs);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ocr-quality__legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zk-space-3);
  margin-top: var(--zk-space-2);
  color: var(--zk-text-secondary);
}

.ocr-quality__legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-radius: 2px;
  vertical-align: middle;
}

.ocr-quality__legend i.is-success {
  background: var(--zk-success);
}

.ocr-quality__legend i.is-warning {
  background: var(--zk-warning);
}

.ocr-quality__legend i.is-danger {
  background: var(--zk-danger);
}

.ocr-quality__legend i.is-neutral {
  background: var(--zk-border);
}

.ocr-quality__avg {
  margin-left: auto;
  color: var(--zk-text-primary);
  font-weight: 600;
}

/* ===== 待关注文档 ===== */
.ocr-cost__watch {
  padding-bottom: var(--zk-space-3);
}

.ocr-watch :deep(.el-table__row) {
  cursor: pointer;
}

.ocr-watch__name {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-primary);
}

.ocr-watch__num {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ocr-watch__split {
  margin-left: var(--zk-space-2);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.ocr-watch__miss {
  margin-right: var(--zk-space-2);
  font-weight: 600;
  color: var(--zk-danger);
  font-variant-numeric: tabular-nums;
}

.ocr-watch__capflag {
  padding: 0 4px;
  margin-left: 4px;
  font-size: var(--zk-font-xs);
  color: var(--zk-warning);
  border: 1px solid var(--zk-warning-light, var(--zk-border));
  border-radius: var(--zk-radius-sm);
}

.ocr-watch__note {
  display: inline-block;
  max-width: 100%;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
}
</style>
