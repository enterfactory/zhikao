<template>
  <div
    class="console-app console-v4"
    :class="{
      'is-collapsed': prefs.collapsed,
      'is-compact': prefs.density === 'compact',
      'has-dark-sidebar': prefs.sidebar === 'dark',
      'mobile-expanded': mobileMenu,
    }"
  >
    <aside class="console-sidebar">
      <router-link to="/home" class="product-brand" aria-label="智考首页">
        <span class="brand-symbol">Z</span>
        <span class="brand-name">
          <strong>智考</strong>
          <small>教学与测评</small>
        </span>
      </router-link>
      <div class="console-organization">
        <span class="organization-avatar">
          <el-icon><School /></el-icon>
        </span>
        <span>
          教学管理空间
          <small>{{ user.role === 'admin' ? '组织与教学管理' : '教师工作空间' }}</small>
        </span>
      </div>
      <nav aria-label="管理导航">
        <section v-for="group in groups" :key="group.label" class="console-menu-group">
          <button
            class="console-group-label"
            :aria-expanded="!closedGroups.includes(group.label)"
            @click="toggleGroup(group.label)"
          >
            <span>{{ group.label }}</span>
            <el-icon>
              <ArrowDown v-if="!closedGroups.includes(group.label)" />
              <ArrowRight v-else />
            </el-icon>
          </button>
          <div
            v-show="
              !closedGroups.includes(group.label) || prefs.collapsed || (narrow && !mobileMenu)
            "
            class="console-nav-items"
          >
            <el-tooltip
              v-for="n in group.nodes"
              :key="n.path"
              :content="n.title || n.meta.title"
              placement="right"
              :disabled="!prefs.collapsed && !narrow"
            >
              <router-link
                :to="'/' + n.path"
                :aria-label="n.title || n.meta.title"
                @click="mobileMenu = false"
              >
                <el-icon><component :is="n.meta.icon || 'Document'" /></el-icon>
                <span class="console-nav-label">{{ n.title || n.meta.title }}</span>
              </router-link>
            </el-tooltip>
          </div>
        </section>
      </nav>
      <div class="console-sidebar-bottom">
        <small>智考开源版</small>
        <button
          :aria-label="(narrow ? !mobileMenu : prefs.collapsed) ? '展开菜单' : '折叠菜单'"
          :aria-expanded="narrow ? mobileMenu : !prefs.collapsed"
          @click="toggleSidebar"
        >
          <el-icon>
            <Expand v-if="narrow ? !mobileMenu : prefs.collapsed" />
            <Fold v-else />
          </el-icon>
          <span>{{ prefs.collapsed ? '展开侧栏' : '收起侧栏' }}</span>
        </button>
      </div>
    </aside>
    <button
      v-if="mobileMenu"
      class="sidebar-scrim"
      aria-label="关闭菜单"
      @click="mobileMenu = false"
    ></button>
    <div class="console-body">
      <header class="console-header">
        <div class="console-breadcrumb">
          <span>{{ user.role === 'admin' ? '系统管理' : '教学管理' }}</span>
          <el-icon><ArrowRight /></el-icon>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="account-tools">
          <button
            class="console-command-button"
            aria-label="查找功能"
            @click="searchVisible = true"
          >
            <el-icon><Search /></el-icon>
            <span>查找功能</span>
            <kbd>Ctrl K</kbd>
          </button>
          <NotificationBell />
          <el-dropdown trigger="click">
            <button class="account-button">
              <span class="user-avatar">
                <img v-if="user.userInfo?.avatar" :src="user.userInfo.avatar" alt="账户头像" />
                <template v-else>{{ name.slice(0, 1) }}</template>
              </span>
              <span class="account-label">{{ name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="console-main"><router-view /></main>
      <footer class="console-footer">
        <span>智考 · 教学与测评</span>
        <span>让教学井然有序</span>
      </footer>
    </div>
    <el-dialog
      v-model="searchVisible"
      title="查找功能"
      width="min(560px, calc(100vw - 32px))"
      class="console-command-dialog"
      @opened="searchInput?.focus()"
      @closed="searchText = ''"
    >
      <el-input
        ref="searchInput"
        v-model="searchText"
        placeholder="搜索功能名称，例如：阅卷、题库"
        :prefix-icon="Search"
        clearable
      />
      <div class="console-command-results">
        <router-link
          v-for="n in searchResults"
          :key="n.path"
          :to="'/' + n.path"
          @click="searchVisible = false"
        >
          <el-icon><component :is="n.meta.icon || 'Document'" /></el-icon>
          <span>{{ n.title || n.meta.title }}</span>
          <small>{{ n.meta.group || '工作空间' }}</small>
          <el-icon><ArrowRight /></el-icon>
        </router-link>
        <el-empty v-if="!searchResults.length" description="没有找到匹配的功能" :image-size="64" />
      </div>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { visibleRoutes } from '@/composables/useMenu'
import { useConsolePreferences } from '@/composables/useConsolePreferences'
import NotificationBell from '@/components/NotificationBell.vue'
const route = useRoute(),
  router = useRouter(),
  user = useUserStore(),
  prefs = useConsolePreferences()
const mobileMenu = ref(false),
  narrow = ref(innerWidth < 900),
  closedGroups = ref([])
const searchVisible = ref(false),
  searchText = ref(''),
  searchInput = ref()
const name = computed(() => user.userInfo?.realName || user.userInfo?.username || '用户')
const titles = {
  home: user.role === 'admin' ? '组织概览' : '教学工作台',
  'teacher/marking': '阅卷工作台',
  'teacher/paper': '组卷管理',
  'admin/crawler': '采集与识别',
  'admin/role': '角色与权限',
  'file/my': '文件空间',
}
const pageTitle = computed(() => titles[route.path.slice(1)] || route.meta.title)
const nodes = computed(() =>
  visibleRoutes(router, user.role, user.permissions)
    .filter((n) => n.path !== 'demo/animation')
    .map((n) => ({ ...n, title: titles[n.path] })),
)
const groups = computed(() => {
  const definitions =
    user.role === 'admin'
      ? [
          ['组织与权限', ['home', 'admin/user', 'admin/class', 'admin/subject', 'admin/role']],
          [
            '教学资源',
            [
              'resources/imports',
              'resources/review',
              'resources/quality',
              'teacher/question',
              'teacher/ai-question',
              'teacher/paper',
            ],
          ],
          [
            '测评管理',
            [
              'teacher/exam',
              'teacher/monitor',
              'teacher/marking',
              'teacher/grade',
              'teacher/analysis',
            ],
          ],
          ['系统管理', ['admin/dict', 'admin/crawler', 'admin/log']],
          ['工作工具', ['ai/chat', 'file/my', 'profile']],
        ]
      : [
          ['工作台', ['home']],
          [
            '教学资源',
            [
              'resources/imports',
              'resources/review',
              'resources/quality',
              'teacher/question',
              'teacher/ai-question',
              'teacher/paper',
            ],
          ],
          [
            '测评管理',
            [
              'teacher/exam',
              'teacher/monitor',
              'teacher/marking',
              'teacher/grade',
              'teacher/analysis',
            ],
          ],
          ['工作工具', ['ai/chat', 'file/my', 'profile']],
        ]
  return definitions
    .map(([label, paths]) => ({
      label,
      nodes: paths.map((p) => nodes.value.find((n) => n.path === p)).filter(Boolean),
    }))
    .filter((g) => g.nodes.length)
})
const searchResults = computed(() =>
  nodes.value.filter((n) =>
    `${n.title || n.meta.title} ${n.meta.group || ''}`.includes(searchText.value.trim()),
  ),
)
function toggleGroup(label) {
  closedGroups.value = closedGroups.value.includes(label)
    ? closedGroups.value.filter((g) => g !== label)
    : [...closedGroups.value, label]
}
function toggleSidebar() {
  if (narrow.value) mobileMenu.value = !mobileMenu.value
  else prefs.collapsed = !prefs.collapsed
}
function resize() {
  narrow.value = innerWidth < 900
  if (!narrow.value) mobileMenu.value = false
}
function keydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchVisible.value = !searchVisible.value
  }
}
async function logout() {
  await user.logout()
  router.replace('/login')
}
onMounted(() => {
  document.body.classList.add('console-mode')
  window.addEventListener('resize', resize)
  window.addEventListener('keydown', keydown)
})
onBeforeUnmount(() => {
  document.body.classList.remove('console-mode')
  window.removeEventListener('resize', resize)
  window.removeEventListener('keydown', keydown)
})
</script>
