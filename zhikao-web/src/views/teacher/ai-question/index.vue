<template>
  <AppPage title="智能出题" description="设定出题要求，逐题复核后导入题库。">
    <template #actions>
      <el-button :disabled="!result" @click="showRaw = true">返回原文</el-button>
    </template>
    <el-alert
      v-if="!aiReady"
      title="出题服务暂不可用，请检查模型配置或稍后重试。"
      type="warning"
      :closable="false"
    />
    <div class="ai-workspace">
      <section class="console-panel">
        <header class="console-panel-head">出题要求</header>
        <el-form
          label-position="top"
          class="console-panel-body"
          :disabled="generating || importing"
        >
          <el-form-item label="科目" required>
            <el-select v-model="form.subjectId" filterable>
              <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点">
            <el-input v-model="form.knowledgePoint" placeholder="如：函数与导数" />
          </el-form-item>
          <el-form-item label="题型">
            <el-select v-model="form.type">
              <el-option v-for="t in 5" :key="t" :value="t" :label="typeLabel(t)" />
            </el-select>
          </el-form-item>
          <div class="console-form-grid">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty">
                <el-option v-for="d in 5" :key="d" :value="d" :label="'难度 ' + d" />
              </el-select>
            </el-form-item>
            <el-form-item label="数量">
              <el-input-number v-model="form.count" :min="1" :max="10" />
            </el-form-item>
          </div>
          <el-form-item label="每题分值">
            <el-input-number v-model="form.score" :min="0.5" :step="0.5" />
          </el-form-item>
          <el-button type="primary" :disabled="!aiReady" :loading="generating" @click="generate">
            生成题目
          </el-button>
          <p class="console-form-hint">生成内容需要教师复核，尤其是公式、答案与知识点归属。</p>
        </el-form>
      </section>
      <section class="console-panel">
        <header class="console-panel-head">
          <h3>
            待复核结果
            <span class="console-cell-note">{{ drafts.length }} 题</span>
          </h3>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="!selectedRows.length || generating"
            @click="confirmImport"
          >
            确认导入 {{ selectedRows.length || '' }}
          </el-button>
        </header>
        <div v-if="drafts.length" class="console-toolbar">
          <el-checkbox v-model="allChecked" :indeterminate="indeterminate" @change="toggleAll">
            全选可用题目
          </el-checkbox>
          <span class="console-cell-note">
            已选 {{ selectedRows.length }} / {{ validCount }} 道 · 请逐题复核
          </span>
        </div>
        <el-alert v-if="parseNote" :title="parseNote" type="info" :closable="false" />
        <SkeletonBlock v-if="generating" :rows="8" />
        <EmptyState
          v-else-if="!drafts.length"
          title="开始准备你的下一道题"
          description="选择科目与出题要求，生成后在这里复核。"
        />
        <article v-for="(q, i) in drafts" :key="q.rowKey" class="console-question-block">
          <div class="console-summary-row">
            <div>
              <el-checkbox
                :model-value="selectedRows.includes(q)"
                :disabled="!q.valid || importing"
                @change="(checked) => selectDraft(q, checked)"
              >
                第 {{ i + 1 }} 题
              </el-checkbox>
              <el-tag size="small" type="info">{{ typeLabel(q.type) }}</el-tag>
              <el-tag size="small" :type="q.valid ? 'warning' : 'danger'">
                {{ q.valid ? '待人工复核' : '内容不完整' }}
              </el-tag>
            </div>
            <el-button text type="primary" :disabled="importing" @click="editDraft(q)">
              编辑
            </el-button>
          </div>
          <RichContent :html="q.content" />
          <div class="console-option-grid">
            <div v-for="o in optionEntries(q)" :key="o.label">
              {{ o.label }}.
              <RichContent :html="o.text" inline />
            </div>
          </div>
          <div class="console-answer-box">
            <h4>参考答案 · {{ q.score }} 分</h4>
            <RichContent :html="q.answer" />
            <RichContent :html="q.analysis" />
          </div>
          <p v-if="q.similar?.length" class="console-form-hint">
            发现 {{ q.similar.length }} 道相似题，请确认是否重复。
          </p>
          <p v-if="!q.valid" class="console-form-hint">
            {{ q.validationMessage || q.warnings?.join('；') || '请编辑并检查题干、选项和答案。' }}
          </p>
        </article>
      </section>
    </div>
    <ZkDrawer v-model="showRaw" title="模型返回原文">
      <el-button @click="copy">复制原文</el-button>
      <div class="console-panel-body" v-html="rendered" />
    </ZkDrawer>
    <ZkDrawer
      v-model="editVisible"
      title="修订题目"
      show-footer
      confirm-text="保存修订"
      @confirm="saveDraft"
    >
      <el-form v-if="editing" label-position="top">
        <el-form-item label="题干" required>
          <el-input v-model="editing.content" type="textarea" :rows="5" />
        </el-form-item>
        <template v-if="[1, 2].includes(editing.type)">
          <el-form-item v-for="key in ['A', 'B', 'C', 'D']" :key="key" :label="'选项 ' + key">
            <el-input v-model="editing.options[key]" />
          </el-form-item>
        </template>
        <el-form-item label="参考答案" required>
          <el-input v-model="editing.answer" type="textarea" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="editing.analysis" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="知识点"><el-input v-model="editing.knowledgePoint" /></el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="editing.score" :min="0.5" :step="0.5" />
        </el-form-item>
      </el-form>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DocumentCopy, MagicStick, Promotion, Refresh, Upload } from '@element-plus/icons-vue'
import RichContent from '@/components/RichContent.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import AppPage from '@/components/AppPage.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { listSubjects } from '@/api/system'
import { findSimilarQuestions, saveQuestionsBatch } from '@/api/exam'
import { aiGenQuestion, aiStatus } from '@/api/ai'
import { useDict } from '@/composables/useDict'
import { renderMarkdown } from '@/utils/markdown'

/**
 * 智能出题页。
 *
 * <p><b>三期改动：从「生成一段文本」升级为「生成可勾选入库的草稿」。</b>
 * 一期只把模型输出渲染成 Markdown，教师必须复制→切页面→逐字段粘贴，
 * 演示时这一步非常尴尬。现在后端把模型 JSON 解析成结构化草稿并发回，
 * 这里渲染成带复选框的表格，勾完点「确认导入题库」即可。
 *
 * <p><b>但「AI 不写库」这条边界没变。</b>草稿先回前端、由人勾选确认，
 * 走的还是与手工新建完全相同的 {@code POST /question/batch} 接口
 * （后端逐条复用单题新建的校验）。提升的是输入效率，不是决策权。
 *
 * <p>「检查」列是刻意加的：AI 生成的题目常见问题是「单选题只给了 2 个选项」
 * 「答案不在选项里」，这些必须在勾选前就看到，而不是入库后才报错。
 */
const router = useRouter()
const { dict } = useDict('question_type')

const subjects = ref([])
const aiReady = ref(false)
const generating = ref(false)
const importing = ref(false)
const result = ref(null)
const drafts = ref([])
const parseNote = ref('')
const showRaw = ref(false)
const tableRef = ref(null)
const selectedRows = ref([])
const allChecked = ref(false)

const form = reactive({
  subjectId: null,
  // 科目名不发请求，只用于给模型提供语境与界面提示；
  // 真正的归属用 subjectId 表达，避免同名科目选错
  subjectName: '',
  knowledgePoint: '',
  type: 1,
  difficulty: 3,
  count: 3,
  score: 5,
})

const rendered = computed(() => renderMarkdown(result.value?.content || ''))
const validCount = computed(() => drafts.value.filter((item) => item.valid).length)
const indeterminate = computed(
  () => selectedRows.value.length > 0 && selectedRows.value.length < validCount.value,
)

/** 只有通过校验的草稿才允许勾选，从源头上避免把残缺题目放进题库 */
function rowSelectable(row) {
  return row.valid
}

function optionEntries(row) {
  const options = row.options || {}
  return Object.keys(options)
    .sort()
    .map((label) => ({ label, text: options[label] }))
}

function typeLabel(type) {
  const hit = (dict.value.question_type || []).find((item) => Number(item.value) === Number(type))
  return hit?.label || '未知'
}

function onSelectionChange(rows) {
  selectedRows.value = rows
  allChecked.value = validCount.value > 0 && rows.length === validCount.value
}

function toggleAll(checked) {
  onSelectionChange(checked ? drafts.value.filter((q) => q.valid) : [])
}

async function generate() {
  const subject = subjects.value.find((item) => item.id === form.subjectId)
  if (!subject) {
    ElMessage.warning('请先选择科目')
    return
  }
  // 名称由选中的科目推出，避免用户改了 id 之后名称还停在旧值
  form.subjectName = subject.subjectName
  generating.value = true
  result.value = null
  drafts.value = []
  selectedRows.value = []
  allChecked.value = false
  try {
    const data = await aiGenQuestion({ ...form })
    result.value = data
    drafts.value = (data.drafts || []).map((row, index) => ({ ...row, rowKey: index }))
    parseNote.value = data.parseNote || ''
    showRaw.value = drafts.value.length === 0
    await nextTick()
    // 只勾选通过校验的题目：默认「全选可用项」比默认全不选更省事，
    // 而不可入库的行根本没有复选框可选，不会误选
    onSelectionChange(drafts.value.filter((q) => q.valid))
    if (drafts.value.length) {
      ElMessage.success(`生成完成，解析出 ${drafts.value.length} 道题目`)
      checkDuplicates()
    } else {
      ElMessage.warning('模型返回的内容不是标准 JSON，已按原文展示')
    }
  } finally {
    generating.value = false
  }
}

/**
 * 逐题查重（生成后自动跑，不阻塞主流程的结果展示）。
 *
 * 串行而不是并发：一次最多 10 道题，串行带来的延迟可以忽略，
 * 换成并发反而会一次性打出 10 个查重请求，在题库大时把数据库顶一下。
 */
async function checkDuplicates() {
  for (const row of drafts.value) {
    if (!row.valid || !row.content) {
      continue
    }
    try {
      row.similar = await findSimilarQuestions({
        subjectId: row.subjectId || form.subjectId,
        type: row.type,
        content: row.content,
      })
    } catch (e) {
      row.similar = []
    }
  }
}

/** 导入选中的草稿：走通用批量写入接口，后端逐条复用单题新建的校验 */
async function importSelected() {
  if (!selectedRows.value.length || importing.value) return
  importing.value = true
  const successful = new Set(),
    errors = []
  try {
    for (const row of [...selectedRows.value]) {
      try {
        const summary = await saveQuestionsBatch([
          {
            subjectId: row.subjectId || form.subjectId,
            type: row.type,
            content: row.content,
            options: row.options || {},
            answer: row.answer,
            analysis: row.analysis,
            difficulty: row.difficulty,
            knowledgePoint: row.knowledgePoint || form.knowledgePoint,
            score: row.score,
          },
        ])
        if (summary.successCount === 1 && !summary.failCount) successful.add(row.rowKey)
        else errors.push(...(summary.errors || ['题目未导入']))
      } catch (e) {
        errors.push(e.msg || e.message || '题目导入失败')
      }
    }
    drafts.value = drafts.value.filter((q) => !successful.has(q.rowKey))
    onSelectionChange(selectedRows.value.filter((q) => !successful.has(q.rowKey)))
    if (errors.length)
      await ElMessageBox.alert(
        '成功 ' + successful.size + ' 道，失败 ' + errors.length + ' 道。\n' + errors.join('\n'),
        '导入结果',
        { type: 'warning' },
      )
    else ElMessage.success('已导入 ' + successful.size + ' 道题目')
  } finally {
    importing.value = false
  }
}

async function copy() {
  try {
    await navigator.clipboard.writeText(result.value.content || '')
    ElMessage.success('已复制模型原文')
  } catch (e) {
    ElMessage.warning('浏览器不支持自动复制，请手动选择文本')
  }
}

function toggleRaw() {
  showRaw.value = !showRaw.value
}

function goChat() {
  router.push('/ai/chat')
}

onMounted(async () => {
  try {
    subjects.value = await listSubjects()
  } catch (e) {
    subjects.value = []
  }
  try {
    const status = await aiStatus()
    aiReady.value = Boolean(status?.enabled)
  } catch (e) {
    aiReady.value = false
  }
})

const editVisible = ref(false),
  editing = ref(null)
function selectDraft(q, checked) {
  onSelectionChange(
    checked ? [...selectedRows.value, q] : selectedRows.value.filter((x) => x !== q),
  )
}
function editDraft(q) {
  editing.value = JSON.parse(JSON.stringify(q))
  editing.value.options ||= {}
  editVisible.value = true
}
function saveDraft() {
  const q = editing.value
  if (!q.content?.trim() || !q.answer?.trim()) {
    ElMessage.warning('请填写题干和参考答案')
    return
  }
  const keys = Object.keys(q.options || {}).filter((k) => q.options[k]?.trim())
  if (
    [1, 2].includes(q.type) &&
    (keys.length < 2 ||
      !q.answer.split('').every((k) => keys.includes(k)) ||
      (q.type === 1 && q.answer.length !== 1))
  ) {
    ElMessage.warning('请检查选项与答案是否对应')
    return
  }
  q.valid = true
  const i = drafts.value.findIndex((x) => x.rowKey === q.rowKey)
  const wasSelected = selectedRows.value.some((x) => x.rowKey === q.rowKey)
  selectedRows.value = selectedRows.value.filter((x) => x.rowKey !== q.rowKey)
  drafts.value[i] = q
  if (wasSelected) selectedRows.value.push(q)
  onSelectionChange(selectedRows.value)
  editVisible.value = false
}
async function confirmImport() {
  try {
    await ElMessageBox.confirm('已核对所选题目的题干、公式与答案？确认后将写入题库。', '确认入库', {
      confirmButtonText: '确认导入',
      type: 'warning',
    })
  } catch {
    return
  }
  await importSelected()
}
</script>
<style scoped>
.ai-workspace {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}
@media (max-width: 1000px) {
  .ai-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
