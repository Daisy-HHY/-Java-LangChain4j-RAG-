import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getKnowledgeList, deleteKnowledge, getKnowledgeStatus, uploadDocument } from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  // State
  const list = ref([])
  const isLoading = ref(false)
  const uploadProgress = ref({})
  const error = ref(null)

  // Actions
  async function loadList(page = 1, size = 20) {
    isLoading.value = true
    error.value = null

    try {
      list.value = await getKnowledgeList(page, size)
    } catch (e) {
      error.value = '加载知识库列表失败'
      console.error(e)
    } finally {
      isLoading.value = false
    }
  }

  async function upload(file, title, tags = '') {
    error.value = null

    try {
      const result = await uploadDocument(file, title, tags)

      // 添加到列表
      list.value.unshift({
        id: result.knowledgeId,
        title,
        fileName: file.name,
        tags,
        status: result.status || 'PENDING'
      })

      // 开始轮询状态
      pollStatus(result.knowledgeId)

      return result
    } catch (e) {
      error.value = '上传失败'
      console.error(e)
      throw e
    }
  }

  async function pollStatus(knowledgeId, interval = 2000) {
    const poll = async () => {
      try {
        const result = await getKnowledgeStatus(knowledgeId)
        const item = list.value.find(i => i.id === knowledgeId)

        if (item) {
          item.status = result.status
        }

        // 如果完成或失败，停止轮询
        if (result.status === 'READY' || result.status === 'FAILED') {
          clearInterval(timers[knowledgeId])
          delete timers[knowledgeId]
        }
      } catch (e) {
        console.error('轮询状态失败:', e)
      }
    }

    // 立即执行一次
    await poll()

    // 设置定时器
    const timer = setInterval(poll, interval)
    timers[knowledgeId] = timer
  }

  async function remove(id) {
    try {
      await deleteKnowledge(id)
      list.value = list.value.filter(item => item.id !== id)
    } catch (e) {
      error.value = '删除失败'
      console.error(e)
      throw e
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    // State
    list,
    isLoading,
    uploadProgress,
    error,
    // Actions
    loadList,
    upload,
    remove,
    clearError
  }
})

// 存储轮询定时器
const timers = {}
