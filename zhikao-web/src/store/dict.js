import { defineStore } from 'pinia'
import { dictBatch } from '@/api/dict'

/**
 * 字典缓存：一次批量拉取 + 会话内复用。
 *
 * 为什么用 Pinia 而不是普通模块变量：字典是「全局共享 + 只读」的数据，
 * 用 store 可以在组件里直接响应式读取（`dictStore.get('question_type')`），
 * 落库时机与生命周期也更清晰——切换账号时可以直接 reset。
 *
 * 缓存策略：命中即返回；未命中则把「该类型」加入本次批量请求，
 * 并发调用会被合并（inFlight 去重），避免一个页面 5 个组件各拉一次字典。
 */
export const useDictStore = defineStore('dict', {
  state: () => ({
    // { [dictType]: [{ label, value, color, sort, status }] }
    items: {},
    // 已完成的类型，避免重复请求
    loaded: {},
    // 正在进行中的批量请求：{ [dictType]: Promise }
    inFlight: {}
  }),
  getters: {
    /** 取某字典的全部选项 */
    get: (state) => (type) => state.items[type] || [],
    /** 按取值取展示文案，找不到时回退为原值（保证页面不出现空白） */
    labelOf: (state) => (type, value) => {
      if (value === null || value === undefined || value === '') return '-'
      const hit = (state.items[type] || []).find((item) => String(item.value) === String(value))
      return hit ? hit.label : String(value)
    },
    /** 按取值取语义色，供标签上色 */
    colorOf: (state) => (type, value) => {
      const hit = (state.items[type] || []).find((item) => String(item.value) === String(value))
      return hit?.color || 'info'
    }
  },
  actions: {
    /**
     * 按需加载：传入若干字典类型，未加载的会合并成一次请求。
     * @returns {Promise<Object>} 该批次的字典内容
     */
    async ensure(types) {
      const need = types.filter((type) => type && !this.loaded[type])
      if (need.length === 0) {
        return this.pick(types)
      }
      // 合并到已有请求上，避免并发重复拉取
      const pending = need.find((type) => this.inFlight[type])
      if (pending) {
        await this.inFlight[pending]
        return this.ensure(types)
      }
      const promise = dictBatch(need)
        .then((data) => {
          Object.entries(data || {}).forEach(([type, list]) => {
            this.items[type] = list || []
            this.loaded[type] = true
          })
        })
        .finally(() => {
          need.forEach((type) => delete this.inFlight[type])
        })
      need.forEach((type) => {
        this.inFlight[type] = promise
      })
      await promise
      return this.pick(types)
    },
    pick(types) {
      const result = {}
      types.forEach((type) => {
        result[type] = this.items[type] || []
      })
      return result
    },
    /** 强制刷新（字典管理页修改后调用） */
    async refresh(types) {
      types.forEach((type) => {
        delete this.loaded[type]
      })
      return this.ensure(types)
    },
    /** 退出登录时清空，避免切账号后读到上一个账号的数据 */
    reset() {
      this.items = {}
      this.loaded = {}
      this.inFlight = {}
    }
  }
})
