/**
 * 菜单/路由可见性计算：双壳（Portal / Console）共用的单一判定源。
 *
 * 三期遗留问题：菜单只按 meta.roles（粗粒度身份）过滤，
 * 新角色（edu_admin / subject_lead）无法表达「同身份、不同能力」。
 * 现在两级都判：roles 管身份壳层，perms 管功能权限点，
 * perms 缺省视为「有身份即可见」，避免逐条补配置时漏一项就整页消失。
 */

/** 分组图标：key 必须与路由 meta.group 完全一致 */
const GROUP_ICONS = {
  教学管理: 'Notebook',
  我的考试: 'Tickets',
  个人中心: 'User',
  系统管理: 'Setting',
  其他: 'Grid'
}

/** 当前用户能看到的路由项（隐藏项、身份不符、权限不足都会在这里被过滤掉） */
export function visibleRoutes(router, persona, permissions = []) {
  const children = router.options.routes.find((item) => item.path === '/')?.children || []
  return children.filter((item) => {
    if (item.meta?.hidden) return false
    if (item.meta?.roles && !item.meta.roles.includes(persona)) return false
    if (item.meta?.perms && !item.meta.perms.some((p) => permissions.includes(p))) return false
    return true
  })
}

/** Console 侧边栏：把可见路由按 meta.group 折叠成子菜单 */
export function buildGroupedMenus(nodes) {
  const result = []
  nodes.forEach((item) => {
    const node = { title: item.meta?.title, icon: item.meta?.icon, index: `/${item.path}` }
    const group = item.meta?.group
    if (!group) {
      result.push(node)
      return
    }
    let parent = result.find((it) => it.title === group)
    if (!parent) {
      parent = { title: group, icon: GROUP_ICONS[group] || 'FolderOpened', children: [] }
      result.push(parent)
    }
    parent.children.push(node)
  })
  return result
}

/** Portal 顶部导航：按标题白名单取平铺链接，权限变化后入口自动消失 */
export function buildFlatNav(nodes, titles) {
  const byTitle = new Map(nodes.map((item) => [item.meta?.title, { title: item.meta?.title, index: `/${item.path}` }]))
  return titles.filter((t) => byTitle.has(t)).map((t) => byTitle.get(t))
}

/** 路由守卫用：当前用户是否有权访问该路由（与菜单过滤同一套规则，防止两处口径漂移） */
export function canAccess(route, persona, permissions = []) {
  const meta = route.meta || {}
  if (meta.roles && !meta.roles.includes(persona)) return { ok: false, reason: 'role' }
  if (meta.perms && !meta.perms.some((p) => permissions.includes(p))) return { ok: false, reason: 'perm' }
  return { ok: true }
}
