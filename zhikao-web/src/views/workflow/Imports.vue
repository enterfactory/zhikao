<template>
  <div v-loading="loading">
    <div class="workspace-page-title">
      <div>
        <h1>资料导入</h1>
        <p>上传教学资料，识别题目后进入人工复核。</p>
      </div>
      <el-button @click="load">刷新列表</el-button>
    </div>
    <div class="workflow-steps">
      <span class="current">01 上传资料</span>
      <b>→</b>
      <span>02 识别与提取</span>
      <b>→</b>
      <span>03 对照原文复核</span>
      <b>→</b>
      <span>04 审核入库</span>
    </div>
    <section class="business-panel upload-panel">
      <div class="upload-symbol">↥</div>
      <h2>添加你的教学资料</h2>
      <p>支持 PDF、Word 文件。文字、公式识别后仍需人工复核。</p>
      <label class="upload-file-button">
        <input
          type="file"
          accept=".pdf,.doc,.docx"
          multiple
          :disabled="uploading"
          @change="upload"
        />
        {{ uploading ? '正在上传…' : '选择文件上传' }}
      </label>
      <p class="muted">每次最多 20 份 · 单文件不超过 50 MB</p>
      <el-alert
        v-if="uploadNote"
        :title="uploadNote"
        :type="uploadError ? 'error' : 'success'"
        :closable="false"
      />
    </section>
    <section class="business-panel">
      <div class="section-heading">
        <h2>
          已导入资料
          <span class="muted">{{ total }}</span>
        </h2>
        <el-input
          v-model="keyword"
          placeholder="搜索文件名称"
          clearable
          style="width: 240px"
          @change="search"
        />
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-table :data="docs">
        <el-table-column prop="docName" label="资料名称" min-width="260" />
        <el-table-column label="识别方式" width="120">
          <template #default="{ row }">
            <span class="tag">{{ row.viaOcr ? 'OCR 识别' : '文本提取' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="parsedCount" label="候选题" width="100" />
        <el-table-column prop="parseNote" label="处理结果" min-width="220" show-overflow-tooltip />
        <el-table-column label="导入时间" width="165">
          <template #default="{ row }">{{ fmt(row.fetchTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
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
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </section>
    <section class="business-panel task-panel">
      <div class="section-heading">
        <h2>处理任务</h2>
        <span class="muted">进行中的任务每 10 秒更新</span>
      </div>
      <el-table :data="tasks">
        <el-table-column prop="id" label="任务编号" min-width="170" />
        <el-table-column prop="sourceName" label="资料来源" min-width="180" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="fetched" label="已提取文档" />
        <el-table-column prop="staged" label="待复核题" />
        <el-table-column prop="error" label="异常信息" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              link
              type="primary"
              :loading="retrying === row.id"
              @click="retry(row)"
            >
              重新处理
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>
<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { crawlerUpload, crawlerDocumentPage, crawlerTaskPage } from '@/api/crawler'
import { retryImport } from '@/api/workflow'
const router = useRouter(),
  docs = ref([]),
  tasks = ref([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(''),
  loading = ref(false),
  uploading = ref(false),
  error = ref(''),
  uploadNote = ref(''),
  uploadError = ref(false),
  retrying = ref(null)
let timer
const fmt = (v) =>
  String(v || '')
    .replace('T', ' ')
    .slice(0, 16)
async function load(quiet = false) {
  if (!quiet) loading.value = true
  error.value = ''
  try {
    const [d, t] = await Promise.all([
      crawlerDocumentPage({ pageNum: page.value, keyword: keyword.value, silent: quiet }),
      crawlerTaskPage({ silent: quiet }),
    ])
    docs.value = d.records
    total.value = Number(d.total)
    tasks.value = t.records
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
async function retry(task) {
  retrying.value = task.id
  try {
    await retryImport(task.id)
    await load()
  } catch (e) {
    error.value = e.message || '重试失败'
  } finally {
    retrying.value = null
  }
}
function search() {
  page.value = 1
  load()
}
async function upload(e) {
  const files = [...e.target.files]
  if (!files.length) return
  if (files.length > 20 || files.some((f) => f.size > 50 * 1024 * 1024)) {
    uploadError.value = true
    uploadNote.value = '请选择最多 20 份、每份不超过 50 MB 的资料'
    e.target.value = ''
    return
  }
  uploading.value = true
  uploadNote.value = ''
  try {
    const r = await crawlerUpload(files)
    uploadError.value = !!r.rejected?.length
    uploadNote.value = `已接收 ${r.stored} 份资料，进入后台识别队列。${(r.rejected || []).join('；')}`
    await load()
  } catch (e) {
    uploadError.value = true
    uploadNote.value = e.message || '上传失败，请重试'
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}
onMounted(() => {
  load()
  timer = setInterval(() => {
    if (tasks.value.some((t) => ['PENDING', 'RUNNING'].includes(t.status))) load(true)
  }, 10000)
})
onUnmounted(() => clearInterval(timer))
</script>
