import request from '@/utils/request'

/**
 * 文件模块。
 *
 * 后端把「上传」与「业务落地」分开：
 * - 上传只负责存文件、算哈希、去重，返回一个 fileId 与访问地址；
 * - 业务侧（头像、题目导入、试卷导入）各自决定这个文件怎么用。
 *
 * 这样做的原因：头像和 Excel 是完全不同的东西，硬塞进一个「上传接口 + type 参数」
 * 会让白名单、大小限制、后续处理逻辑全挤在一个 if-else 里。
 */

/** 通用上传（category 决定扩展名白名单与磁盘目录） */
export function uploadFile(file, category, bizType) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData, { params: { category, bizType } })
}

/** 上传头像（图片白名单，限制 2MB）。后端一步到位：存文件 + 写入当前用户 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/avatar', formData)
}

/** 清除头像，回退到「姓名首字」占位 */
export function clearAvatar() {
  return request.delete('/file/avatar')
}

/** 我的上传记录 */
export function pageMyFiles(params) {
  return request.get('/file/page', { params })
}

/** 删除自己上传的文件 */
export function deleteFile(id) {
  return request.delete(`/file/${id}`)
}

/**
 * 组装文件访问地址。
 *
 * 注意这里是 `/api/file/view/{id}` 而不是裸路径：后端 context-path 是 /api，
 * 开发态又经 Vite 代理转发，因此必须带上前缀，
 * 否则图片在开发环境会 404（这也是一期头像功能一直没接上的原因之一）。
 */
export function fileUrl(id) {
  return id ? `/api/file/view/${id}` : ''
}
