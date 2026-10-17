<template>
  <AppPage title="数据字典" description="维护统一的下拉选项、展示文案和状态取值。">
    <template #actions>
      <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openTypeDialog()">新增字典类型</el-button>
    </template>
    <div class="console-split">
      <aside v-loading="list.loading" class="console-panel console-directory">
        <h3>字典类型</h3>
        <el-input
          v-model="list.query.keyword"
          placeholder="搜索字典"
          clearable
          @keyup.enter="list.search()"
          @clear="list.search()"
        />
        <el-alert v-if="list.error" :title="list.error" type="error" :closable="false" />
        <button
          v-for="r in list.rows"
          :key="r.id"
          :class="{ active: currentType?.id === r.id }"
          @click="openItems(r)"
        >
          {{ r.dictName }}
          <small>{{ r.itemCount || 0 }}</small>
        </button>
        <el-pagination
          v-if="list.total > list.pageSize"
          small
          layout="prev,next"
          :current-page="list.pageNum"
          :page-size="list.pageSize"
          :total="list.total"
          @current-change="list.onPageChange"
        />
      </aside>
      <div>
        <section class="console-panel-head console-panel">
          <div>
            <h3>{{ currentType?.dictName || '选择字典类型' }}</h3>
            <span class="console-cell-note">
              {{ currentType?.dictType }} ·
              {{ isBuiltIn(currentType?.dictType) ? '内置类型' : '自定义类型' }}
            </span>
          </div>
          <div class="console-toolbar-actions">
            <el-button :disabled="!currentType" @click="openTypeDialog(currentType)">
              编辑类型
            </el-button>
            <el-button
              :disabled="!currentType || isBuiltIn(currentType?.dictType)"
              @click="removeType(currentType)"
            >
              删除类型
            </el-button>
            <el-button
              type="primary"
              :icon="Plus"
              :disabled="!currentType"
              @click="openItemDialog()"
            >
              新增字典项
            </el-button>
          </div>
        </section>
        <DataTable
          :rows="items"
          :loading="itemLoading"
          :error="itemError"
          :show-pagination="false"
          empty-title="暂无字典项"
          @retry="loadItems"
        >
          <el-table-column prop="label" label="展示文案" min-width="130" />
          <el-table-column prop="value" label="字段取值" min-width="110" />
          <el-table-column prop="sort" label="排序" width="85" />
          <el-table-column label="显示样式" width="120">
            <template #default="{ row }">
              <el-tag :type="row.color === 'primary' ? undefined : row.color">
                {{ row.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="135">
            <template #default="{ row }">
              <el-button link type="primary" @click="openItemDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="removeItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </DataTable>
        <p class="console-note" style="margin-top: 14px">
          停用后不再出现在业务选项中。内置类型保留，避免影响已有数据。
        </p>
      </div>
    </div>
    <ZkDrawer
      v-model="typeDialog"
      :title="form.id ? '编辑字典类型' : '新增字典类型'"
      description="类型编码一旦确定不可修改：它已经被各页面的下拉框引用，改名等于让线上取值对不上"
      size="md"
      show-footer
      cancel-text="取消"
      :loading="saving"
      confirm-text="保存"
      @confirm="saveType"
    >
      <el-form
        ref="typeFormRef"
        :model="form"
        :rules="typeRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="form.dictName" placeholder="如：题型" />
        </el-form-item>
        <el-form-item label="类型编码" prop="dictType">
          <el-input
            v-model="form.dictType"
            placeholder="如：question_type（小写字母开头，可含数字与下划线）"
            :disabled="Boolean(form.id)"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="这个字典给谁用、改了会影响哪些页面"
          />
        </el-form-item>
      </el-form>
    </ZkDrawer>
    <ZkDrawer
      v-model="itemDialog"
      :title="itemForm.id ? '编辑字典项' : '新增字典项'"
      size="md"
      show-footer
      cancel-text="取消"
      :loading="saving"
      confirm-text="保存"
      @confirm="saveItem"
    >
      <el-form
        ref="itemFormRef"
        :model="itemForm"
        :rules="itemRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="展示文案" prop="itemLabel">
          <el-input v-model="itemForm.itemLabel" placeholder="如：单选题" />
        </el-form-item>
        <el-form-item label="取值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" placeholder="如：1" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-select v-model="itemForm.color">
            <el-option v-for="c in COLORS" :key="c" :label="c" :value="c">
              <span class="dict-color" :class="`is-${c}`">
                <i />
                {{ c }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <div class="dict-form__pair">
          <el-form-item label="排序">
            <el-input-number
              v-model="itemForm.sort"
              :min="0"
              :max="999"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="itemForm.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </div>
      </el-form>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/FilterBar.vue'
import FilterField from '@/components/FilterField.vue'
import RowActions from '@/components/RowActions.vue'
import ZkDialog from '@/components/ZkDialog.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import { usePagedQuery } from '@/composables/useAsync'
import { confirmAction, notifyError, notifySuccess, validateForm } from '@/utils/feedback'
import {
  dictItemList,
  dictRemoveItem,
  dictRemoveType,
  dictSaveItem,
  dictSaveType,
  dictTypePage,
} from '@/api/dict'
import { useDictStore } from '@/store/dict'

/**
 * 数据字典管理页。
 *
 * 一条重要的设计边界：**内置字典不允许删除类型**（只允许改文案/排序/颜色/停用）。
 * 原因：内置类型的取值被前端展示层直接引用，删掉类型会让页面出现空白选项，
 * 而这类问题只在用户点开某个页面时才会暴露，排查成本很高。
 */
const BUILT_IN = [
  'question_type',
  'difficulty',
  'user_role',
  'exam_status',
  'exam_published',
  'exam_student_status',
  'paper_gen_type',
  'paper_status',
  'ai_task_type',
  'ai_task_status',
]

const COLORS = ['primary', 'success', 'warning', 'danger', 'info']

const dictStore = useDictStore()

const list = reactive(
  usePagedQuery(
    (params) =>
      dictTypePage({
        keyword: params.keyword || undefined,
        pageNum: params.pageNum,
        pageSize: params.pageSize,
      }),
    {
      defaultQuery: () => ({ keyword: '' }),
      errorText: '字典类型读取失败，请检查后端服务是否可用',
    },
  ),
)

const saving = ref(false)

const typeDialog = ref(false)
const typeFormRef = ref(null)
const form = reactive({ id: null, dictName: '', dictType: '', remark: '', status: 1 })
const typeRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]{1,49}$/,
      message: '只能由小写字母、数字、下划线组成，且以字母开头',
      trigger: 'blur',
    },
  ],
}

const itemDrawer = ref(false)
const currentType = ref(null)
const items = ref([])
const itemLoading = ref(false)
const itemError = ref('')
const itemDialog = ref(false)
const itemFormRef = ref(null)
const itemForm = reactive({
  id: null,
  dictType: '',
  itemLabel: '',
  itemValue: '',
  color: 'primary',
  sort: 0,
  status: 1,
})
const itemRules = {
  itemLabel: [{ required: true, message: '请输入展示文案', trigger: 'blur' }],
  itemValue: [{ required: true, message: '请输入取值', trigger: 'blur' }],
}

function isBuiltIn(type) {
  return BUILT_IN.includes(type)
}

/** 操作列规则：高频的两个（字典项、编辑）平铺，删除收进「更多」并标红 */
const typeActions = [
  { key: 'items', label: '字典项', run: openItems },
  { key: 'edit', label: '编辑', run: openTypeDialog },
  {
    key: 'remove',
    label: '删除类型',
    run: removeType,
    danger: true,
    disabled: (row) => isBuiltIn(row.dictType),
  },
]

const itemActions = [
  { key: 'edit', label: '编辑', run: openItemDialog },
  { key: 'remove', label: '删除', run: removeItem, danger: true },
]

function openTypeDialog(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    dictName: row?.dictName ?? '',
    dictType: row?.dictType ?? '',
    remark: row?.remark ?? '',
    status: row?.status ?? 1,
  })
  typeDialog.value = true
}

async function saveType() {
  if (!(await validateForm(typeFormRef.value))) return
  saving.value = true
  try {
    await dictSaveType(form)
    notifySuccess('字典类型已保存')
    typeDialog.value = false
    await reloadAll()
  } catch (e) {
    notifyError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeType(row) {
  const ok = await confirmAction({
    title: '删除字典类型',
    message: `将删除「${row.dictName}」及其下全部 ${row.itemCount ?? 0} 个字典项，此操作不可恢复。确定继续吗？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await dictRemoveType(row.id)
    notifySuccess('字典类型已删除')
    await reloadAll()
  } catch (e) {
    notifyError(e, '删除失败')
  }
}

/** 先开抽屉再取数：以前是 await 完才开，点「字典项」会像没反应一样卡一下 */
function openItems(row) {
  currentType.value = row
  items.value = []
  itemError.value = ''
  itemDrawer.value = false
  loadItems()
}

async function loadItems() {
  if (!currentType.value) return
  itemLoading.value = true
  try {
    items.value = (await dictItemList(currentType.value.dictType)) || []
    itemError.value = ''
  } catch (e) {
    items.value = []
    itemError.value = e?.msg || e?.message || '字典项读取失败'
  } finally {
    itemLoading.value = false
  }
}

function openItemDialog(row) {
  Object.assign(itemForm, {
    id: row?.id ?? null,
    dictType: row?.dictType ?? currentType.value?.dictType,
    itemLabel: row?.label ?? '',
    itemValue: row?.value ?? '',
    color: row?.color ?? 'primary',
    sort: row?.sort ?? 0,
    status: row?.status ?? 1,
  })
  itemDialog.value = true
}

async function saveItem() {
  if (!(await validateForm(itemFormRef.value))) return
  saving.value = true
  try {
    await dictSaveItem(itemForm)
    notifySuccess('字典项已保存')
    itemDialog.value = false
    await loadItems()
    await reloadAll()
  } catch (e) {
    notifyError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeItem(row) {
  const ok = await confirmAction({
    title: '删除字典项',
    message: `确定删除「${row.label}」（取值 ${row.value}）吗？引用该类型的页面会立即少掉这个选项。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await dictRemoveItem(row.id)
    notifySuccess('字典项已删除')
    await loadItems()
    await reloadAll()
  } catch (e) {
    notifyError(e, '删除失败')
  }
}

/** 字典内容变了要同时刷类型计数与前端缓存，否则页面还在用旧文案 */
async function reloadAll() {
  await list.load()
  dictStore.reset()
}

watch(
  () => list.rows,
  (rows) => {
    const next = rows.find((r) => r.id === currentType.value?.id) || rows[0]
    if (next) openItems(next)
    else {
      currentType.value = null
      items.value = []
    }
  },
)
</script>
