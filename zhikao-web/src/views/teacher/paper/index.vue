<template>
  <AppPage title="组卷管理" description="组织题目、调整分值，为考试准备可发布的试卷。">
    <template #actions>
      <template v-if="!genVisible">
        <el-button @click="openGen('random')">规则组卷</el-button>
        <el-button type="primary" @click="openGen('manual')">手动组卷</el-button>
      </template>
      <template v-else>
        <el-button :disabled="genSaving" @click="closeEditor">返回列表</el-button>
        <el-button type="primary" :loading="genSaving" @click="submitGen">保存试卷</el-button>
      </template>
    </template>
    <template v-if="!genVisible">
      <section class="console-panel">
        <div class="console-workspace-tabs">
          <button
            :class="{ active: query.status === null }"
            @click="
              () => {
                query.status = null
                search()
              }
            "
          >
            全部试卷
          </button>
          <button
            :class="{ active: query.status === 0 }"
            @click="
              () => {
                query.status = 0
                search()
              }
            "
          >
            草稿
          </button>
          <button
            :class="{ active: query.status === 1 }"
            @click="
              () => {
                query.status = 1
                search()
              }
            "
          >
            已发布
          </button>
        </div>
        <div class="console-toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索试卷名称"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.subjectId" placeholder="全部科目" clearable @change="search">
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
          </el-select>
          <el-button @click="search">查询</el-button>
        </div>
        <div v-if="selection.length" class="console-bulk-bar">
          已选 {{ selection.length }} 份
          <el-button text type="danger" @click="handleBatchDelete">批量删除</el-button>
        </div>
        <DataTable
          :rows="rows"
          :total="total"
          :loading="loading"
          :error="err"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          selectable
          :selectable-fn="(row) => row.status === 0"
          @selection-change="selection = $event"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @retry="load"
        >
          <el-table-column label="试卷名称" min-width="230">
            <template #default="{ row }">
              <div class="console-cell-title">{{ row.paperName }}</div>
              <div class="console-cell-note">{{ row.createTime }} · #{{ row.id }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="subjectName" label="科目" min-width="120" />
          <el-table-column label="题量 / 总分" width="135">
            <template #default="{ row }">
              {{ row.questionCount }} 题 / {{ row.totalScore }} 分
            </template>
          </el-table-column>
          <el-table-column label="时长" width="100">
            <template #default="{ row }">{{ row.totalDuration }} 分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '已发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button text type="primary" @click="preview(row)">预览</el-button>
              <el-button text type="primary" @click="toggleStatus(row)">
                {{ row.status === 1 ? '撤回' : '发布' }}
              </el-button>
              <el-button v-if="row.status === 0" text type="danger" @click="handleDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </DataTable>
      </section>
    </template>
    <div v-else class="paper-workspace">
      <section class="console-panel">
        <header class="console-panel-head">
          <h3>{{ genMode === 'manual' ? '题目编排' : '抽题规则' }}</h3>
          <el-button
            v-if="genMode === 'manual'"
            :disabled="!gen.subjectId"
            @click="
              () => {
                pickerVisible = true
                loadPicker()
              }
            "
          >
            从题库选题
          </el-button>
          <el-button v-else @click="addRule">添加规则</el-button>
        </header>
        <template v-if="genMode === 'manual'">
          <el-empty v-if="!pickedRows.length" description="选择科目后，从题库加入题目" />
          <article v-for="(q, i) in pickedRows" :key="q.id" class="console-question-block">
            <div class="console-summary-row">
              <strong>第 {{ i + 1 }} 题 · {{ q.typeName }}</strong>
              <div>
                <el-button text :disabled="i === 0" @click="moveQuestion(i, -1)">上移</el-button>
                <el-button text :disabled="i === pickedRows.length - 1" @click="moveQuestion(i, 1)">
                  下移
                </el-button>
                <el-button text type="danger" @click="pickedRows.splice(i, 1)">移除</el-button>
              </div>
            </div>
            <RichContent :html="q.content" />
            <div class="console-summary-row">
              <span class="console-cell-note">{{ q.knowledgePoint || '未设置知识点' }}</span>
              <label>
                本题分值
                <el-input-number
                  v-model="q.pickScore"
                  :min="0.5"
                  :step="0.5"
                  :precision="2"
                  size="small"
                />
              </label>
            </div>
          </article>
        </template>
        <div v-else class="console-panel-body">
          <div v-for="(rule, i) in gen.rules" :key="i" class="paper-rule">
            <el-select v-model="rule.type" aria-label="题型">
              <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <label>
              数量
              <el-input-number v-model="rule.count" :min="1" :max="100" />
            </label>
            <label>
              分值
              <el-input-number v-model="rule.score" :min="0.5" :step="0.5" />
            </label>
            <el-select v-model="rule.difficulty" clearable placeholder="不限难度">
              <el-option v-for="d in 5" :key="d" :value="d" :label="'难度 ' + d" />
            </el-select>
            <el-button
              text
              type="danger"
              :disabled="gen.rules.length === 1"
              @click="gen.rules.splice(i, 1)"
            >
              移除
            </el-button>
          </div>
        </div>
      </section>
      <aside class="console-panel">
        <header class="console-panel-head">试卷设置</header>
        <el-form label-position="top" class="console-panel-body">
          <el-form-item label="试卷名称" required>
            <el-input v-model="gen.paperName" maxlength="100" placeholder="如：第一章单元测验" />
          </el-form-item>
          <el-form-item label="所属科目" required>
            <el-select v-model="gen.subjectId" @change="onGenSubjectChange">
              <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="建议时长（分钟）">
            <el-input-number v-model="gen.totalDuration" :min="1" :max="600" />
          </el-form-item>
          <div class="console-answer-box">
            <p>
              共
              {{
                genMode === 'manual'
                  ? pickedRows.length
                  : gen.rules.reduce((s, r) => s + r.count, 0)
              }}
              题
            </p>
            <strong>
              {{
                genMode === 'manual'
                  ? pickedTotal
                  : gen.rules.reduce((s, r) => s + r.count * r.score, 0)
              }}
              分
            </strong>
          </div>
          <p class="console-form-hint">保存后为草稿。请预览并核对题目后发布。</p>
        </el-form>
      </aside>
    </div>
    <ZkDrawer v-model="pickerVisible" title="从题库选题" size="xl">
      <div class="console-toolbar">
        <el-input
          v-model="pickKeyword"
          placeholder="搜索题干或知识点"
          clearable
          @keyup.enter="
            () => {
              pickPage = 1
              loadPicker()
            }
          "
        />
        <el-button
          @click="
            () => {
              pickPage = 1
              loadPicker()
            }
          "
        >
          搜索
        </el-button>
        <span>已选 {{ pickedRows.length }} 题</span>
      </div>
      <el-table :data="pickQuestions" v-loading="pickLoading">
        <el-table-column label="题目" min-width="280">
          <template #default="{ row }">
            <RichContent :html="row.content" />
            <div class="console-cell-note">
              {{ row.typeName }} · {{ row.knowledgePoint }} · {{ row.score }} 分
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button text :type="isPicked(row) ? 'danger' : 'primary'" @click="togglePick(row)">
              {{ isPicked(row) ? '移除' : '加入' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pickPage"
        :page-size="20"
        :total="pickTotal"
        layout="total, prev, pager, next"
        @current-change="loadPicker"
      />
    </ZkDrawer>
    <ZkDrawer v-model="previewVisible" title="试卷预览" size="xl">
      <template v-if="previewData.paper">
        <h2>{{ previewData.paper.paperName }}</h2>
        <p class="console-question-meta">
          {{ previewData.paper.totalScore }} 分 · {{ previewData.paper.totalDuration }} 分钟
        </p>
        <article
          v-for="(q, i) in previewData.questions"
          :key="q.id || i"
          class="console-question-block"
        >
          <h4>第 {{ i + 1 }} 题 · {{ q.score }} 分</h4>
          <RichContent :html="q.content" />
          <div class="console-option-grid">
            <div v-for="(text, key) in q.options" :key="key">
              {{ key }}.
              <RichContent :html="text" inline />
            </div>
          </div>
          <div class="console-answer-box">
            <h4>参考答案</h4>
            <RichContent :html="q.answer" />
            <RichContent :html="q.analysis" />
          </div>
        </article>
      </template>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Delete,
  DocumentAdd,
  MagicStick,
  Plus,
  Promotion,
  Refresh,
  RefreshRight,
  Search,
  View,
} from '@element-plus/icons-vue'
import RichContent from '@/components/RichContent.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import {
  deletePaper,
  genManualPaper,
  genRandomPaper,
  getPaper,
  getQuestion,
  pagePapers,
  pageQuestions,
  updatePaperStatus,
} from '@/api/exam'
import { listSubjects } from '@/api/system'

const route = useRoute()
const TYPES = [
  { value: 1, label: '单选' },
  { value: 2, label: '多选' },
  { value: 3, label: '判断' },
  { value: 4, label: '填空' },
  { value: 5, label: '简答' },
]
const PICK_PAGE_SIZE = 100

const loading = ref(false)
const err = ref('')
const rows = ref([])
const total = ref(0)
const subjects = ref([])
const selection = ref([])
const query = ref({ pageNum: 1, pageSize: 10, subjectId: null, status: null, keyword: '' })

const genVisible = ref(false)
const genMode = ref('manual')
const genSaving = ref(false)
const gen = ref({ paperName: '', subjectId: null, totalDuration: 90, rules: [] })

const pickLoading = ref(false)
const pickQuestions = ref([])
const pickTableRef = ref()
const pickedRows = ref([])

const previewVisible = ref(false)
const previewData = ref({ paper: null, questions: [] })

const pickedTotal = computed(() =>
  pickedRows.value.reduce((sum, row) => sum + Number(row.pickScore || 0), 0).toFixed(2),
)

async function load() {
  loading.value = true
  try {
    const res = await pagePapers(query.value)
    rows.value = res.records || []
    total.value = res.total || 0
    err.value = ''
  } catch (e) {
    err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  err.value = ''
  load()
}

function resetQuery() {
  query.value = { pageNum: 1, pageSize: 10, subjectId: null, status: null, keyword: '' }
  err.value = ''
  load()
}

function onPageChange(pageNum) {
  query.value.pageNum = pageNum
  err.value = ''
  load()
}

function onSizeChange(pageSize) {
  query.value.pageSize = pageSize
  query.value.pageNum = 1
  err.value = ''
  load()
}

function openGen(mode) {
  genMode.value = mode
  gen.value = {
    paperName: '',
    subjectId: null,
    totalDuration: 90,
    rules: [{ type: 1, count: 5, score: 4, difficulty: null }],
  }
  pickQuestions.value = []
  pickedRows.value = []
  genVisible.value = true
}

/** 切换科目后加载候选题目（手工组卷用） */
async function onGenSubjectChange() {
  pickedRows.value = []
  pickPage.value = 1
  pickKeyword.value = ''
  await loadPicker()
}

function onSelectionChange(selectionRows) {
  pickedRows.value = selectionRows
}

function addRule() {
  gen.value.rules.push({ type: 1, count: 5, score: 4, difficulty: null })
}

async function submitGen() {
  if (!gen.value.paperName) {
    ElMessage.warning('请填写试卷名称')
    return
  }
  if (!gen.value.subjectId) {
    ElMessage.warning('请选择科目')
    return
  }
  if (
    genMode.value === 'manual' &&
    pickedRows.value.some((q) => !Number.isFinite(q.pickScore) || q.pickScore <= 0)
  ) {
    ElMessage.warning('请检查题目分值')
    return
  }
  genSaving.value = true
  try {
    if (genMode.value === 'manual') {
      if (!pickedRows.value.length) {
        ElMessage.warning('请至少勾选一道题目')
        return
      }
      await genManualPaper({
        paperName: gen.value.paperName,
        subjectId: gen.value.subjectId,
        totalDuration: gen.value.totalDuration,
        questions: pickedRows.value.map((row) => ({ questionId: row.id, score: row.pickScore })),
      })
    } else {
      await genRandomPaper({
        paperName: gen.value.paperName,
        subjectId: gen.value.subjectId,
        totalDuration: gen.value.totalDuration,
        rules: gen.value.rules,
      })
    }
    ElMessage.success('组卷成功')
    genVisible.value = false
    load()
  } finally {
    genSaving.value = false
  }
}

async function preview(row) {
  previewData.value = await getPaper(row.id)
  previewVisible.value = true
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  await updatePaperStatus(row.id, next)
  ElMessage.success(next === 1 ? '已发布' : '已撤回为草稿')
  load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除试卷「${row.paperName}」吗？`, '危险操作', {
    type: 'warning',
  })
  await deletePaper(row.id)
  ElMessage.success('删除成功')
  load()
}

// 后端没有批删接口：逐条删除并汇总结果；勾选已被 selectableFn 限制为草稿
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selection.value.length} 份草稿试卷吗？`,
      '危险操作',
      {
        type: 'warning',
      },
    )
  } catch (e) {
    return
  }
  const results = await Promise.allSettled(selection.value.map((row) => deletePaper(row.id)))
  const failed = results.filter((item) => item.status === 'rejected').length
  if (failed) {
    ElMessage.warning(`删除完成：成功 ${results.length - failed} 条，失败 ${failed} 条`)
  } else {
    ElMessage.success(`已删除 ${results.length} 份试卷`)
  }
  selection.value = []
  load()
}

onMounted(async () => {
  try {
    subjects.value = await listSubjects()
  } catch (e) {
    subjects.value = []
  }
  load()
  const ids = String(route.query.questions || '')
    .split(',')
    .filter((v) => /^\d+$/.test(v))
    .slice(0, 100)
  if (ids.length) {
    try {
      const selected = await Promise.all(ids.map(getQuestion))
      if (new Set(selected.map((q) => q.subjectId)).size !== 1)
        throw new Error('请使用同一科目的题目组卷')
      openGen('manual')
      gen.value.subjectId = selected[0].subjectId
      pickedRows.value = selected.map((q) => ({ ...q, pickScore: Number(q.score) || 5 }))
    } catch (e) {
      ElMessage.warning(e.msg || e.message || '选题加载失败')
    }
  }
})

const pickerVisible = ref(false),
  pickKeyword = ref(''),
  pickPage = ref(1),
  pickTotal = ref(0)
async function loadPicker() {
  if (!gen.value.subjectId) return
  pickLoading.value = true
  try {
    const r = await pageQuestions({
      pageNum: pickPage.value,
      pageSize: 20,
      subjectId: gen.value.subjectId,
      keyword: pickKeyword.value,
    })
    pickQuestions.value = r.records || []
    pickTotal.value = r.total || 0
  } finally {
    pickLoading.value = false
  }
}
function isPicked(q) {
  return pickedRows.value.some((x) => x.id === q.id)
}
function togglePick(q) {
  const i = pickedRows.value.findIndex((x) => x.id === q.id)
  if (i >= 0) pickedRows.value.splice(i, 1)
  else pickedRows.value.push({ ...q, pickScore: Number(q.score) || 5 })
}
function moveQuestion(i, d) {
  const a = pickedRows.value
  const [q] = a.splice(i, 1)
  a.splice(i + d, 0, q)
}
async function canLeave() {
  if (!genVisible.value || (!gen.value.paperName && !pickedRows.value.length)) return true
  return ElMessageBox.confirm('离开后将丢失当前未保存的组卷内容。', '放弃组卷', { type: 'warning' })
    .then(() => true)
    .catch(() => false)
}
async function closeEditor() {
  if (await canLeave()) genVisible.value = false
}
onBeforeRouteLeave(canLeave)
</script>
<style scoped>
.paper-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;
}
.paper-rule {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px 0;
  border-bottom: 1px solid #e8ebf0;
}
.paper-rule .el-select {
  width: 140px;
}
.paper-rule label {
  display: flex;
  align-items: center;
  gap: 8px;
}
@media (max-width: 1000px) {
  .paper-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
