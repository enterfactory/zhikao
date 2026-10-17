<template>
  <AppPage title="考试管理" description="安排考试、管理发布与作答过程。">
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openForm">创建考试</el-button>
    </template>
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
          全部考试
        </button>
        <button
          v-for="(label, i) in ['未开始', '进行中', '已结束']"
          :key="i"
          :class="{ active: query.status === i }"
          @click="
            () => {
              query.status = i
              search()
            }
          "
        >
          {{ label }}
        </button>
      </div>
      <div class="console-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索考试名称"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="query.classId"
          placeholder="全部班级"
          clearable
          filterable
          @change="search"
        >
          <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
        </el-select>
        <el-button :icon="Refresh" @click="search">刷新</el-button>
      </div>
      <div v-if="selection.length" class="console-bulk-bar">
        已选 {{ selection.length }} 场
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
        :selectable-fn="(row) => row.status !== 1"
        @selection-change="selection = $event"
        @page-change="onPageChange"
        @size-change="onSizeChange"
        @retry="load"
      >
        <el-table-column label="考试 / 试卷" min-width="250">
          <template #default="{ row }">
            <div class="console-cell-title">{{ row.examName }}</div>
            <div class="console-cell-note">
              {{ row.paperName || '试卷' }} · {{ row.duration }} 分钟
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="className" label="参考班级" min-width="140" />
        <el-table-column label="考试时间" min-width="180">
          <template #default="{ row }">
            {{ row.startTime }}
            <div class="console-cell-note">至 {{ row.endTime }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'primary' : row.status === 2 ? 'info' : 'warning'"
              size="small"
            >
              {{ row.statusName || ['未开始', '进行中', '已结束'][row.status] }}
            </el-tag>
            <div class="console-cell-note">{{ row.published === 1 ? '已发布' : '未发布' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.published !== 1 && row.status === 0"
              text
              type="primary"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              text
              type="primary"
              @click="router.push({ path: '/teacher/monitor', query: { examId: row.id } })"
            >
              监控
            </el-button>
            <el-dropdown @command="(c) => examAction(c, row)">
              <el-button text>
                更多
                <el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="grade">成绩统计</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="finish">
                    提前结束
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status !== 1" command="delete" divided>
                    删除考试
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </DataTable>
    </section>
    <ZkDrawer
      v-model="formVisible"
      title="创建考试"
      description="选择已发布试卷，确认参考班级与考试时间。"
      show-footer
      :loading="saving"
      confirm-text="创建考试"
      cancel-text="取消"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="考试名称" prop="examName">
          <el-input v-model="form.examName" placeholder="如 Java 期中考试" />
        </el-form-item>
        <el-form-item label="试卷" prop="paperId">
          <el-select
            v-model="form.paperId"
            class="control-full"
            placeholder="请选择已发布的试卷"
            filterable
          >
            <el-option
              v-for="p in publishedPapers"
              :key="p.id"
              :label="`${p.paperName}（${p.questionCount} 题 / ${p.totalScore} 分）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参考班级" prop="classId">
          <el-select
            v-model="form.classId"
            class="control-full"
            placeholder="请选择班级"
            filterable
          >
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            class="control-full"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            class="control-full"
          />
        </el-form-item>
        <el-form-item label="答题时长">
          <el-input-number v-model="form.duration" :min="10" :max="300" :step="10" />
          分钟
        </el-form-item>
        <el-form-item label="自动阅卷">
          <el-switch v-model="form.autoMark" :active-value="1" :inactive-value="0" />
          <span class="tip">开启后客观题交卷即判分</span>
        </el-form-item>
        <el-form-item label="防作弊">
          <el-switch v-model="form.cheatCheck" :active-value="1" :inactive-value="0" />
          <span class="tip">开启后记录切屏次数</span>
        </el-form-item>
      </el-form>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CircleClose,
  Delete,
  Monitor,
  Plus,
  Promotion,
  Refresh,
  RefreshRight,
  Search,
  TrendCharts,
} from '@element-plus/icons-vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import {
  createExam,
  deleteExam,
  finishExam,
  listPapers,
  monitorExam,
  pageExams,
  publishExam,
} from '@/api/exam'
import { listClasses } from '@/api/system'

/**
 * 四期回填：外壳换成 AppPage + DataTable，error 态可重试；
 * 批量删除受后端契约限制（无批删接口），逐条删除并汇总结果，
 * 进行中的考试通过 selectableFn 禁止勾选，避免误删正在作答的场次。
 */
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const err = ref('')
const rows = ref([])
const total = ref(0)
const classes = ref([])
const papers = ref([])
const selection = ref([])
const query = ref({ pageNum: 1, pageSize: 10, classId: null, status: null, keyword: '' })

const formVisible = ref(false)
const formRef = ref()
const form = ref(emptyForm())

const monitorVisible = ref(false)
const monitor = ref({})
const monitorExamId = ref(null)

// 只有已发布的试卷才能用于创建考试
const publishedPapers = computed(() => papers.value.filter((item) => item.status === 1))

function emptyForm() {
  const now = new Date()
  const start = new Date(now.getTime() + 5 * 60 * 1000)
  const end = new Date(now.getTime() + 120 * 60 * 1000)
  return {
    examName: '',
    paperId: null,
    classId: null,
    startTime: toText(start),
    endTime: toText(end),
    duration: 90,
    autoMark: 1,
    cheatCheck: 0,
  }
}

function toText(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

const rules = {
  examName: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  classId: [{ required: true, message: '请选择参考班级', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try {
    const res = await pageExams(query.value)
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
  query.value = { pageNum: 1, pageSize: 10, classId: null, status: null, keyword: '' }
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

function openForm() {
  if (!publishedPapers.value.length) {
    ElMessage.warning('还没有已发布的试卷，请先在「试卷组卷」中组卷并发布')
    return
  }
  form.value = emptyForm()
  formRef.value?.clearValidate()
  formVisible.value = true
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  if (
    new Date(form.value.endTime.replace(' ', 'T')) <=
    new Date(form.value.startTime.replace(' ', 'T'))
  ) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  saving.value = true
  try {
    await createExam(form.value)
    ElMessage.success('创建成功，请在列表中点击「发布」后学生才可见')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确定发布「${row.examName}」吗？发布后该班学生即可参加。`, '提示', {
    type: 'warning',
  })
  await publishExam(row.id)
  ElMessage.success('已发布')
  load()
}

async function handleFinish(row) {
  await ElMessageBox.confirm('确定提前结束该考试吗？结束后学生将无法继续作答。', '危险操作', {
    type: 'warning',
  })
  await finishExam(row.id)
  ElMessage.success('已结束')
  load()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除考试「${row.examName}」吗？`, '危险操作', { type: 'warning' })
  await deleteExam(row.id)
  ElMessage.success('删除成功')
  load()
}

// 后端没有批删接口：逐条删除并汇总；进行中场次已在勾选层被禁掉
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 场考试吗？`, '危险操作', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  const results = await Promise.allSettled(selection.value.map((row) => deleteExam(row.id)))
  const failed = results.filter((item) => item.status === 'rejected').length
  if (failed) {
    ElMessage.warning(`删除完成：成功 ${results.length - failed} 条，失败 ${failed} 条`)
  } else {
    ElMessage.success(`已删除 ${results.length} 场考试`)
  }
  selection.value = []
  load()
}

async function openMonitor(row) {
  monitorExamId.value = row.id
  monitor.value = await monitorExam(row.id)
  monitorVisible.value = true
}

async function refreshMonitor() {
  if (monitorExamId.value) {
    monitor.value = await monitorExam(monitorExamId.value)
  }
}

// 成绩跳转到成绩统计页
function openGrade(row) {
  router.push({ path: '/teacher/grade', query: { examId: row.id } })
}

onMounted(async () => {
  try {
    const [classList, paperList] = await Promise.all([listClasses(), listPapers()])
    classes.value = classList
    papers.value = paperList
  } catch (e) {
    classes.value = []
    papers.value = []
  }
  load()
})

function examAction(c, row) {
  if (c === 'grade') openGrade(row)
  if (c === 'finish') handleFinish(row)
  if (c === 'delete') handleDelete(row)
}
</script>
