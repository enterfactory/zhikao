<template>
  <AppPage title="个人设置" description="管理账号信息、安全与界面偏好。">
    <el-alert v-if="userStore.userInfo?.passwordChangeRequired" title="请先修改初始口令，完成后重新登录。" type="warning" :closable="false" show-icon />
    <div class="console-split">
      <nav class="console-panel console-directory" aria-label="设置分类">
        <button
          v-for="s in SECTIONS"
          :key="s.key"
          :class="{ active: section === s.key }"
          :disabled="userStore.userInfo?.passwordChangeRequired && s.key !== 'security'"
          @click="section = s.key"
        >
          <el-icon><component :is="s.icon" /></el-icon>
          {{ s.label }}
        </button>
        <p class="directory-note">界面偏好保存在当前浏览器。</p>
      </nav>
      <div class="settings-content">
        <template v-if="section === 'account'">
          <section class="console-panel">
            <header class="console-panel-head">个人资料</header>
            <div class="console-panel-body">
              <div class="console-member profile-avatar">
                <el-avatar :size="64" :src="avatarUrl">{{ initial }}</el-avatar>
                <div>
                  <strong>{{ userStore.userInfo?.realName }}</strong>
                  <p class="console-cell-note">
                    {{ roleText }} · {{ userStore.userInfo?.username }}
                  </p>
                </div>
                <el-upload
                  :auto-upload="false"
                  :show-file-list="false"
                  accept="image/png,image/jpeg,image/gif,image/webp"
                  :disabled="uploading"
                  :on-change="onFileChange"
                >
                  <el-button :loading="uploading">更换头像</el-button>
                </el-upload>
                <el-button v-if="avatarUrl" text type="danger" @click="removeAvatar">
                  清除
                </el-button>
              </div>
              <p class="console-form-hint">PNG、JPG、GIF 或 WebP，最大 2 MB。</p>
              <dl class="profile-facts">
                <div v-for="f in accountFacts" :key="f.label">
                  <dt>{{ f.label }}</dt>
                  <dd>{{ f.value }}</dd>
                </div>
              </dl>
              <p class="console-form-hint">姓名、账号与角色由组织管理员维护。</p>
            </div>
          </section>
        </template>
        <template v-else-if="section === 'security'">
          <section class="console-panel">
            <header class="console-panel-head">修改密码</header>
            <div class="console-panel-body">
              <el-form
                ref="formRef"
                :model="form"
                :rules="rules"
                label-position="top"
                class="settings__form"
              >
                <el-form-item label="当前密码" prop="oldPassword">
                  <el-input
                    v-model="form.oldPassword"
                    type="password"
                    show-password
                    placeholder="请输入当前登录密码"
                    autocomplete="current-password"
                  />
                </el-form-item>

                <el-form-item label="新密码" prop="newPassword">
                  <el-input
                    v-model="form.newPassword"
                    type="password"
                    show-password
                    placeholder="至少 8 位，含字母与数字"
                    autocomplete="new-password"
                    @focus="touchedNew = true"
                  />
                  <!-- 强度条与规则清单边输边给反馈：等提交后才发现「少个数字」是最差的一次交互 -->
                  <div v-if="form.newPassword" class="settings__strength">
                    <div class="settings__strength-bars">
                      <i
                        v-for="n in 4"
                        :key="n"
                        class="settings__strength-bar"
                        :class="[`is-${strength.level}`, { 'is-on': n <= strength.score }]"
                      />
                    </div>
                    <span class="settings__strength-label">{{ strength.label }}</span>
                  </div>
                  <ul v-if="touchedNew || form.newPassword" class="settings__rules">
                    <li
                      v-for="rule in passwordRules"
                      :key="rule.text"
                      :class="{ 'is-ok': rule.ok }"
                    >
                      <el-icon :size="13">
                        <component :is="rule.ok ? 'CircleCheckFilled' : 'CircleClose'" />
                      </el-icon>
                      {{ rule.text }}
                    </li>
                  </ul>
                </el-form-item>

                <el-form-item label="确认新密码" prop="confirmPassword">
                  <el-input
                    v-model="form.confirmPassword"
                    type="password"
                    show-password
                    placeholder="再次输入新密码"
                    autocomplete="new-password"
                  />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" :loading="submitting" @click="submit">
                    更新密码
                  </el-button>
                  <el-button :disabled="submitting" @click="resetForm">清空</el-button>
                </el-form-item>
              </el-form>
              <p class="console-form-hint">密码更新后请重新登录，其他设备上的登录将失效。</p>
            </div>
          </section>
          <section class="console-panel">
            <header class="console-panel-head">
              <h3>最近账号活动</h3>
              <el-button :loading="activityLoading" @click="loadActivity">刷新</el-button>
            </header>
            <el-alert v-if="activityError" :title="activityError" type="error" :closable="false" />
            <el-table :data="activityRows" v-loading="activityLoading">
              <el-table-column label="活动" min-width="150">
                <template #default="{ row }">{{ activityTitle(row) }}</template>
              </el-table-column>
              <el-table-column prop="ip" label="IP 地址" min-width="150" />
              <el-table-column label="时间" min-width="160">
                <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </template>
        <template v-else-if="section === 'appearance'">
          <section class="console-panel">
            <header class="console-panel-head">界面偏好</header>
            <div class="console-panel-body">
              <template v-if="userStore.role !== 'student'">
                <div class="settings-row">
                  <div>
                    <h4>侧栏外观</h4>
                    <p>选择浅色或深色导航。</p>
                  </div>
                  <el-radio-group v-model="preferences.sidebar">
                    <el-radio-button value="light">浅色</el-radio-button>
                    <el-radio-button value="dark">深色</el-radio-button>
                  </el-radio-group>
                </div>
                <div class="settings-row">
                  <div>
                    <h4>表格密度</h4>
                    <p>紧凑模式可显示更多记录。</p>
                  </div>
                  <el-radio-group v-model="preferences.density">
                    <el-radio-button value="standard">标准</el-radio-button>
                    <el-radio-button value="compact">紧凑</el-radio-button>
                  </el-radio-group>
                </div>
                <div class="settings-row">
                  <div>
                    <h4>默认收起导航</h4>
                    <p>收起后保留图标和悬浮提示。</p>
                  </div>
                  <el-switch v-model="preferences.collapsed" />
                </div>
              </template>
              <div v-else class="settings-row">
                <h4>界面主题</h4>
                <el-select :model-value="theme" @change="pickTheme">
                  <el-option v-for="t in themes" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </div>
            </div>
          </section>
        </template>
        <template v-else>
          <section class="console-panel">
            <header class="console-panel-head">角色与可用功能</header>
            <div class="console-panel-body">
              <div class="console-summary-row" v-for="code in roleCodes" :key="code">
                <strong>{{ roleLabel(code) }}</strong>
                <span>{{ code }}</span>
              </div>
              <p class="console-form-hint">角色与权限由管理员分配。</p>
              <div class="permission-section" v-for="g in permissionGroups" :key="g.module">
                <h4>{{ g.moduleName }} · {{ g.items.length }}</h4>
                <el-tag v-for="code in g.items" :key="code" size="small" type="info">
                  {{ permLabel(code) }}
                </el-tag>
              </div>
            </div>
          </section>
        </template>
      </div>
    </div>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { PERM_LABELS } from '@/utils/permissions'
import { useRouter } from 'vue-router'
import {
  Camera,
  CircleCheckFilled,
  CircleClose,
  Delete,
  Lock,
  Refresh,
  User,
  Brush,
  Key,
} from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { useUserStore } from '@/store/user'
import { useTheme, THEMES } from '@/composables/useTheme'
import { changePassword, securityActivity } from '@/api/auth'
import { clearAvatar, uploadAvatar } from '@/api/file'

/**
 * 个人设置页。
 *
 * 结构按主流产品的设置页来做：左侧按「改什么」分区（账号 / 安全 / 外观 / 权限），
 * 右侧只渲染当前分区，每区内一条设置一行——标题 + 说明在左、控件在右。
 * 之前是「左头像卡 + 右改密卡」两栏平铺，既看不出还有哪些能改，
 * 也没地方放主题与权限这两个真实存在、却一直没有自助入口的东西。
 *
 * 页面只暴露后端真有接口的能力：账号/姓名/角色没有自助修改接口，就明确标成只读，
 * 不做「输入框能编辑、保存才发现不行」的假界面。
 */
const preferences = useConsolePreferences()
const router = useRouter()
const userStore = useUserStore()
const { theme, setTheme } = useTheme()

const SECTIONS = [
  { key: 'account', label: '账号', desc: '头像与基本信息', icon: User },
  { key: 'security', label: '安全', desc: '密码与账号活动', icon: Lock },
  { key: 'appearance', label: '外观', desc: '界面主题', icon: Brush },
  { key: 'permissions', label: '权限', desc: '角色与功能', icon: Key },
]

const section = ref(userStore.userInfo?.passwordChangeRequired ? 'security' : 'account')

const formRef = ref(null)
const uploading = ref(false)
const submitting = ref(false)
const touchedNew = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// ==================== 身份 ====================

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')
const initial = computed(() => (userStore.userInfo?.realName || 'U').slice(0, 1))

/** persona 码（admin/teacher/student）：菜单与壳层就是按它分流的，这里沿用同一口径 */
const PERSONA_LABELS = { admin: '管理身份', teacher: '教师', student: '学生' }
const roleText = computed(() => PERSONA_LABELS[userStore.role] || '未知身份')
const roleTagType = computed(
  () => ({ admin: 'danger', teacher: 'primary', student: 'success' })[userStore.role] || 'info',
)

const ROLE_LABELS = {
  super_admin: '超级管理员',
  edu_admin: '教务管理员',
  subject_lead: '学科负责人',
  teacher: '教师',
  student: '学生',
}

const roleCodes = computed(() => userStore.userInfo?.roleCodes || [])
const primaryRoleCode = computed(() => roleCodes.value[0] || '')
const permissions = computed(() => userStore.userInfo?.permissions || [])

function roleLabel(code) {
  return ROLE_LABELS[code] || code
}

const accountFacts = computed(() => {
  const info = userStore.userInfo || {}
  return [
    { label: '登录账号', value: info.username || '-', locked: true },
    { label: '姓名', value: info.realName || '-', locked: true },
    { label: '身份', value: roleText.value, locked: true },
    {
      label: '角色',
      value: roleCodes.value.length ? roleCodes.value.map(roleLabel).join('、') : '未分配',
      locked: true,
    },
    { label: '用户 ID', value: info.id ?? '-', title: '数据库主键，报障时提供这个最快' },
  ]
})

// ==================== 权限分组 ====================

const MODULE_LABELS = {
  system: '系统管理',
  question: '题库',
  crawler: '题库采集',
  paper: '组卷',
  exam: '考试',
  marking: '阅卷',
  grade: '成绩',
  analysis: '学情分析',
  ai: '智能助手',
  file: '文件',
  notification: '通知',
  announcement: '公告',
  portal: '学生门户',
  learn: '学习',
}

function permModule(code) {
  return (code || '').split(':')[0]
}

function permLabel(code) {
  const parts = (code || '').split(':')
  // 三段式（question:audit）读起来最自然；后端加新码时原样显示，页面不会因缺映射而漏项
  return PERM_LABELS[code] || parts.join(' · ')
}

/** 与后端 DataInitializer 的权限种子对齐的中文名单；缺项时退回显示权限码本身 */

const permissionGroups = computed(() => {
  const byModule = new Map()
  permissions.value.forEach((code) => {
    const module = permModule(code) || 'other'
    if (!byModule.has(module)) byModule.set(module, [])
    byModule.get(module).push(code)
  })
  return [...byModule.entries()].map(([module, items]) => ({
    module,
    moduleName: MODULE_LABELS[module] || module,
    items,
  }))
})

// ==================== 密码 ====================

/** 与后端 PasswordPolicy 一致：长度 ≥ 8 且字母数字兼备，避免「前端放行、后端报错」 */
const passwordRules = computed(() => {
  const value = form.newPassword || ''
  return [
    { text: '至少 8 位', ok: value.length >= 8 },
    { text: '包含字母', ok: /[a-zA-Z]/.test(value) },
    { text: '包含数字', ok: /\d/.test(value) },
    { text: '与当前密码不同', ok: !!value && value !== form.oldPassword },
  ]
})

/**
 * 强度只用于「边输边提示」，不是提交条件：
 * 后端的硬规则只有长度与字母数字，这里若自加特殊字符要求，就会出现「页面说不合格、
 * 提交却成功」的分裂口径。所以 4 格分的是「离最低要求有多远」。
 */
const strength = computed(() => {
  const value = form.newPassword || ''
  if (!value) return { score: 0, level: 'none', label: '' }
  let score = 0
  if (value.length >= 8) score += 1
  if (/[a-zA-Z]/.test(value) && /\d/.test(value)) score += 1
  if (value.length >= 12) score += 1
  if (/[^a-zA-Z0-9]/.test(value) && value.length >= 10) score += 1
  const levels = ['weak', 'weak', 'fair', 'good', 'strong']
  const labels = { weak: '偏弱', fair: '可用', good: '良好', strong: '扎实' }
  const level = levels[score]
  return { score, level, label: `强度：${labels[level]}` }
})

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const hit = passwordRules.value.find((item) => !item.ok)
        callback(
          hit
            ? new Error(`新密码${hit.text.replace('至少 8 位', '长度需不少于 8 位')}（当前不满足）`)
            : undefined,
        )
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        callback(value !== form.newPassword ? new Error('两次输入的新密码不一致') : undefined)
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码已更新，请使用新密码重新登录')
    // 服务端已撤销所有旧会话，本地清理后重新登录。
    await userStore.logout()
    router.push('/login')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  formRef.value?.resetFields()
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  touchedNew.value = false
}

// ==================== 头像 ====================

/** el-upload 选完文件后的回调：手动上传，便于先做前端校验并给即时反馈 */
async function onFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) {
    return
  }
  const MAX = 2 * 1024 * 1024
  if (raw.size > MAX) {
    ElMessage.warning('图片大小不能超过 2 MB')
    return
  }
  uploading.value = true
  try {
    await uploadAvatar(raw)
    // 头像地址由后端写入，前端不自己拼，所以传完重新拉一次用户信息
    await userStore.fetchInfo()
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
  }
}

async function removeAvatar() {
  try {
    await ElMessageBox.confirm('确定清除当前头像吗？将回退为姓名首字展示', '提示', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  await clearAvatar()
  await userStore.fetchInfo()
  ElMessage.success('已清除头像')
}

// ==================== 外观 ====================

const themes = THEMES

function pickTheme(value) {
  setTheme(value)
}

// ==================== 账号活动 ====================

const activityRows = ref([])
const activityLoading = ref(false)
const activityError = ref('')

async function loadActivity() {
  if (userStore.userInfo?.passwordChangeRequired) return
  activityLoading.value = true
  try {
    const data = await securityActivity(8)
    const list = Array.isArray(data) ? data : []
    // 审计表没有暴露 id 给这个接口，用「时间 + 事件」拼一个稳定 key
    activityRows.value = list.map((item, index) => ({
      ...item,
      key: `${item.operTime || ''}-${item.kind || ''}-${index}`,
    }))
    activityError.value = ''
  } catch (e) {
    activityError.value = e?.message || '账号活动暂时读不到'
  } finally {
    activityLoading.value = false
  }
}

watch(section, (value) => {
  if (value === 'security' && !activityRows.value.length) loadActivity()
})

const ACTIVITY_TITLES = {
  LOGIN: '登录成功',
  LOGIN_FAILED: '登录失败',
  LOGOUT: '退出登录',
  PASSWORD: '修改密码',
}

const ACTIVITY_TONES = {
  LOGIN: 'success',
  LOGIN_FAILED: 'danger',
  LOGOUT: 'neutral',
  PASSWORD: 'warning',
}

function activityTitle(item) {
  return ACTIVITY_TITLES[item.kind] || item.title || '账号操作'
}

function activityTone(item) {
  // 失败原因（如密码错误、被锁定）比「这是一次登录」更值得标出来，所以失败一律红
  if (item.resultStatus === 0) return 'danger'
  return ACTIVITY_TONES[item.kind] || 'neutral'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function relativeTime(value) {
  if (!value) return '未知时间'
  const time = new Date(String(value).replace(/-/g, '/').replace('T', ' '))
  if (Number.isNaN(time.getTime())) return formatTime(value)
  const diff = Math.max(0, Date.now() - time.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `${Math.floor(diff / day)} 天前`
  return formatTime(value)
}

onMounted(() => {
  // 登录态里缺权限/角色（例如旧版本签发的会话）时补一次，权限分区才不会是空的
  if (!userStore.userInfo?.username) {
    userStore.fetchInfo()
  }
})

import { useConsolePreferences } from '@/composables/useConsolePreferences'
</script>
<style scoped>
.settings-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}
.profile-avatar {
  flex-wrap: wrap;
}
.profile-facts {
  margin-top: 24px;
}
.profile-facts > div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  padding: 18px 0;
  border-bottom: 1px solid #e6e9ef;
  font-size: 13px;
}
.profile-facts dt {
  color: #7b8799;
}
.profile-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 0;
  border-bottom: 1px solid #e6e9ef;
}
.settings-row p {
  color: #8490a1;
  font-size: 12px;
  margin-top: 6px;
}
.permission-section {
  margin-top: 24px;
}
.permission-section .el-tag {
  margin: 12px 8px 0 0;
}
.settings__form {
  max-width: 440px;
}
.settings__strength,
.settings__rules {
  font-size: 12px;
  color: #8490a1;
}
.settings__rules {
  padding-left: 16px;
}
.settings__rules li {
  margin-top: 6px;
}
.settings__rules .is-ok {
  color: #28775c;
}
@media (max-width: 700px) {
  .settings-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .profile-facts > div {
    grid-template-columns: 90px minmax(0, 1fr);
  }
}
</style>
