<template>
  <div v-loading="loading">
    <div class="workspace-page-title">
      <div>
        <h1>题目复核</h1>
        <p>对照原文检查题干、公式、选项和答案，确认后审核入库。</p>
      </div>
      <router-link to="/resources/imports">← 返回资料列表</router-link>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="review-filter">
      <el-select v-model="stage" placeholder="全部状态" clearable @change="fetchCandidates">
        <el-option label="待复核" value="TAGGED" />
        <el-option label="已入库" value="PROMOTED" /><el-option label="已驳回" value="REJECTED" />
      </el-select>
      <el-button @click="fetchCandidates">刷新候选题</el-button>
      <span class="muted">{{ total }} 道候选题</span>
    </div>
    <div class="curation-layout">
      <aside class="business-panel candidate-list">
        <h3>候选题目</h3>
        <button
          v-for="(c, i) in candidates"
          :key="c.id"
          :class="{ active: form.id === c.id }"
          @click="select(c.id)"
        >
          <span>
            {{ i + 1 }}
            <small>{{ c.promotedQuestionId ? '已入库' : c.stage === 'REJECTED' ? '已驳回' : '待复核' }}</small>
          </span>
          <span>{{ plain(c.content).slice(0, 64) }}</span>
        </button>
        <el-empty v-if="!candidates.length" description="暂无候选题" />
        <el-pagination
          size="small"
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="prev,next"
          @current-change="fetchCandidates"
        />
      </aside>
      <section class="business-panel original-document">
        <div class="section-heading">
          <h3>原文对照</h3>
          <el-button :disabled="!rawId" size="small" @click="download">下载原文</el-button>
        </div>
        <div class="document-page-controls">
          <el-button size="small" :disabled="sourcePage <= 1" @click="turnPage(-1)">
            上一页
          </el-button>
          <span>第 {{ sourcePage }} 页</span>
          <el-button size="small" :disabled="!pageUrl || (document?.pageCount && sourcePage >= document.pageCount)" @click="turnPage(1)">下一页</el-button>
        </div>
        <div v-loading="previewing" class="source-paper">
          <div v-if="pageUrl" class="source-page-frame"><img :src="pageUrl" alt="原始 PDF 页面" /><span v-if="highlightStyle" class="source-anchor-highlight" :style="highlightStyle" aria-label="题干起始区域" /></div>
          <template v-else>
            <p class="muted">{{ previewNote || '选择候选题后显示来源资料' }}</p>
            <pre>{{ document?.contentPreview || '' }}</pre>
          </template>
        </div>
        <p class="study-help">{{ form.sourcePage ? `已定位至第 ${form.sourcePage} 页，标记区域为题干起始位置。` : '当前题目未获得可靠页码，请对照文本锚点或下载原文核对。' }} Word 资料通过文本锚点核对。</p>
        <div v-if="form.sourceAnchor" class="source-anchor-note"><strong>原文文本锚点</strong><p>{{ form.sourceAnchor }}</p></div>
        <p v-if="document?.contentHash" class="study-help">资料版本 {{ document.contentHash.slice(0,12) }} · {{ document.parserVersion }}</p>
      </section>
      <section class="business-panel candidate-editor">
        <template v-if="form.id">
          <div class="section-heading">
            <h3>复核与修订</h3>
            <span class="tag">版本 {{ form.revision || 1 }}</span>
          </div>
          <el-form label-position="top" :disabled="!!form.promotedQuestionId || form.stage === 'REJECTED' || saving">
            <div class="form-two-columns">
              <el-form-item label="科目">
                <el-select v-model="form.subjectId" placeholder="选择科目">
                  <el-option
                    v-for="s in subjects"
                    :key="s.id"
                    :label="s.subjectName"
                    :value="s.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="题型">
                <el-select v-model="form.questionType">
                  <el-option v-for="(n, k) in names" :key="k" :label="n" :value="Number(k)" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="题干（支持 LaTeX 公式）">
              <el-input v-model="form.content" type="textarea" :rows="5" />
            </el-form-item>
            <details class="stem-preview">
              <summary>预览题干与公式</summary>
              <RichContent :html="form.content" />
            </details>
            <template v-if="[1, 2].includes(form.questionType)">
              <el-form-item v-for="key in ['A', 'B', 'C', 'D']" :key="key" :label="'选项 ' + key">
                <el-input v-model="form.options[key]" />
              </el-form-item>
            </template>
            <el-form-item label="参考答案">
              <el-input v-model="form.answer" placeholder="多选用逗号分隔；多个填空用 ; 分隔" />
            </el-form-item>
            <el-form-item label="解析">
              <el-input v-model="form.analysis" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="知识点"><el-input v-model="form.knowledgeTags" /></el-form-item>
            <el-form-item v-if="document?.pageCount" label="原文定位页码"><el-input-number v-model="form.sourcePage" :min="1" :max="document.pageCount" /><small>自动定位有误时可人工更正</small></el-form-item>
            <el-form-item label="难度"><el-rate v-model="form.difficulty" /></el-form-item>
            <el-checkbox v-model="form.formulaConfirmed">
              已对照原文核对题目、答案及公式
            </el-checkbox>
          </el-form>
          <div class="review-actions">
            <el-button type="danger" plain :disabled="!!form.promotedQuestionId || form.stage === 'REJECTED'" :loading="saving" @click="reject">驳回</el-button>
            <el-button :disabled="!!form.promotedQuestionId || form.stage === 'REJECTED'" :loading="saving" @click="save">
              保存修订
            </el-button>
            <el-button
              type="primary"
              :disabled="!!form.promotedQuestionId || form.stage === 'REJECTED' || !form.formulaConfirmed"
              :loading="saving"
              @click="publish"
            >
              {{ form.promotedQuestionId ? '已审核入库' : '保存并审核入库' }}
            </el-button>
          </div>
          <details v-if="history.length" class="review-history"><summary>修订与审核记录（{{ history.length }}）</summary><ol><li v-for="(entry,i) in history" :key="i">版本 {{ entry.revision }} · {{ {EDIT:'保存修订',PUBLISH:'审核入库',REJECT:'驳回'}[entry.action] || entry.action }} · {{ entry.operator }} · {{ entry.created_at }}</li></ol></details>
        </template>
        <el-empty v-else description="请选择一道候选题" />
      </section>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { crawlerQuestionPage, crawlerDocumentDetail } from '@/api/crawler'
import {
  candidate,
  startCandidateReview, candidateEvents, rejectCandidate,
  saveCandidate,
  publishCandidate,
  documentPageImage,
  documentSource,
} from '@/api/workflow'
import { listSubjects } from '@/api/system'
import RichContent from '@/components/RichContent.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
const route = useRoute(),
  candidates = ref([]),
  form = ref({}),
  history = ref([]),
  subjects = ref([]),
  document = ref(null),
  page = ref(1),
  total = ref(0),
  stage = ref(''),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  sourcePage = ref(1),
  pageUrl = ref(''),
  previewing = ref(false),
  previewNote = ref(''),
  rawId = computed(() => form.value.rawId || route.query.document),
  names = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' }
let selection = 0,
  previewRequest = 0
const plain = (s) => String(s || '').replace(/<[^>]*>/g, '')
async function fetchCandidates() {
  loading.value = true
  try {
    const r = await crawlerQuestionPage({
      pageNum: page.value,
      rawId: route.query.document,
      stage: stage.value,
    })
    candidates.value = r.records
    total.value = Number(r.total)
    if (r.records.length && !r.records.some((c) => c.id === form.value.id))
      await select(r.records[0].id)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
async function select(id) {
  const seq = ++selection
  error.value = ''
  try {
    const r = await candidate(id)
    if (seq !== selection) return
    form.value = { ...r, options: r.options || {}, formulaConfirmed: r.formulaConfirmed === 1 }
    sourcePage.value = r.sourcePage || 1
    const doc = await crawlerDocumentDetail(r.rawId)
    if (seq !== selection) return
    document.value = doc
    const events = await candidateEvents(id)
    if (seq !== selection) return
    history.value = events
    if (!r.promotedQuestionId && r.stage !== 'REJECTED') await startCandidateReview(id)
    if (seq !== selection) return
    await preview()
  } catch (e) {
    error.value = e.message || '候选题加载失败'
  }
}
function turnPage(delta) {
  sourcePage.value += delta
  preview()
}
async function preview() {
  const seq = ++previewRequest
  previewing.value = true
  previewNote.value = ''
  if (pageUrl.value) URL.revokeObjectURL(pageUrl.value)
  pageUrl.value = ''
  try {
    const b = await documentPageImage(rawId.value, sourcePage.value)
    if (seq !== previewRequest) return
    if (!b.type.startsWith('image/'))
      throw new Error('无法预览该页。Word 请下载原文核对，PDF 请检查页码。')
    pageUrl.value = URL.createObjectURL(b)
  } catch (e) {
    if (seq === previewRequest) previewNote.value = e.message || '暂时无法显示原文预览'
  } finally {
    if (seq === previewRequest) previewing.value = false
  }
}
watch(
  () => [form.value.id, form.value.revision, JSON.stringify([
    form.value.subjectId, form.value.questionType, form.value.content, form.value.options,
    form.value.answer, form.value.analysis, form.value.knowledgeTags,
    form.value.sourcePage, form.value.difficulty,
  ])],
  (next, previous) => {
    if (next[0] && next[0] === previous?.[0] && next[1] === previous?.[1] && next[2] !== previous?.[2]) {
      form.value.formulaConfirmed = false
    }
  },
  { flush: 'sync' },
)
async function persist() {
  if (!form.value.subjectId) throw new Error('请先选择科目')
  const r = await saveCandidate(form.value.id, {
    ...form.value,
    expectedVersion: form.value.revision || 1,
  })
  form.value = { ...r, options: r.options || {}, formulaConfirmed: r.formulaConfirmed === 1 }
}
async function save() {
  saving.value = true
  error.value = ''
  try {
    await persist()
    history.value = await candidateEvents(form.value.id)
    ElMessage.success('修订已保存')
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}
async function publish() {
  saving.value = true
  error.value = ''
  try {
    await persist()
    const id = await publishCandidate(form.value.id, form.value.revision)
    form.value.promotedQuestionId = id
    ElMessage.success('已审核并写入正式题库')
    await fetchCandidates()
  } catch (e) {
    error.value = e.message || '入库失败，请检查必填字段'
  } finally {
    saving.value = false
  }
}
const highlightStyle = computed(() => {
  if (!form.value.sourceBbox || sourcePage.value !== form.value.sourcePage) return null
  const box = form.value.sourceBbox.split(',').map(Number)
  if (box.length !== 4 || box.some(n => !Number.isFinite(n))) return null
  return {left:`${box[0]*100}%`, top:`${box[1]*100}%`, width:`${box[2]*100}%`, height:`${box[3]*100}%`}
})
async function reject() {
  try {
    const { value } = await ElMessageBox.prompt('填写驳回原因，便于回看处理结果。', '驳回候选题', {inputValidator:v=>!!v?.trim() || '请填写原因'})
    saving.value=true
    await rejectCandidate(form.value.id, form.value.revision, value)
    ElMessage.success('已驳回');await select(form.value.id);await fetchCandidates()
  } catch(e) {if(e!=='cancel' && e!=='close')error.value=e.message || '驳回失败'} finally {saving.value=false}
}
async function download() {
  try {
    const b = await documentSource(rawId.value)
    const u = URL.createObjectURL(b),
      a = window.document.createElement('a')
    a.href = u
    a.download = document.value?.docName || '原始资料'
    a.click()
    setTimeout(() => URL.revokeObjectURL(u), 1000)
  } catch (e) {
    error.value = e.message || '下载失败'
  }
}
onMounted(async () => {
  try {
    subjects.value = await listSubjects()
    await fetchCandidates()
  } catch (e) {
    error.value = e.message || '加载失败'
  }
})
onUnmounted(() => {
  previewRequest++
  if (pageUrl.value) URL.revokeObjectURL(pageUrl.value)
})
</script>

<style scoped>
.candidate-list>button>span:last-child{min-width:0;overflow-wrap:anywhere}.curation-layout>*{min-width:0}
@media(max-width:900px){.curation-layout{grid-template-columns:minmax(0,1fr)}.candidate-list{grid-row:auto;max-height:240px}.candidate-editor{grid-column:auto}.original-document{position:static}.candidate-list>button>span:first-child{flex:0 0 66px}.source-paper{max-height:520px}}
.source-page-frame{position:relative;line-height:0}.source-page-frame img{display:block;width:100%}.source-anchor-highlight{position:absolute;background:rgba(255,193,7,.22);border:2px solid #d99a08;pointer-events:none;box-sizing:border-box}.source-anchor-note{padding:14px;background:#f8fafc;border-left:3px solid #d8e1ed;font-size:13px;line-height:1.7;margin-top:12px}.source-anchor-note p{margin:6px 0 0}.review-history{margin-top:24px;color:#64748b;font-size:12px;line-height:1.8}
</style>
