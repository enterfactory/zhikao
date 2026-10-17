<template>
  <AppPage title="科目管理" description="维护教学科目及统一编码。">
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增科目</el-button>
    </template>
    <div>
      <div>
        <div class="console-toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索科目名称或编码"
            :prefix-icon="Search"
            clearable
          />
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
        <DataTable
          :rows="filteredRows"
          :loading="loading"
          :show-pagination="false"
          :error="err"
          empty-title="没有找到科目"
          @retry="load"
        >
          <el-table-column prop="subjectName" label="科目名称" min-width="200">
            <template #default="{ row }">
              <strong class="console-cell-title">{{ row.subjectName }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="subjectCode" label="科目编码" min-width="180" />
          <el-table-column prop="createTime" label="创建时间" min-width="170" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </DataTable>
      </div>
    </div>
    <ZkDrawer
      v-model="dialogVisible"
      :title="form.id ? '编辑科目' : '新增科目'"
      size="md"
      show-footer
      :loading="saving"
      confirm-text="保存科目"
      cancel-text="取消"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="科目名称" prop="subjectName">
          <el-input v-model="form.subjectName" placeholder="如 Java 程序设计" />
        </el-form-item>
        <el-form-item label="科目编码" prop="subjectCode">
          <el-input v-model="form.subjectCode" placeholder="如 JAVA" />
        </el-form-item>
      </el-form>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import DataTable from '@/components/DataTable.vue'
import { deleteSubject, listSubjects, saveSubject } from '@/api/system'

/**
 * 科目量为百级、无后端分页，前端关键字过滤即可；
 * error 态与空态统一交给 DataTable，避免加载失败被误读成「没有科目」。
 */
const rows = ref([])
const keyword = ref('')
const loading = ref(false)
const err = ref('')
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const form = ref({ id: null, subjectName: '', subjectCode: '' })

const rules = {
  subjectName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
}

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return rows.value
  }
  return rows.value.filter((item) =>
    [item.subjectName, item.subjectCode].some((text) => (text || '').toLowerCase().includes(kw)),
  )
})

async function load() {
  loading.value = true
  try {
    rows.value = await listSubjects()
    err.value = ''
  } catch (e) {
    err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = row
    ? { id: row.id, subjectName: row.subjectName, subjectCode: row.subjectCode }
    : { id: null, subjectName: '', subjectCode: '' }
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    await saveSubject(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除科目「${row.subjectName}」吗？`, '危险操作', {
    type: 'warning',
  })
  await deleteSubject(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
