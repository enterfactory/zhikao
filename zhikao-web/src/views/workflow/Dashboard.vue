<template>
  <div v-loading="loading">
    <div class="workspace-page-title">
      <div>
        <h1>教学工作台</h1>
        <p>从一份资料，到一次有反馈的考试。</p>
      </div>
      <el-button v-if="canImport" type="primary" @click="router.push('/resources/imports')">
        ＋ 导入教学资料
      </el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="business-metrics">
      <div v-for="m in metrics" :key="m.label">
        <span>{{ m.label }}</span>
        <strong>{{ m.value }}</strong>
        <small>{{ m.hint }}</small>
      </div>
    </div>
    <section class="business-panel">
      <div class="section-heading">
        <h2>教学流程</h2>
        <span class="muted">按步骤完成本次教学准备</span>
      </div>
      <div class="workflow-links">
        <router-link v-for="(w, i) in links" :key="w.path" :to="w.path">
          <span>{{ String(i + 1).padStart(2, '0') }}</span>
          <h3>{{ w.title }}</h3>
          <p>{{ w.note }}</p>
          <b>前往处理 →</b>
        </router-link>
      </div>
    </section>
    <section v-if="canImport" class="business-panel">
      <div class="section-heading">
        <h2>最近导入的资料</h2>
        <router-link to="/resources/imports">全部资料 →</router-link>
      </div>
      <el-table :data="docs">
        <el-table-column prop="docName" label="资料名称" min-width="240" />
        <el-table-column prop="parsedCount" label="候选题" width="100" />
        <el-table-column prop="parseNote" label="识别结果" min-width="230" show-overflow-tooltip />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="router.push({ path: '/resources/review', query: { document: row.id } })"
            >
              对照复核 →
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <section class="business-panel">
      <div class="section-heading">
        <h2>近期考试</h2>
        <router-link to="/teacher/exam">全部考试 →</router-link>
      </div>
      <el-table :data="exams">
        <el-table-column prop="examName" label="考试名称" min-width="220" />
        <el-table-column prop="className" label="班级" />
        <el-table-column prop="statusName" label="状态" />
        <el-table-column label="开考时间" min-width="180">
          <template #default="{ row }">
            {{ String(row.startTime || '').replace('T', ' ') }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="router.push({ path: '/teacher/monitor', query: { id: row.id } })"
            >
              监控与发布 →
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { pageExams, pageQuestions } from '@/api/exam'
import { crawlerStats, crawlerDocumentPage } from '@/api/crawler'
const router = useRouter(),
  user = useUserStore(),
  canImport = computed(() => user.permissions.includes('crawler:manage')),
  docs = ref([]),
  exams = ref([]),
  stats = ref({}),
  questions = ref(0),
  examTotal = ref(0),
  loading = ref(true),
  error = ref('')
const metrics = computed(() => [
  { label: '正式题库', value: questions.value, hint: '可用于组卷的题目' },
  {
    label: '待复核候选题',
    value: canImport.value
      ? Number(stats.value.TAGGED || 0) +
        Number(stats.value.CLEANED || 0) +
        Number(stats.value.PARSED || 0)
      : '—',
    hint: '完成对照后审核入库',
  },
  {
    label: '已入库候选题',
    value: canImport.value ? stats.value.PROMOTED || 0 : '—',
    hint: '来自资料识别与复核',
  },
  { label: '考试场次', value: examTotal.value, hint: '当前权限范围内' },
])
const links = computed(() =>
  (canImport.value
    ? [
        { path: '/resources/imports', title: '导入资料', note: 'PDF / Word 上传与识别' },
        { path: '/resources/review', title: '对照复核', note: '核对原文、题目与公式' },
      ]
    : []
  ).concat([
    { path: '/teacher/question', title: '维护题库', note: '管理正式题目与知识点' },
    { path: '/teacher/paper', title: '组卷发布', note: '选题组卷与安排考试' },
    { path: '/teacher/monitor', title: '考试与反馈', note: '监控、阅卷与发布解析' },
  ]),
)
onMounted(async () => {
  try {
    const [e, q] = await Promise.all([
      pageExams({ pageNum: 1, pageSize: 5 }),
      pageQuestions({ pageNum: 1, pageSize: 1 }),
    ])
    exams.value = e.records
    examTotal.value = e.total
    questions.value = q.total
    if (canImport.value) {
      const [s, d] = await Promise.all([crawlerStats(), crawlerDocumentPage({ pageSize: 5 })])
      stats.value = s
      docs.value = d.records
    }
  } catch (e) {
    error.value = e.message || '工作台加载失败'
  } finally {
    loading.value = false
  }
})
</script>
