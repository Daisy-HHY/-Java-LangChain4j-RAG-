import apiClient from './index'

/**
 * 上传知识库文档
 * @param {File} file - 文件对象
 * @param {string} title - 标题
 * @param {string} tags - 标签（可选）
 */
export function uploadDocument(file, title, tags = '') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title)
  formData.append('tags', tags)

  return apiClient.post('/knowledge/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取知识库列表
 * @param {number} page - 页码
 * @param {number} size - 每页数量
 */
export function getKnowledgeList(page = 1, size = 10) {
  return apiClient.get('/knowledge/list', {
    params: { page, size }
  })
}

/**
 * 删除知识库文档
 * @param {number} id - 知识库ID
 */
export function deleteKnowledge(id) {
  return apiClient.delete(`/knowledge/${id}`)
}

/**
 * 获取知识库状态
 * @param {number} id - 知识库ID
 */
export function getKnowledgeStatus(id) {
  return apiClient.get(`/knowledge/${id}/status`)
}
