<script setup>
import { ref, onMounted } from 'vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { ElMessage } from 'element-plus'

const knowledgeStore = useKnowledgeStore()

const dialogVisible = ref(false)
const uploadForm = ref({
  title: '',
  tags: '',
  file: null
})

onMounted(() => {
  knowledgeStore.loadList()
})

function openUploadDialog() {
  dialogVisible.value = true
}

function handleFileChange(file) {
  uploadForm.value.file = file.raw
  if (!uploadForm.value.title) {
    uploadForm.value.title = file.name.replace(/\.[^.]+$/, '')
  }
}

async function handleUpload() {
  if (!uploadForm.value.file || !uploadForm.value.title) {
    ElMessage.warning('请选择文件并输入标题')
    return
  }

  try {
    await knowledgeStore.upload(
      uploadForm.value.file,
      uploadForm.value.title,
      uploadForm.value.tags
    )
    ElMessage.success('上传成功')
    dialogVisible.value = false
    uploadForm.value = { title: '', tags: '', file: null }
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

async function handleDelete(id) {
  try {
    await knowledgeStore.remove(id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function getStatusType(status) {
  switch (status) {
    case 'READY': return 'success'
    case 'PROCESSING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}
</script>

<template>
  <div class="knowledge-view">
    <div class="page-header">
      <h1 class="page-title">知识库</h1>
      <el-button type="primary" @click="openUploadDialog">
        上传文档
      </el-button>
    </div>

    <div v-loading="knowledgeStore.isLoading" class="knowledge-list">
      <div v-if="knowledgeStore.list.length === 0" class="empty-state">
        <div class="empty-icon">·</div>
        <h3>暂无文档</h3>
        <p>上传文档以构建知识库</p>
        <el-button type="primary" @click="openUploadDialog">上传</el-button>
      </div>

      <div v-else class="cards-grid">
        <el-card
          v-for="(item, index) in knowledgeStore.list"
          :key="item.id"
          class="knowledge-card"
          :style="{ animationDelay: `${index * 50}ms` }"
          shadow="hover"
        >
          <div class="card-header">
            <h3 class="card-title">{{ item.title }}</h3>
            <el-tag :type="getStatusType(item.status)" size="small">
              {{ item.status }}
            </el-tag>
          </div>

          <div class="card-meta">
            <span>{{ item.fileName || '未知文件' }}</span>
          </div>

          <div class="card-actions">
            <el-button size="small" text>查看</el-button>
            <el-button
              size="small"
              text
              type="danger"
              @click="handleDelete(item.id)"
            >
              删除
            </el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="上传文档"
      width="420px"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="输入文档标题" />
        </el-form-item>

        <el-form-item label="标签">
          <el-input v-model="uploadForm.tags" placeholder="用逗号分隔" />
        </el-form-item>

        <el-form-item label="文件">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            accept=".pdf,.doc,.docx,.txt,.md"
          >
            <div class="upload-placeholder">
              <span>拖拽或点击上传</span>
            </div>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-view {
  max-width: var(--max-content-width);
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-8);
  animation: fadeIn var(--transition-normal) ease;
}

.page-title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  animation: fadeIn var(--transition-normal) ease;

  .empty-icon {
    font-size: 3rem;
    font-weight: 200;
    color: var(--text-muted);
    margin-bottom: var(--space-4);
  }

  h3 {
    color: var(--text-primary);
    margin-bottom: var(--space-2);
  }

  p {
    color: var(--text-secondary);
    margin-bottom: var(--space-6);
  }
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

.knowledge-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  opacity: 0;
  animation: fadeIn var(--transition-normal) ease forwards;

  &:hover {
    border-color: var(--border-default);
    box-shadow: var(--shadow-md);
  }

  :deep(.el-card__body) {
    padding: var(--space-5);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-3);
}

.card-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  margin-right: var(--space-3);
}

.card-meta {
  font-size: 0.8125rem;
  color: var(--text-muted);
  margin-bottom: var(--space-4);
}

.card-actions {
  display: flex;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
}

.upload-placeholder {
  padding: var(--space-8);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-md);
  text-align: center;
  color: var(--text-muted);
  font-size: 0.875rem;
  transition: all var(--transition-fast);

  &:hover {
    border-color: var(--accent-primary);
    color: var(--text-secondary);
  }
}
</style>
