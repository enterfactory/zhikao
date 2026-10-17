<template>
  <AppPage title="班级管理" description="组织班级成员，维护年级与专业信息。">
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增班级</el-button>
    </template>
    <div class="console-split">
      <aside class="console-panel console-directory">
        <h3>年级目录</h3>
        <button :class="{ active: !selectedGrade }" @click="selectedGrade = null">
          全部年级
          <small>{{ rows.length }}</small>
        </button>
        <button
          v-for="g in grades"
          :key="g"
          :class="{ active: selectedGrade === g }"
          @click="selectedGrade = g"
        >
          {{ g }}
        </button>
      </aside>
      <div>
        <div class="console-toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索班级名称、年级或专业"
            :prefix-icon="Search"
            clearable
          />
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
        <DataTable
          :rows="visibleRows"
          :loading="loading"
          :show-pagination="false"
          :error="err"
          empty-title="没有找到班级"
          @retry="load"
        >
          <el-table-column prop="className" label="班级名称" min-width="200">
            <template #default="{ row }">
              <strong class="console-cell-title">{{ row.className }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="grade" label="年级" min-width="120" />
          <el-table-column prop="major" label="专业 / 学段" min-width="150" />
          <el-table-column prop="createTime" label="创建时间" min-width="170" />
          <el-table-column label="操作" width="230">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="
                  () => {
                    router.push({ path: '/admin/user', query: { classId: row.id, userType: 3 } })
                  }
                "
              >
                查看成员
              </el-button>
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </DataTable>
      </div>
    </div>
    <ZkDrawer
      v-model="dialogVisible"
      :title="form.id ? '编辑班级' : '新增班级'"
      size="md"
      show-footer
      :loading="saving"
      confirm-text="保存班级"
      cancel-text="取消"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="如 软件工程2301班" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="form.grade" placeholder="如 2023级" />
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="form.major" placeholder="如 软件工程" />
        </el-form-item>
      </el-form>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { useRouter } from 'vue-router'

import { computed, onMounted, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import DataTable from '@/components/DataTable.vue'
import { deleteClass, listClasses, saveClass } from '@/api/system'

/**
 * 班级量为百级、无后端分页，前端关键字过滤即可；
 * DataTable 关掉分页只吃 rows，空态 / error 态由组件统一承担。
 */
const rows = ref([])
const keyword = ref('')
const loading = ref(false)
const err = ref('')
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const form = ref({ id: null, className: '', grade: '', major: '' })

const rules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
}

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return rows.value
  }
  return rows.value.filter((item) =>
    [item.className, item.grade, item.major].some((text) =>
      (text || '').toLowerCase().includes(kw),
    ),
  )
})

async function load() {
  loading.value = true
  try {
    rows.value = await listClasses()
    err.value = ''
  } catch (e) {
    err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = row
    ? { id: row.id, className: row.className, grade: row.grade, major: row.major }
    : { id: null, className: '', grade: '', major: '' }
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    await saveClass(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除班级「${row.className}」吗？`, '危险操作', {
    type: 'warning',
  })
  await deleteClass(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)

const selectedGrade = ref(null)
const grades = computed(() =>
  [...new Set(rows.value.map((r) => r.grade).filter(Boolean))].sort().reverse(),
)
const visibleRows = computed(() =>
  filteredRows.value.filter((r) => !selectedGrade.value || r.grade === selectedGrade.value),
)
const router = useRouter()
</script>
