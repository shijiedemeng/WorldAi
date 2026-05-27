<template>
  <div class="admin-page defect-record-detail-page">
    <PageHeader
      title="缺陷详情"
    >
      <el-button @click="goBackToList">返回列表</el-button>
      <el-button type="primary" :disabled="loadingDetail || !selectedRemoteDefect" @click="goToAnalysis">AI 处理</el-button>
      <el-button :loading="loadingDetail" @click="loadDetail">刷新详情</el-button>
    </PageHeader>

    <template v-if="selectedSource && selectedRemoteDefect">
      <el-card class="detail-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>缺陷基础信息</span>
            <el-space wrap>
              <el-tag>{{ platformLabelMap[selectedSource.platformType] || selectedSource.platformType }}</el-tag>
              <el-tag type="info">{{ selectedSource.sourceName }}</el-tag>
              <el-tag v-if="selectedRemoteDefect.persistedRecordId" type="success">已保存基础信息</el-tag>
            </el-space>
          </div>
        </template>

        <div class="detail-summary">
          <div class="detail-summary__meta">
            <el-tag type="success">{{ selectedRemoteDefect.externalDefectKey || selectedRemoteDefect.externalDefectId }}</el-tag>
            <el-tag type="info">{{ selectedRemoteDefect.projectCode }}</el-tag>
            <el-tag v-if="selectedRemoteDefect.hasImageFlag" type="warning">含图片</el-tag>
          </div>
          <h2>{{ selectedRemoteDefect.title }}</h2>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="状态">{{ selectedRemoteDefect.defectStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="严重级别">{{ selectedRemoteDefect.severity || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ selectedRemoteDefect.defectType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ selectedRemoteDefect.assignedTo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ selectedRemoteDefect.reporterName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ selectedRemoteDefect.openedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ selectedRemoteDefect.updatedAtRemote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="外部编号">{{ selectedRemoteDefect.externalDefectId }}</el-descriptions-item>
            <el-descriptions-item label="外部键">{{ selectedRemoteDefect.externalDefectKey || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>

      <el-card class="detail-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>缺陷内容</span>
          </div>
        </template>

        <section class="detail-section">
          <div class="detail-section__title">描述</div>
          <DefectRichText :content="selectedRemoteDefect.descriptionText || selectedRemoteDefect.summary" />
        </section>

        <section v-if="selectedRemoteDefect.attachments.length > 0" class="detail-section">
          <div class="detail-section__title">附件</div>
          <div
            v-for="attachment in selectedRemoteDefect.attachments"
            :key="attachment.externalAttachmentId || attachment.fileId || attachment.url"
            class="attachment-item"
          >
            <div class="attachment-item__header">
              <div class="attachment-item__main">
                <a v-if="attachment.url" :href="attachment.url" target="_blank" rel="noreferrer">
                  {{ attachment.fileName || attachment.fileId || '未命名附件' }}
                </a>
                <span v-else>{{ attachment.fileName || attachment.fileId || '未命名附件' }}</span>
              </div>
              <small>{{ formatAttachmentMeta(attachment) }}</small>
            </div>
            <DefectRichText
              v-if="isImageAttachment(attachment) && attachment.url"
              class="attachment-item__preview"
              :content="`图片：${attachment.url}`"
            />
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__title">评论</div>
          <div v-if="selectedRemoteDefect.comments.length > 0" class="comment-list">
            <div v-for="comment in selectedRemoteDefect.comments" :key="`${comment.externalCommentId}-${comment.commentedAt}`" class="comment-item">
              <strong>{{ comment.authorName || '未知用户' }}</strong>
              <span>{{ comment.commentedAt || '-' }}</span>
              <DefectRichText class="comment-item__content" :content="comment.commentContent" />
            </div>
          </div>
          <EmptyBlock v-else description="暂无评论" />
        </section>
      </el-card>
    </template>

    <el-card v-else class="detail-card" shadow="never">
      <EmptyBlock :description="loadingDetail ? '正在加载缺陷详情' : '未找到当前缺陷，请返回列表重新选择'" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import DefectRichText from '@/components/defect/DefectRichText.vue'
import { fetchDefectSources, viewSourceDefectDetail } from '@/api/defect'
import type { DefectAttachment, DefectSourceConfig, RemoteDefect } from '@/types/defect'
import { defectPlatformOptions } from '@/types/options'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const appStore = useAppStore()
const props = defineProps<{
  sourceCode: string
  externalDefectId: string
}>()

const platformLabelMap = Object.fromEntries(defectPlatformOptions.map((item) => [item.value, item.label]))

const defectSources = ref<DefectSourceConfig[]>([])
const selectedSource = computed(() => defectSources.value.find((item) => item.sourceCode === currentSourceCode.value) || null)
const selectedRemoteDefect = ref<RemoteDefect | null>(null)
const loadingDetail = ref(false)
const currentSourceCode = ref(props.sourceCode || '')
const currentExternalDefectId = ref(props.externalDefectId || '')

onMounted(async () => {
  await loadSources()
  await loadDetail()
})

watch(
  () => [props.sourceCode, props.externalDefectId],
  async () => {
    currentSourceCode.value = props.sourceCode || ''
    currentExternalDefectId.value = props.externalDefectId || ''
    await loadDetail()
  },
)

async function loadSources() {
  defectSources.value = await fetchDefectSources()
}

async function loadDetail() {
  if (!currentSourceCode.value || !currentExternalDefectId.value) {
    selectedRemoteDefect.value = null
    return
  }
  loadingDetail.value = true
  try {
    selectedRemoteDefect.value = await viewSourceDefectDetail(currentSourceCode.value, currentExternalDefectId.value)
  } catch (error) {
    selectedRemoteDefect.value = null
    ElMessage.error(error instanceof Error ? error.message : '缺陷详情加载失败')
  } finally {
    loadingDetail.value = false
  }
}

function goBackToList() {
  appStore.closeActiveTabAndOpen({
    name: 'defect-records',
    query: {
      sourceCode: currentSourceCode.value,
    },
  }, router)
}

function goToAnalysis() {
  if (!currentSourceCode.value || !currentExternalDefectId.value) {
    return
  }
  router.push({
    name: 'defect-record-analysis',
    params: {
      sourceCode: currentSourceCode.value,
      externalDefectId: currentExternalDefectId.value,
    },
  })
}

function formatAttachmentMeta(attachment: DefectAttachment) {
  const parts = [attachment.suffix, attachment.size ? `${attachment.size} B` : '', attachment.creatorName, attachment.createdAt].filter(Boolean)
  return parts.join(' / ') || '-'
}

function isImageAttachment(attachment: DefectAttachment) {
  return [attachment.suffix, attachment.fileName, attachment.url].some((value) => isImageValue(value))
}

function isImageValue(value?: string) {
  if (!value) {
    return false
  }
  const normalized = value.toLowerCase()
  const path = normalized.split(/[?#]/)[0]
  return /(^|\.)(png|jpe?g|gif|webp|bmp|svg|avif)$/.test(path) || normalized.includes('/image/')
}
</script>

<style scoped>
.defect-record-detail-page {
  display: grid;
  gap: 18px;
}

.detail-card {
  margin-bottom: 0;
}

.detail-summary {
  display: grid;
  gap: 14px;
}

.detail-summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-section {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.detail-section__title {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.attachment-item {
  display: grid;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  color: #0f172a;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.36);
}

.attachment-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.attachment-item__main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.attachment-item__main a,
.attachment-item__main span {
  color: #0f172a;
  text-decoration: none;
  word-break: break-all;
}

.attachment-item__main a:hover {
  color: var(--el-color-primary);
}

.attachment-item small {
  color: var(--el-text-color-secondary);
  flex: 0 0 auto;
}

.attachment-item__preview {
  padding-top: 2px;
}

.comment-list {
  display: grid;
  gap: 12px;
}

.comment-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7f8fb;
}

.comment-item > strong,
.comment-item > span {
  display: block;
}

.comment-item > span {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.comment-item__content {
  margin: 10px 0 0;
}

@media (max-width: 960px) {
  .attachment-item {
    align-items: flex-start;
  }

  .attachment-item__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
