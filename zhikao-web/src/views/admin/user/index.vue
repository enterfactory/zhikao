<template>
  <AppPage title="用户管理" description="管理成员账号、身份和班级归属。">
    <template #actions>
      <el-button :icon="Upload" @click="importVisible = true">批量导入</el-button>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增用户</el-button>
    </template>
    <div class="console-split">
      <aside class="console-panel console-directory">
        <h3>组织目录</h3>
        <button
          :class="{ active: !query.classId && !query.userType }"
          @click="
            () => {
              query.classId = null
              query.userType = null
              search()
            }
          "
        >
          <el-icon><User /></el-icon>
          全部成员
        </button>
        <button
          :class="{ active: query.userType === 2 }"
          @click="
            () => {
              query.classId = null
              query.userType = 2
              search()
            }
          "
        >
          <el-icon><User /></el-icon>
          教师团队
        </button>
        <button
          v-for="c in classList"
          :key="c.id"
          :class="{ active: query.classId === c.id }"
          @click="
            () => {
              query.classId = c.id
              query.userType = 3
              search()
            }
          "
        >
          <el-icon><Folder /></el-icon>
          {{ c.className }}
        </button>
        <router-link class="directory-footer" to="/admin/class">管理班级 →</router-link>
      </aside>
      <div>
        <div class="console-workspace-tabs">
          <el-tabs
            :model-value="String(query.userType || '')"
            @tab-change="
              (value) => {
                query.userType = value ? Number(value) : null
                query.classId = null
                search()
              }
            "
          >
            <el-tab-pane label="全部成员" name="" />
            <el-tab-pane label="教师" name="2" />
            <el-tab-pane label="学生" name="3" />
            <el-tab-pane label="管理员" name="1" />
          </el-tabs>
        </div>
        <div class="console-toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索姓名、账号"
            :prefix-icon="Search"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <div class="console-toolbar-actions">
            <el-select v-model="query.status" placeholder="全部状态" clearable @change="search">
              <el-option label="正常" :value="1" />
              <el-option label="已停用" :value="0" />
            </el-select>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
        <DataTable
          :rows="rows"
          :total="total"
          :loading="loading"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          :error="err"
          empty-title="没有找到成员"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @retry="load"
        >
          <el-table-column label="成员" min-width="210">
            <template #default="{ row }">
              <div class="console-member">
                <el-avatar :size="32">
                  {{ (row.realName || row.username || '用').slice(0, 1) }}
                </el-avatar>
                <div>
                  <strong class="console-cell-title">{{ row.realName }}</strong>
                  <small class="console-cell-note">{{ row.username }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="130">
            <template #default="{ row }">
              <el-tag>{{ roleLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="className" label="班级" min-width="140">
            <template #default="{ row }">{{ row.className || '未分配' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '正常' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190">
            <template #default="{ row }">
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button link type="primary" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-dropdown trigger="click">
                <el-button link type="primary">
                  更多
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleResetPassword(row)">重置密码</el-dropdown-item>
                    <el-dropdown-item divided @click="handleDelete(row)">删除成员</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </DataTable>
      </div>
    </div>
    <ZkDrawer
      v-model="dialogVisible"
      :title="form.id ? '编辑用户' : '新增用户'"
      description="创建成员账号并分配工作范围"
      size="md"
      show-footer
      :loading="saving"
      confirm-text="保存用户"
      cancel-text="取消"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <h3 class="console-section-label">基本信息</h3>
        <div class="console-form-grid">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model="form.username" placeholder="唯一的登录账号" />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" />
          </el-form-item>
        </div>
        <el-form-item label="联系手机" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <h3 class="console-section-label">身份与归属</h3>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode" @change="handleRoleChange">
            <el-option
              v-for="r in roleList"
              :key="r.roleCode"
              :label="r.roleName"
              :value="r.roleCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="showUserTypeFallback" label="身份" prop="userType">
          <el-select v-model="form.userType">
            <el-option
              v-for="r in PERSONA_OPTIONS"
              :key="r.value"
              :label="r.label"
              :value="r.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isStudentForm" label="所属班级" prop="classId">
          <el-select v-model="form.classId" filterable>
            <el-option v-for="c in classList" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.id ? '修改密码' : '初始密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.id ? '留空保留原密码' : '请设置强初始密码'"
          />
          <p class="console-form-hint">初始口令至少 8 位，包含字母与数字；24 小时内首次登录后须修改。</p>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="正常"
          />
        </el-form-item>
      </el-form>
    </ZkDrawer>
    <ImportWizard v-model="importVisible" @done="load" />
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ZkDrawer from '@/components/ZkDrawer.vue'
import ImportWizard from '@/components/ImportWizard.vue'
const importVisible = ref(false)
const route = useRoute()
import {
  CircleCheck,
  CircleClose,
  Delete,
  Download,
  Edit,
  Key,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  Upload,
} from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import {
  deleteUser,
  downloadUserTemplate,
  importUsers,
  listClasses,
  listRbacRoles,
  pageUsers,
  resetUserPassword,
  saveUser,
  updateUserStatus,
} from '@/api/system'
import { saveBlob } from '@/utils/download'

/**
 * 角色选择器的数据流：
 * 1. 数据源是 /system/role/list（[{ id, roleCode, roleName, persona }]）；
 *    接口失败时退回下面的 FALLBACK_ROLES，保证表单永远可选。
 * 2. persona 映射 userType（1 管理 / 2 教师 / 3 学生）随表单一起提交，
 *    后端 DTO 新增 roleCode 前旧服务也能按 userType 正确落库。
 * 3. 若某条角色数据缺失 persona，表单显式退回「用户类型」下拉（showUserTypeFallback），
 *    此时仅额外附带 roleCode，不猜映射。
 */
const FALLBACK_ROLES = [
  { roleCode: 'super_admin', roleName: '超级管理员', persona: 1 },
  { roleCode: 'edu_admin', roleName: '教务管理员', persona: 1 },
  { roleCode: 'subject_lead', roleName: '科目负责人', persona: 2 },
  { roleCode: 'teacher', roleName: '教师', persona: 2 },
  { roleCode: 'student', roleName: '学生', persona: 3 },
]

// 列表筛选仍按身份（userType）过滤：后端 /user/page 的查询契约没变
const PERSONA_OPTIONS = [
  { label: '管理员', value: 1 },
  { label: '教师', value: 2 },
  { label: '学生', value: 3 },
]

// 存量用户没有 roleCode 时，按 userType 反推一个默认角色回显
const DEFAULT_ROLE_BY_USER_TYPE = { 1: 'edu_admin', 2: 'teacher', 3: 'student' }

const defaultQuery = () => ({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  userType: null,
  classId: null,
  status: null,
})
const defaultForm = () => ({
  id: null,
  username: '',
  realName: '',
  roleCode: 'teacher',
  userType: 2,
  classId: null,
  phone: '',
  password: '',
  status: 1,
})

const query = ref(defaultQuery())
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const err = ref('')
const classList = ref([])
const roleList = ref(FALLBACK_ROLES)

const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const form = ref(defaultForm())

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }],
  // classId 只在学生表单里渲染（v-if），required 规则随之只在学生场景生效
  classId: [{ required: true, message: '请选择所属班级', trigger: 'change' }],
  // 新账号必须设置初始口令；编辑时留空保留原口令。
  // 原来写 6~32，用户按提示填完仍会被后端拒绝——前后端口径必须一致。
  password: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback(form.value.id ? undefined : new Error('请设置初始密码'))
        if (value.length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
          return callback(new Error('密码至少 8 位，且需同时包含字母与数字'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  phone: [{ pattern: /^$|^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

const selectedRole = computed(() =>
  roleList.value.find((item) => item.roleCode === form.value.roleCode),
)

// 缺失 persona 的角色无法自动映射 userType，显式请用户选择而不是猜测
const showUserTypeFallback = computed(
  () => Boolean(selectedRole.value) && selectedRole.value.persona == null,
)

const isStudentForm = computed(() =>
  showUserTypeFallback.value
    ? form.value.userType === 3
    : Number(selectedRole.value?.persona) === 3,
)

function personaLabel(role) {
  return { 1: '管理', 2: '教师', 3: '学生' }[Number(role?.persona)] || '未映射'
}

function roleLabel(row) {
  if (row.roleName) {
    return row.roleName
  }
  const codes = row.roleCodes || (row.roleCode ? [row.roleCode] : [])
  const names = codes.map(
    (code) => roleList.value.find((item) => item.roleCode === code)?.roleName || code,
  )
  return names.join('、') || row.userTypeName || '-'
}

async function load() {
  loading.value = true
  try {
    const res = await pageUsers(query.value)
    rows.value = res.records || []
    total.value = res.total || 0
    err.value = ''
  } catch (e) {
    err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 角色 / 班级是辅助数据：失败时静默退回内置值，不打断主列表
async function loadRoles() {
  try {
    const data = await listRbacRoles()
    if (Array.isArray(data) && data.length) {
      roleList.value = data
    }
  } catch (e) {
    roleList.value = FALLBACK_ROLES
  }
}

async function loadClasses() {
  try {
    classList.value = await listClasses()
  } catch (e) {
    classList.value = []
  }
}

function search() {
  query.value.pageNum = 1
  err.value = ''
  load()
}

function resetQuery() {
  query.value = defaultQuery()
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

function openForm(row) {
  if (row) {
    const role =
      roleList.value.find((item) => item.roleCode === (row.roleCodes?.[0] || row.roleCode)) ||
      roleList.value.find((item) => item.roleCode === DEFAULT_ROLE_BY_USER_TYPE[row.userType]) ||
      roleList.value.find((item) => Number(item.persona) === Number(row.userType))
    form.value = {
      id: row.id,
      username: row.username,
      realName: row.realName,
      roleCode: row.roleCodes?.[0] || row.roleCode || role?.roleCode || 'teacher',
      userType: row.userType,
      classId: row.classId,
      phone: row.phone,
      password: '',
      status: row.status,
    }
  } else {
    form.value = defaultForm()
    applyRoleToUserType()
  }
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

// persona → userType；无 persona 的角色不动 userType（由兜底下拉决定）
function applyRoleToUserType() {
  const role = selectedRole.value
  if (role && role.persona != null) {
    form.value.userType = Number(role.persona)
  }
}

// 修复原缺陷：切换角色后必须清掉学生场景的班级值与校验残留，
// 否则教师账号会带着旧 classId 提交、或切回学生时残留过期的红字提示
function handleRoleChange() {
  applyRoleToUserType()
  if (!isStudentForm.value) {
    form.value.classId = null
  }
  formRef.value?.clearValidate('classId')
}

async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    applyRoleToUserType()
    await saveUser({ ...form.value })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleToggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  await updateUserStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}

async function handleResetPassword(row) {
  await ElMessageBox.confirm(`确定将「${row.realName}」的密码重置为默认密码吗？`, '提示', {
    type: 'warning',
  })
  const password = await resetUserPassword(row.id)
  ElMessageBox.alert(`临时口令：${password}（24 小时内有效，登录后须修改）`, '重置成功', { type: 'success' })
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除用户「${row.realName}」吗？其作答与成绩记录将一并失效。`,
    '危险操作',
    { type: 'warning' },
  )
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  load()
}

// 返回 false 阻止 el-upload 的默认上传行为，改由本方法手动提交
async function handleImport(file) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await importUsers(formData)

  if (res.failCount > 0) {
    ElMessageBox.alert(
      `成功 ${res.successCount} 条，失败 ${res.failCount} 条：\n${res.errors.join('\n')}`,
      '导入结果',
      { type: 'warning' },
    )
  } else {
    ElMessage.success(`成功导入 ${res.successCount} 条`)
  }
  load()
  return false
}

async function handleDownloadTemplate() {
  const blob = await downloadUserTemplate()
  saveBlob(blob, '用户导入模板.xlsx')
}

onMounted(() => {
  if (route.query.classId) query.value.classId = Number(route.query.classId)
  if (route.query.userType) query.value.userType = Number(route.query.userType)
  loadRoles()
  loadClasses()
  load()
})
</script>
