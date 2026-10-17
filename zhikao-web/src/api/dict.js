import request from '@/utils/request'

// ===== 数据字典 =====
// 读取对所有登录用户开放（学生答题页也要用题型、状态文案），管理接口仅管理员。
export const dictBatch = (types) =>
  request.get('/dict/batch', { params: { types: types.join(',') } })

export const dictItems = (type) => request.get(`/dict/${type}`)

export const dictTypePage = (params) => request.get('/dict/type/page', { params })

export const dictItemList = (dictType) => request.get('/dict/item/list', { params: { dictType } })

export const dictSaveType = (data) => request.post('/dict/type', data)

export const dictRemoveType = (id) => request.delete(`/dict/type/${id}`)

export const dictSaveItem = (data) => request.post('/dict/item', data)

export const dictRemoveItem = (id) => request.delete(`/dict/item/${id}`)
