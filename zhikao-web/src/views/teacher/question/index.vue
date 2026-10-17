<template>
  <AppPage title="题库管理" description="查找、维护与复用题目，开始下一次教学。">
    <template #actions>
      <el-button @click="importVisible = true">批量导入</el-button>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增题目</el-button>
    </template>
    <div class="console-split">
      <aside class="console-panel console-directory">
        <div class="console-panel-head">科目目录</div>
        <button
          :class="{ active: !query.subjectId }"
          @click="
            () => {
              query.subjectId = null
              search()
            }
          "
        >
          全部科目
        </button>
        <button
          v-for="s in subjects"
          :key="s.id"
          :class="{ active: query.subjectId === s.id }"
          @click="
            () => {
              query.subjectId = s.id
              search()
            }
          "
        >
          {{ s.subjectName }}
        </button>
        <p class="directory-note">知识点可通过右侧关键字查找。</p>
      </aside>
      <section class="console-panel">
        <div class="console-workspace-tabs">
          <button
            :class="{ active: !query.type }"
            @click="
              () => {
                query.type = null
                search()
              }
            "
          >
            全部题目
          </button>
          <button
            v-for="t in TYPES"
            :key="t.value"
            :class="{ active: query.type === t.value }"
            @click="
              () => {
                query.type = t.value
                search()
              }
            "
          >
            {{ t.label }}
          </button>
        </div>
        <div class="console-toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索题干或知识点"
            clearable
            :prefix-icon="Search"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.difficulty" placeholder="全部难度" clearable @change="search">
            <el-option v-for="d in 5" :key="d" :label="'难度 ' + d" :value="d" />
          </el-select>
          <el-button @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
        <div v-if="selection.length" class="console-bulk-bar">
          已选 {{ selection.length }} 道题
          <el-button text type="primary" @click="composeSelection">加入新试卷</el-button>
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
          @selection-change="selection = $event"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @retry="load"
        >
          <el-table-column label="题目" min-width="300">
            <template #default="{ row }">
              <button
                class="console-cell-title console-link"
                @click="
                  () => {
                    previewRow = row
                    previewVisible = true
                  }
                "
              >
                {{ plainText(row.content) }}
              </button>
              <div class="console-cell-note">
                {{ row.knowledgePoint || '未设置知识点' }} · {{ row.subjectName }} · #{{ row.id }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="题型 / 难度" width="130">
            <template #default="{ row }">
              {{ row.typeName }}
              <div class="console-cell-note">难度 {{ row.difficulty }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="分值" width="80" />
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button
                text
                type="primary"
                @click="
                  () => {
                    previewRow = row
                    previewVisible = true
                  }
                "
              >
                预览
              </el-button>
              <el-button text type="primary" @click="openForm(row)">编辑</el-button>
              <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </DataTable>
      </section>
    </div>
    <ZkDrawer
      v-model="dialogVisible"
      :title="form.id ? '编辑题目' : '新增题目'"
      show-footer
      :loading="saving"
      confirm-text="保存题目"
      cancel-text="取消"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="科目" prop="subjectId">
          <el-select
            v-model="form.subjectId"
            class="control-full"
            placeholder="请选择科目"
            filterable
          >
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型" prop="type">
          <el-radio-group v-model="form.type" :disabled="!!form.id" @change="onTypeChange">
            <el-radio-button v-for="t in TYPES" :key="t.value" :value="t.value">
              {{ t.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题干" />
        </el-form-item>

        <template v-if="isChoice">
          <el-form-item v-for="key in optionKeys" :key="key" :label="'选项 ' + key">
            <el-input v-model="form.options[key]" :placeholder="`选项 ${key} 内容`" />
          </el-form-item>
        </template>

        <el-form-item label="正确答案" prop="answer">
          <el-select
            v-if="form.type === 1"
            v-model="form.answer"
            class="control-full"
            placeholder="请选择答案"
          >
            <el-option v-for="key in filledOptionKeys" :key="key" :label="key" :value="key" />
          </el-select>
          <el-checkbox-group v-else-if="form.type === 2" v-model="multiAnswer">
            <el-checkbox v-for="key in filledOptionKeys" :key="key" :value="key">
              {{ key }}
            </el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else-if="form.type === 3" v-model="form.answer">
            <el-radio value="对">对</el-radio>
            <el-radio value="错">错</el-radio>
          </el-radio-group>
          <el-input
            v-else
            v-model="form.answer"
            :placeholder="
              form.type === 4
                ? '填空：多个可接受答案用 | 分隔，多个空用 ; 分隔'
                : '简答：填写参考答案，留待教师批改'
            "
          />
        </el-form-item>

        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" />
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="form.knowledgePoint" placeholder="如 Java 基础" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="form.score" :min="0.5" :step="0.5" :precision="2" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <div class="console-section-label">题干预览</div>
      <RichContent :html="form.content" />
    </ZkDrawer>
    <ZkDrawer v-model="previewVisible" title="题目预览">
      <template v-if="previewRow">
        <div class="console-question-meta">
          {{ previewRow.subjectName }} · {{ previewRow.typeName }} · {{ previewRow.score }} 分
        </div>
        <RichContent :html="previewRow.content" />
        <div class="console-option-grid">
          <div v-for="[key, text] in optionEntries(previewRow.options)" :key="key">
            {{ key }}.
            <RichContent :html="text" inline />
          </div>
        </div>
        <div class="console-answer-box">
          <h4>参考答案</h4>
          <RichContent :html="previewRow.answer" />
          <h4>解析</h4>
          <RichContent :html="previewRow.analysis" empty-text="暂无解析" />
        </div>
      </template>
    </ZkDrawer>
    <ImportWizard
      v-model="importVisible"
      kind="questions"
      :subject-id="query.subjectId"
      :subjects="subjects"
      @done="load"
    />
  </AppPage>
</template>
<script setup>
import { useRouter } from 'vue-router'
import { computed, onMounted, ref, watch } from 'vue'
import {
  Delete,
  Download,
  Edit,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  Upload,
} from '@element-plus/icons-vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import ImportWizard from '@/components/ImportWizard.vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import RichContent from '@/components/RichContent.vue'
import {
  deleteQuestion,
  downloadQuestionTemplate,
  importQuestions,
  pageQuestions,
  saveQuestion,
} from '@/api/exam'
import { listSubjects } from '@/api/system'
import { saveBlob } from '@/utils/download'

const router = useRouter()
function composeSelection() {
  const ids = [...new Set(selection.value.map((q) => q.subjectId))]
  if (ids.length !== 1) {
    ElMessage.warning('请先选择同一科目的题目')
    return
  }
  router.push({
    path: '/teacher/paper',
    query: { questions: selection.value.map((q) => q.id).join(',') },
  })
}
const TYPES = [
  { value: 1, label: '单选' },
  { value: 2, label: '多选' },
  { value: 3, label: '判断' },
  { value: 4, label: '填空' },
  { value: 5, label: '简答' },
]
const OPTION_KEYS = ['A', 'B', 'C', 'D']

const loading = ref(false)
const saving = ref(false)
const err = ref('')
const rows = ref([])
const total = ref(0)
const subjects = ref([])
const selection = ref([])
const importSubjectId = ref(null)
const dialogVisible = ref(false)
const formRef = ref()

const query = ref({
  pageNum: 1,
  pageSize: 10,
  subjectId: null,
  type: null,
  difficulty: null,
  keyword: '',
})

const emptyForm = () => ({
  id: null,
  subjectId: null,
  type: 1,
  content: '',
  options: { A: '', B: '', C: '', D: '' },
  answer: '',
  analysis: '',
  difficulty: 3,
  knowledgePoint: '',
  score: 5,
})
const form = ref(emptyForm())
const multiAnswer = ref([])

const rules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  answer: [{ required: true, message: '请填写正确答案', trigger: 'blur' }],
}

// 单选 / 多选需要维护选项
const isChoice = computed(() => form.value.type === 1 || form.value.type === 2)
const optionKeys = OPTION_KEYS
const filledOptionKeys = computed(() =>
  OPTION_KEYS.filter((key) => (form.value.options[key] || '').trim()),
)

// 多选题的答案用数组绑定，提交时拼成 ABD
watch(multiAnswer, (value) => {
  if (form.value.type === 2) {
    form.value.answer = [...value].sort().join('')
  }
})

async function load() {
  loading.value = true
  try {
    const res = await pageQuestions(query.value)
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

function reset() {
  query.value = {
    pageNum: 1,
    pageSize: 10,
    subjectId: null,
    type: null,
    difficulty: null,
    keyword: '',
  }
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

function onTypeChange() {
  form.value.answer = ''
  multiAnswer.value = []
}

// 列表单元格只做一行截断预览：把 HTML 标签摘掉当纯文本用，完整内容在 popover 里渲染
function plainText(val) {
  if (val === null || val === undefined) return ''
  return String(val)
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;|&#39;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
}

// 选项在 VO 里是 Map（A→文本），导入题也可能是 JSON 串或数组：统一成 [key, text] 列表
function optionEntries(raw) {
  if (raw === null || raw === undefined || raw === '') return []
  let val = raw
  if (typeof val === 'string') {
    const text = val.trim()
    if (/^[{[]/.test(text)) {
      try {
        val = JSON.parse(text)
      } catch (e) {
        val = null
      }
    }
  }
  if (val === null || val === undefined) return []
  if (Array.isArray(val)) {
    return val
      .map((item, i) => [
        String.fromCharCode(65 + i),
        item && typeof item === 'object' ? (item.content ?? item.text ?? '') : item,
      ])
      .filter(([, text]) => String(text ?? '').trim() !== '')
  }
  if (typeof val === 'object') {
    return Object.entries(val).filter(([, text]) => String(text ?? '').trim() !== '')
  }
  return []
}

function openForm(row) {
  if (row) {
    form.value = {
      id: row.id,
      subjectId: row.subjectId,
      type: row.type,
      content: row.content,
      options: { A: '', B: '', C: '', D: '', ...(row.options || {}) },
      answer: row.answer,
      analysis: row.analysis,
      difficulty: row.difficulty || 3,
      knowledgePoint: row.knowledgePoint,
      score: Number(row.score),
    }
    multiAnswer.value = row.type === 2 ? (row.answer || '').split('') : []
  } else {
    form.value = emptyForm()
    multiAnswer.value = []
  }
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  if (isChoice.value && filledOptionKeys.value.length < 2) {
    ElMessage.warning('选择题至少需要填写 2 个选项')
    return
  }
  saving.value = true
  try {
    // 只提交填了内容的选项
    const payload = { ...form.value, options: {} }
    if (isChoice.value) {
      filledOptionKeys.value.forEach((key) => {
        payload.options[key] = form.value.options[key]
      })
    }
    await saveQuestion(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    '确定删除该题目吗？已加入试卷的题目不受影响，但试卷预览将缺失本题。',
    '危险操作',
    {
      type: 'warning',
    },
  )
  await deleteQuestion(row.id)
  ElMessage.success('删除成功')
  load()
}

// 后端没有批删接口：逐条删除并汇总结果，失败的 id 保留给用户复查
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 道题目吗？`, '危险操作', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  const results = await Promise.allSettled(selection.value.map((row) => deleteQuestion(row.id)))
  const failed = results.filter((item) => item.status === 'rejected').length
  if (failed) {
    ElMessage.warning(`删除完成：成功 ${results.length - failed} 条，失败 ${failed} 条`)
  } else {
    ElMessage.success(`已删除 ${results.length} 道题目`)
  }
  selection.value = []
  load()
}

async function handleImport(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await importQuestions(importSubjectId.value, formData)
  if (res.failCount > 0) {
    ElMessageBox.alert(
      `成功 ${res.successCount} 条，失败 ${res.failCount} 条\n` + res.errors.join('\n'),
      '导入结果',
      { type: 'warning' },
    )
  } else {
    ElMessage.success(`成功导入 ${res.successCount} 条`)
  }
  load()
  return false // 阻止 el-upload 自动上传
}

async function downloadTemplate() {
  saveBlob(await downloadQuestionTemplate(), '题目导入模板.xlsx')
}

onMounted(async () => {
  try {
    subjects.value = await listSubjects()
  } catch (e) {
    subjects.value = []
  }
  load()
})

const importVisible = ref(false),
  previewVisible = ref(false),
  previewRow = ref(null)
</script>
