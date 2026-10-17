import request from '@/utils/request'

// ==================== 用户管理 ====================

/** 分页查询用户：{ pageNum, pageSize, keyword, userType, classId, status } */
export function pageUsers(params) {
  return request.get('/user/page', { params })
}

/** 新增或修改用户（id 为空即新增） */
export function saveUser(data) {
  return request.post('/user', data)
}

/** 启用 / 禁用用户 */
export function updateUserStatus(id, status) {
  return request.put(`/user/${id}/status`, null, { params: { status } })
}

/** 重置密码，返回重置后的明文密码 */
export function resetUserPassword(id) {
  return request.put(`/user/${id}/password/reset`)
}

/** 删除用户 */
export function deleteUser(id) {
  return request.delete(`/user/${id}`)
}

/** 批量导入用户（formData 内含 file 字段） */
export function importUsers(formData) {
  return request.post('/user/import', formData)
}

/** 下载用户导入模板（返回文件流） */
export function downloadUserTemplate() {
  return request.get('/user/import/template', { responseType: 'blob' })
}

// ==================== 班级管理 ====================

/** 查询全部班级 */
export function listClasses() {
  return request.get('/class/list')
}

/** 新增或修改班级 */
export function saveClass(data) {
  return request.post('/class', data)
}

/** 删除班级 */
export function deleteClass(id) {
  return request.delete(`/class/${id}`)
}

// ==================== 科目管理 ====================

/** 查询全部科目 */
export function listSubjects() {
  return request.get('/subject/list')
}

/** 新增或修改科目 */
export function saveSubject(data) {
  return request.post('/subject', data)
}

/** 删除科目 */
export function deleteSubject(id) {
  return request.delete(`/subject/${id}`)
}

// ==================== 角色 ====================

/** 查询全部角色（系统内置字典） */
export function listRoles() {
  return request.get('/role/list')
}

/**
 * 四期 RBAC：角色列表，返回 [{ id, roleCode, roleName, persona }]。
 * 与上面 listRoles（旧 user_type 字典）并存，用户表单 / 角色页改用它作数据源，
 * persona 用于映射回 userType（1 管理 / 2 教师 / 3 学生）保持后端旧链路兼容。
 */
export function listRbacRoles() {
  return request.get('/role/list')
}

/**
 * 角色的权限明细。后端 RBAC 权限配置未上线时会 404，
 * 调用方（角色管理页详情弹窗）负责降级展示，这里不做兜底。
 */
export function getRolePermissions(id) {
  return request.get(`/role/${id}/permissions`)
}
