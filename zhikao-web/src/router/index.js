import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/login/index.vue'
import Layout from '@/views/layout/index.vue'
import Home from '@/views/home/index.vue'
import { useUserStore } from '@/store/user'
import { canAccess } from '@/composables/useMenu'

/**
 * 路由表。
 * meta 约定：
 * - title       菜单与页头标题（同时用于页签）
 * - icon        Element Plus 图标名（须在 utils/icons.js 里注册）
 * - group       菜单分组（相同 group 折叠为一个子菜单）
 * - roles       可访问的粗粒度身份（admin/teacher/student）——决定页面出现在哪套壳
 * - perms       可访问的权限点（RBAC）；多个为「任一满足」。
 *               与 roles 两级判定：roles 管壳层归属，perms 管功能开关；
 *               缺省 perms 表示「有身份即可见」
 * - hidden      不在菜单中展示（如答题页只能从考试列表进入）
 * - activeMenu  隐藏页签归属的菜单项（答题页高亮「我的考试」）
 * - bare        沉浸模式：Portal 壳不渲染导航（答题页专用，考试环境不应有分心入口）
 */
const routes = [
  { path: '/login', name: 'Login', component: Login },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: Home,
        meta: { title: '教学工作台', icon: 'Odometer' }
      },
      {path:'resources/imports',name:'DocumentImports',component:()=>import('@/views/workflow/Imports.vue'),meta:{title:'资料导入',icon:'Upload',group:'资料与题库',roles:['admin','teacher'],perms:['crawler:manage']}},
      {path:'resources/quality',name:'DocumentQuality',component:()=>import('@/views/workflow/Quality.vue'),meta:{title:'资料质量评测',icon:'DataAnalysis',group:'资料与题库',roles:['admin','teacher'],perms:['crawler:manage']}},
      {path:'resources/review',name:'CandidateReview',component:()=>import('@/views/workflow/Review.vue'),meta:{title:'题目复核',icon:'EditPen',group:'资料与题库',roles:['admin','teacher'],perms:['crawler:manage']}},
      {path:'teacher/monitor',name:'ExamOperations',component:()=>import('@/views/workflow/Monitor.vue'),meta:{title:'考试监控与发布',icon:'Monitor',group:'教学管理',roles:['admin','teacher'],perms:['exam:manage']}},
      // ===== 智能助手（全角色可用，数据范围由后端工具层按角色隔离）=====
      {
        path: 'ai/chat',
        name: 'AiChat',
        component: () => import('@/views/ai/chat/index.vue'),
        // keepAlive：这个页面承载对话上下文，切走再回来不该丢会话
        meta: { title: '智能助手', icon: 'ChatDotRound', keepAlive: true, perms: ['ai:chat'] }
      },
      // ===== 教学管理（有对应权限点即可见：teacher / subject_lead / edu_admin…）=====
      {
        path: 'teacher/question',
        name: 'QuestionManage',
        component: () => import('@/views/teacher/question/index.vue'),
        meta: { title: '题库管理', icon: 'Notebook', group: '教学管理', roles: ['admin', 'teacher'], perms: ['question:view'] }
      },
      {
        path: 'teacher/ai-question',
        name: 'AiQuestion',
        component: () => import('@/views/teacher/ai-question/index.vue'),
        meta: { title: '智能出题', icon: 'MagicStick', group: '教学管理', roles: ['admin', 'teacher'], perms: ['ai:gen:question'] }
      },
      {
        path: 'teacher/paper',
        name: 'PaperManage',
        component: () => import('@/views/teacher/paper/index.vue'),
        meta: { title: '组卷管理', icon: 'Document', group: '教学管理', roles: ['admin', 'teacher'], perms: ['paper:manage'] }
      },
      {
        path: 'teacher/exam',
        name: 'ExamManage',
        component: () => import('@/views/teacher/exam/index.vue'),
        meta: { title: '考试管理', icon: 'Calendar', group: '教学管理', roles: ['admin', 'teacher'], perms: ['exam:manage'] }
      },
      {
        path: 'teacher/marking',
        name: 'Marking',
        component: () => import('@/views/teacher/marking/index.vue'),
        meta: { title: '主观题阅卷', icon: 'EditPen', group: '教学管理', roles: ['admin', 'teacher'], perms: ['marking:do'] }
      },
      {
        path: 'teacher/grade',
        name: 'ExamGrade',
        component: () => import('@/views/teacher/grade/index.vue'),
        meta: { title: '成绩统计', icon: 'Histogram', group: '教学管理', roles: ['admin', 'teacher'], perms: ['grade:view'] }
      },
      {
        path: 'teacher/analysis',
        name: 'ExamAnalysis',
        component: () => import('@/views/teacher/analysis/index.vue'),
        meta: { title: '学情分析', icon: 'TrendCharts', group: '教学管理', roles: ['admin', 'teacher'], perms: ['analysis:view'] }
      },
      // ===== 我的考试（学生）=====
      {
        path: 'student/exam',
        name: 'StudentExam',
        component: () => import('@/views/student/exam/index.vue'),
        meta: { title: '考试安排', icon: 'Tickets', group: '我的考试', roles: ['student'] }
      },
      {
        path: 'student/grade',
        name: 'StudentGrade',
        component: () => import('@/views/student/grade/index.vue'),
        meta: { title: '学习记录', icon: 'Medal', group: '我的考试', roles: ['student'] }
      },
      {
        path: 'student/wrong',
        name: 'WrongBook',
        component: () => import('@/views/student/wrong/index.vue'),
        meta: { title: '练习与错题', icon: 'Memo', group: '我的考试', roles: ['student'] }
      },
      {
        path: 'student/answering',
        name: 'Answering',
        component: () => import('@/views/student/answering/index.vue'),
        // bare：进入考试即脱离导航。倒计时与答题卡就是全部界面元素
        meta: { title: '在线答题', roles: ['student'], hidden: true, activeMenu: '/student/exam', bare: true }
      },
      // ===== 系统管理（按权限点放行，不再按「是不是管理员」一刀切）=====
      {
        path: 'admin/user',
        name: 'UserManage',
        component: () => import('@/views/admin/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', group: '系统管理', roles: ['admin'], perms: ['system:user:manage'] }
      },
      {
        path: 'admin/class',
        name: 'ClassManage',
        component: () => import('@/views/admin/clazz/index.vue'),
        meta: { title: '班级管理', icon: 'School', group: '系统管理', roles: ['admin'], perms: ['system:class:manage'] }
      },
      {
        path: 'admin/subject',
        name: 'SubjectManage',
        component: () => import('@/views/admin/subject/index.vue'),
        meta: { title: '科目管理', icon: 'Collection', group: '系统管理', roles: ['admin'], perms: ['system:subject:manage'] }
      },
      {
        path: 'admin/role',
        name: 'RoleManage',
        component: () => import('@/views/admin/role/index.vue'),
        meta: { title: '角色管理', icon: 'Key', group: '系统管理', roles: ['admin'], perms: ['system:role:view'] }
      },
      {
        path: 'admin/dict',
        name: 'DictManage',
        component: () => import('@/views/admin/dict/index.vue'),
        meta: { title: '数据字典', icon: 'Postcard', group: '系统管理', roles: ['admin'], perms: ['system:dict:manage'] }
      },
      {
        path: 'admin/log',
        name: 'OperLogManage',
        component: () => import('@/views/admin/log/index.vue'),
        meta: { title: '操作日志', icon: 'Files', group: '系统管理', roles: ['admin'], perms: ['system:log:view'] }
      },
      {
        path: 'admin/crawler',
        name: 'CrawlerManage',
        component: () => import('@/views/admin/crawler/index.vue'),
        meta: { title: '题库采集', icon: 'Download', group: '系统管理', roles: ['admin'], perms: ['crawler:manage'] }
      },
      // ===== 个人中心 =====
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人设置', icon: 'Setting', group: '个人中心' }
      },
      {
        path: 'file/my',
        name: 'MyFiles',
        component: () => import('@/views/file/index.vue'),
        meta: { title: '我的文件', icon: 'Folder', group: '个人中心' }
      },
      // 通知中心：不进侧边栏菜单，从右上角铃铛进。
      // 它有独立路由但没有菜单入口——因为通知是「被某件事触发」才去看的，
      // 摆在菜单里只会多一个永远没人点的入口。
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('@/views/notification/index.vue'),
        meta: { title: '通知中心', icon: 'Bell', hidden: true }
      },
      // ===== 其他 =====
      // 动效演示本质上是一份「样式规范的自查工具」，只对维护界面的人有意义，
      // 因此只开放给管理员；学生/教师看到一堆会动的方块只会困惑
      {
        path: 'demo/animation',
        name: 'AnimationDemo',
        component: () => import('@/views/demo/animation/index.vue'),
        meta: { title: '动效演示', icon: 'Brush', group: '其他', roles: ['admin'], keepAlive: true }
      }
    ]
  },
  // 403 / 404：三期之前越权与错路径都静默弹回首页，用户不知道发生了什么。
  // 专属页面 + 明确文案是「系统值得信任」的最低成本表达。
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无访问权限' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  },
  // 兜底：未匹配的路径进 404 页（而不是白屏或无痕弹回）
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 路由切换回到顶部：切页后停留在大表格底部是很差的体验
  scrollBehavior: () => ({ top: 0 })
})

// 全局前置守卫：登录态校验 + 刷新后补用户信息 + 身份/权限两级拦截
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (!userStore.token) {
    return to.path === '/login' ? true : '/login'
  }
  if (to.path === '/login') {
    return '/'
  }
  // 刷新页面后 store 有 token 但 userInfo 缺失时补齐（同时校验 token 是否仍有效）
  if (!userStore.userInfo) {
    try {
      await userStore.fetchInfo()
    } catch (e) {
      await userStore.logout()
      return '/login'
    }
  }

  if (userStore.userInfo?.passwordChangeRequired && to.path !== "/profile") return "/profile"

  const verdict = canAccess(to, userStore.role, userStore.permissions)
  if (!verdict.ok) {
    // 身份不符（如学生摸管理路由）：弹回自己的首页即可，这属于「不存在这个入口」；
    // 身份符合但权限不足（如教师访问用户管理）：进 403 页说明白，避免「点了没反应」的猜测
    if (verdict.reason === 'perm') {
      return { path: '/403', query: { perms: (to.meta?.perms || []).join(',') } }
    }
    return '/home'
  }
  return true
})

export default router
