<template>
  <AppPage title="角色与权限" description="查看角色能力范围，为成员分配合适的身份。">
    <template #actions>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button
        type="primary"
        :disabled="!current"
        @click="router.push({ path: '/admin/user', query: { userType: current.persona } })"
      >
        管理成员
      </el-button>
    </template>
    <el-alert v-if="err" :title="err" type="error" :closable="false" />
    <div class="console-split">
      <aside v-loading="loading" class="console-panel console-directory">
        <h3>角色目录 · {{ rows.length }}</h3>
        <button
          v-for="r in rows"
          :key="r.id"
          :class="{ active: current?.id === r.id }"
          @click="openDetail(r)"
        >
          <el-icon><Key /></el-icon>
          {{ r.roleName }}
        </button>
        <p class="directory-note">角色定义由组织统一维护，权限矩阵只读。</p>
      </aside>
      <section class="console-panel">
        <header class="console-panel-head">
          <div>
            <h3>{{ current?.roleName || '选择一个角色' }}</h3>
            <p class="console-cell-note">
              {{ current?.roleCode }} · {{ roleRemark[current?.roleCode] || '' }}
            </p>
          </div>
          <el-tag>只读权限</el-tag>
        </header>
        <div v-loading="permLoading" class="console-panel-body">
          <el-alert
            title="权限配置为只读。成员角色可在用户管理中调整。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-alert
            v-if="permissionError"
            :title="permissionError"
            type="error"
            :closable="false"
          />
          <div v-if="permissions.length" class="permission-matrix">
            <section v-for="g in permissionGroups" :key="g.label">
              <h3>{{ g.label }}</h3>
              <div v-for="p in g.items" :key="permKey(p)" class="permission-item">
                <el-icon><CircleCheck /></el-icon>
                <span>{{ permLabel(p) }}</span>
              </div>
            </section>
          </div>
          <el-empty
            v-else-if="!permLoading && !permissionError"
            description="当前角色没有可展示的权限明细"
            :image-size="72"
          />
        </div>
      </section>
    </div>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
import { Refresh } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import { PERM_LABELS } from '@/utils/permissions'
import { getRolePermissions, listRbacRoles } from '@/api/system'

const roleRemark = {
  super_admin: '系统最高权限：全部管理功能与配置',
  edu_admin: '教务管理：用户 / 班级 / 科目 / 角色查看',
  subject_lead: '科目负责：本科目题库、组卷与考试',
  teacher: '题库维护、组卷、发布考试、阅卷与成绩统计',
  student: '参加考试、查看本人成绩与错题解析',
  admin: '系统全部权限：用户 / 班级 / 科目 / 角色管理',
}

const rows = ref([])
const loading = ref(false)
const err = ref('')

const current = ref(null)
const permissions = ref([])
const permLoading = ref(false)
const permissionError = ref('')
let permissionRequest = 0

function permLabel(perm) {
  if (typeof perm === 'string') {
    return PERM_LABELS[perm] || perm
  }
  return perm?.permissionName || perm?.name || perm?.code || '-'
}

function permKey(perm) {
  return typeof perm === 'string' ? perm : (perm?.id ?? perm?.code ?? permLabel(perm))
}

async function load() {
  loading.value = true
  try {
    rows.value = await listRbacRoles()
    const next = rows.value.find((r) => r.id === current.value?.id) || rows.value[0]
    if (next) await openDetail(next)
    else {
      permissionRequest++
      current.value = null
      permissions.value = []
      permissionError.value = ''
      permLoading.value = false
    }
    err.value = ''
  } catch (e) {
    err.value = e?.msg || e?.message || '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  current.value = row
  const request = ++permissionRequest
  permissionError.value = ''

  // 列表已带权限时直接复用，避免再打一次接口
  if (Array.isArray(row.permissions) && row.permissions.length) {
    permissions.value = row.permissions
    permLoading.value = false
    return
  }

  permissions.value = []
  permLoading.value = true
  try {
    const data = await getRolePermissions(row.id)
    if (request === permissionRequest)
      permissions.value = Array.isArray(data) ? data : data?.records || data?.list || []
  } catch (e) {
    if (request === permissionRequest) {
      permissions.value = []
      permissionError.value = e?.msg || e?.message || '权限明细加载失败，请重试'
    }
  } finally {
    if (request === permissionRequest) permLoading.value = false
  }
}

onMounted(load)

const permissionGroups = computed(() => {
  const groups = new Map()
  for (const p of permissions.value) {
    const code = typeof p === 'string' ? p : p.permissionCode || p.code || p.permissionName || ''
    const key = code.split(':')[0]
    const label =
      {
        system: '组织与系统',
        question: '题库管理',
        paper: '试卷与组卷',
        exam: '考试管理',
        marking: '阅卷评分',
        grade: '成绩统计',
        analysis: '学情分析',
        crawler: '资料与识别',
        ai: '智能工具',
        file: '文件空间',
        learn: '学习管理',
      }[key] || '其他权限'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label).push(p)
  }
  return [...groups].map(([label, items]) => ({ label, items }))
})
</script>
<style scoped>
.permission-matrix {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin-top: 26px;
}
.permission-matrix h3 {
  font-size: 13px;
  margin: 0 0 16px;
}
.permission-item {
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: 12px;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.permission-item .el-icon {
  color: #8490a1;
  flex-shrink: 0;
}
@media (max-width: 650px) {
  .permission-matrix {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
