<template>
  <AppPage title="文件空间" description="集中查看上传附件与导出文件，文件归属与业务引用分别管理。">
    <template #actions>
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" @click="uploadVisible = true">上传文件</el-button>
    </template>
    <section class="console-panel">
      <div class="console-workspace-tabs">
        <button
          :class="{ active: !query.category }"
          @click="
            () => {
              query.category = null
              search()
            }
          "
        >
          全部文件
        </button>
        <button
          v-for="c in categories"
          :key="c.value"
          :class="{ active: query.category === c.value }"
          @click="
            () => {
              query.category = c.value
              search()
            }
          "
        >
          {{ c.label }}
        </button>
      </div>
      <div class="console-toolbar">
        <el-input v-model="filename" placeholder="在本页查找文件名" clearable />
        <el-radio-group v-if="isAdmin" v-model="query.mine" @change="search">
          <el-radio-button :value="true">我的文件</el-radio-button>
          <el-radio-button :value="false">全站文件</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="viewMode">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="grid">网格</el-radio-button>
        </el-radio-group>
      </div>
      <DataTable
        v-if="viewMode === 'list'"
        :rows="visibleRows"
        :total="total"
        :loading="loading"
        :error="error"
        :page-num="pageNum"
        :page-size="pageSize"
        @page-change="onPageChange"
        @size-change="onSizeChange"
        @retry="load"
      >
        <el-table-column label="文件名称" min-width="260">
          <template #default="{ row }">
            <div class="console-member">
              <el-icon><Document /></el-icon>
              <div>
                <strong class="console-cell-title">{{ row.name }}</strong>
                <span class="console-cell-note">
                  {{ categoryLabel(row.category) }} · {{ row.sizeText }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="上传人" min-width="100" />
        <el-table-column prop="createTime" label="上传时间" min-width="170" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="isImage(row)" text type="primary" @click="preview(row)">
              预览
            </el-button>
            <el-button text type="primary" @click="download(row)">下载</el-button>
            <el-button text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </DataTable>
      <div v-else v-loading="loading">
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-empty v-else-if="!visibleRows.length" description="暂无匹配文件" />
        <div v-else class="file-grid">
          <article v-for="f in visibleRows" :key="f.id" class="file-card">
            <div class="file-art">
              <img v-if="isImage(f)" :src="f.url" alt="文件缩略图" />
              <el-icon v-else><Document /></el-icon>
            </div>
            <strong>{{ f.name }}</strong>
            <p class="console-cell-note">{{ f.sizeText }} · {{ categoryLabel(f.category) }}</p>
            <p class="console-cell-note">{{ f.createTime }}</p>
            <div>
              <el-button v-if="isImage(f)" text type="primary" @click="preview(f)">预览</el-button>
              <el-button text type="primary" @click="download(f)">下载</el-button>
              <el-button text type="danger" @click="remove(f)">删除</el-button>
            </div>
          </article>
        </div>
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </section>
    <ZkDrawer v-model="uploadVisible" title="上传文件" :loading="uploading">
      <el-form label-position="top">
        <el-form-item label="文件分类">
          <el-select v-model="uploadCategory" :disabled="uploading">
            <el-option
              v-for="c in uploadableCategories"
              :key="c.value"
              :value="c.value"
              :label="c.label"
            />
          </el-select>
        </el-form-item>
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          :accept="acceptText"
          :disabled="uploading"
          :on-change="queueFile"
        >
          <el-icon :size="28"><UploadFilled /></el-icon>
          <p>点击选择或拖入文件</p>
          <small>{{ uploadHint }}</small>
        </el-upload>
        <div v-for="f in uploadQueue" :key="f.id" class="console-summary-row">
          <span>{{ f.name }}</span>
          <span>{{ f.status }}</span>
        </div>
      </el-form>
    </ZkDrawer>
    <ZkDrawer v-model="previewVisible" :title="previewName">
      <img v-if="previewUrl" :src="previewUrl" class="file-preview" alt="文件预览" />
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Delete,
  Document,
  Download,
  Refresh,
  RefreshRight,
  Search,
  UploadFilled,
  View,
} from '@element-plus/icons-vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/store/user'
import { deleteFile, pageMyFiles, uploadFile } from '@/api/file'

/**
 * 我的文件页（三期新增，配合后端文件中心）。
 *
 * <p>为什么值得单独做一个页面：文件散落在各处时，用户无法回答
 * 「我上次导的那个模板还在吗」「这个头像占多大」这类问题；
 * 而演示时一屏列出「上传 → 分类 → 体积 → 来源」本身就是对文件模块最好的说明。
 *
 * <p>权限在界面上也如实体现：学生只看到头像一种分类，
 * 教师/管理员才有表格与试卷附件；「仅我的 / 全站」开关只对管理员出现。
 */
const userStore = useUserStore()

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const uploading = ref(false)
const uploadCategory = ref('excel')
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

const query = reactive({ category: null, mine: true })

const isAdmin = computed(() => userStore.role === 'admin')
const isStudent = computed(() => userStore.role === 'student')

const categories = [
  { value: 'avatar', label: '头像' },
  { value: 'excel', label: '表格数据' },
  { value: 'question', label: '题目文件' },
  { value: 'paper', label: '试卷附件' },
  { value: 'export', label: '导出结果' },
  { value: 'other', label: '其他' },
]

/** 学生只能上传头像——与后端 FileController 的判定保持一致 */
const uploadableCategories = computed(() =>
  isStudent.value ? categories.filter((item) => item.value === 'avatar') : categories,
)

const uploadRules = {
  avatar: { ext: ['png', 'jpg', 'jpeg', 'gif', 'webp'], mb: 2 },
  excel: { ext: ['xlsx', 'xls', 'csv'], mb: 10 },
  question: { ext: ['xlsx', 'xls', 'csv'], mb: 10 },
  paper: { ext: ['xlsx', 'xls', 'pdf', 'doc', 'docx', 'png', 'jpg', 'jpeg'], mb: 20 },
  export: { ext: ['xlsx', 'csv'], mb: 20 },
  other: { ext: ['xlsx', 'xls', 'pdf', 'docx', 'png', 'jpg', 'jpeg', 'txt'], mb: 10 },
}
const uploadRule = computed(() => uploadRules[uploadCategory.value] || uploadRules.other)
const acceptText = computed(() => uploadRule.value.ext.map((ext) => '.' + ext).join(','))
const uploadHint = computed(
  () => `支持 ${uploadRule.value.ext.join(' / ')}；单个文件不超过 ${uploadRule.value.mb} MB`,
)

function categoryLabel(value) {
  return categories.find((item) => item.value === value)?.label || '其他'
}

function categoryTagType(value) {
  return { avatar: 'success', excel: 'primary', export: 'warning' }[value] || 'info'
}

function isImage(row) {
  return ['png', 'jpg', 'jpeg', 'gif', 'webp'].includes((row.ext || '').toLowerCase())
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await pageMyFiles({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      category: query.category || undefined,
      // 非管理员不受该参数影响（后端会强制按本人过滤），这里照传即可
      mine: query.mine,
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.msg || e.message || '文件列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function resetQuery() {
  query.category = null
  query.mine = true
  search()
}

function onPageChange(page) {
  pageNum.value = page
  load()
}

function onSizeChange(size) {
  pageSize.value = size
  pageNum.value = 1
  load()
}

async function onFileChange(selectedFile) {
  const raw = selectedFile.raw
  if (!raw) {
    return
  }
  uploading.value = true
  try {
    const result = await uploadFile(raw, uploadCategory.value)
    ElMessage.success(`上传成功：${result.name}（${result.sizeText}）`)
    search()
  } finally {
    uploading.value = false
  }
}

function preview(row) {
  previewUrl.value = row.url
  previewName.value = row.name
  previewVisible.value = true
}

function download(row) {
  // 用后端给的下载地址（带 Content-Disposition），不自己拼 URL
  window.open(row.downloadUrl, '_blank')
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定移除「${row.name}」的文件记录吗？其他人的引用会保留；仍被头像或题目使用的文件需先解除引用。`,
      '提示',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  try {
    await deleteFile(row.id)
    ElMessage.success('文件记录已移除')
    await load()
  } catch (e) { error.value = e.message || '删除失败，请重试' }
}

onMounted(() => {
  if (isStudent.value) uploadCategory.value = 'avatar'
  load()
})

const error = ref(''),
  filename = ref(''),
  viewMode = ref('list'),
  uploadVisible = ref(false),
  uploadQueue = ref([])
const visibleRows = computed(() =>
  rows.value.filter(
    (f) => !filename.value || f.name.toLowerCase().includes(filename.value.toLowerCase()),
  ),
)
async function queueFile(f) {
  if (uploading.value) return
  const raw = f.raw
  if (!raw) return
  const limit = uploadRule.value.mb
  if (!uploadRule.value.ext.includes(raw.name.split('.').pop().toLowerCase())) {
    ElMessage.warning('该分类支持：' + uploadRule.value.ext.join(' / '))
    return
  }
  if (raw.size > limit * 1024 * 1024) {
    ElMessage.warning('文件不能超过 ' + limit + ' MB')
    return
  }
  const q = { id: f.uid, name: f.name, status: '上传中' }
  uploadQueue.value.push(q)
  uploading.value = true
  try {
    await uploadFile(raw, uploadCategory.value)
    q.status = '上传完成'
    await load()
  } catch (e) {
    q.status = '上传失败：' + (e.msg || e.message || '请重试')
  } finally {
    uploading.value = false
  }
}
</script>
<style scoped>
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  padding: 20px;
}
.file-card {
  padding: 16px;
  border: 1px solid #e6e9ef;
  border-radius: 5px;
  min-width: 0;
}
.file-card > strong {
  display: block;
  overflow-wrap: anywhere;
  font-size: 13px;
  margin-top: 14px;
}
.file-art {
  height: 110px;
  background: #f6f8fc;
  display: grid;
  place-items: center;
  color: #7184af;
  font-size: 36px;
  border-radius: 4px;
}
.file-art img {
  height: 100%;
  max-width: 100%;
  object-fit: contain;
}
.file-preview {
  max-width: 100%;
  max-height: 70vh;
  display: block;
  margin: auto;
}
</style>
