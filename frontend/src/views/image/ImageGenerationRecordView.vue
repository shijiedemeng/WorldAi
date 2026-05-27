<template>
  <div class="admin-page image-record-page">
    <PageHeader title="生图记录">
      <el-button :loading="loading" @click="loadRecords()">刷新</el-button>
      <el-button type="primary" :disabled="!imageSettings.length" @click="openCreate('TEXT_TO_IMAGE')">文生图</el-button>
      <el-button type="success" :disabled="!imageSettings.length" @click="openCreate('IMAGE_EDIT')">图生图</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable class="w-full" placeholder="全部状态">
            <el-option v-for="item in imageGenerationStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.generationType" clearable class="w-full" placeholder="全部类型">
            <el-option v-for="item in imageGenerationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口配置">
          <el-select v-model="filters.aiSettingKey" clearable filterable class="w-full" placeholder="全部配置">
            <el-option v-for="item in imageSettings" :key="item.settingKey" :label="settingLabel(item)" :value="item.settingKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="模型 / 提示词 / 错误" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        :data="records"
        stripe
        v-loading="loading"
        row-key="id"
        empty-text="暂无生图记录"
        @row-dblclick="openDetail"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.generationType) }}</template>
        </el-table-column>
        <el-table-column prop="aiSettingKey" label="接口配置" width="160" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型" width="150" show-overflow-tooltip />
        <el-table-column label="配置" width="180">
          <template #default="{ row }">
            {{ row.imageSize || '-' }} / {{ row.quality || '-' }} / {{ row.outputFormat || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-image
              v-if="row.resultFileUrl"
              class="image-record-page__thumb"
              :src="row.resultFileUrl"
              :preview-src-list="[row.resultFileUrl]"
              fit="cover"
              preview-teleported
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="提示词" min-width="260">
          <template #default="{ row }">
            <span class="image-record-page__preview">{{ preview(row.finalPrompt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finishedAt" label="完成时间" width="180">
          <template #default="{ row }">{{ row.finishedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="success" :disabled="!row.resultFileUrl" @click="openImage(row)">图片</el-button>
            <el-button
              v-if="row.status === 'FAILED'"
              link
              type="warning"
              :loading="retryingRecordId === row.id"
              @click.stop="retryRecord(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="loadRecords"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="createVisible" :title="createTitle" size="680px" destroy-on-close>
      <el-form :model="form" label-width="140px">
        <el-form-item label="接口配置" required>
          <el-select v-model="form.aiSettingKey" filterable class="w-full" placeholder="选择生图接口配置">
            <el-option v-for="item in imageSettings" :key="item.settingKey" :label="settingLabel(item)" :value="item.settingKey" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createMode === 'IMAGE_EDIT'" label="原图" required>
          <el-upload
            action="#"
            accept="image/*"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleImageChange"
          >
            <el-button>{{ imageFile ? '重新选择图片' : '选择图片' }}</el-button>
          </el-upload>
          <span class="image-record-page__file-name">{{ imageFile?.name || '未选择图片' }}</span>
        </el-form-item>
        <el-form-item v-if="createMode === 'IMAGE_EDIT'" label="Mask">
          <el-upload
            action="#"
            accept="image/png"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleMaskChange"
          >
            <el-button>{{ maskFile ? '重新选择 Mask' : '选择 PNG Mask' }}</el-button>
          </el-upload>
          <span class="image-record-page__file-name">{{ maskFile?.name || '未选择 Mask' }}</span>
        </el-form-item>
        <el-form-item label="正向模板">
          <el-select v-model="form.promptTemplateCodes" multiple filterable clearable collapse-tags class="w-full" placeholder="可多选">
            <el-option v-for="item in positiveTemplates" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="负向模板">
          <el-select v-model="form.negativeTemplateCodes" multiple filterable clearable collapse-tags class="w-full" placeholder="可多选">
            <el-option v-for="item in negativeTemplates" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义正向">
          <el-input v-model="form.positivePromptText" type="textarea" :rows="5" placeholder="每行一条，可和模板组合" />
        </el-form-item>
        <el-form-item label="自定义负向">
          <el-input v-model="form.negativePromptText" type="textarea" :rows="4" placeholder="每行一条，例如不要水印、不要变形" />
        </el-form-item>
        <el-row :gutter="14">
          <el-col :md="12" :xs="24">
            <el-form-item label="尺寸">
              <el-select v-model="form.size" class="w-full">
                <el-option v-for="item in imageSizeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="质量">
              <el-select v-model="form.quality" class="w-full">
                <el-option v-for="item in imageQualityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="输出格式">
              <el-select v-model="form.outputFormat" class="w-full">
                <el-option v-for="item in imageOutputFormatOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="背景">
              <el-select v-model="form.background" class="w-full">
                <el-option v-for="item in imageBackgroundOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="审核">
              <el-select v-model="form.moderation" class="w-full">
                <el-option v-for="item in imageModerationOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="结果格式">
              <el-select v-model="form.responseFormat" class="w-full">
                <el-option v-for="item in imageResponseFormatOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="createMode === 'IMAGE_EDIT'" :md="12" :xs="24">
            <el-form-item label="保真度">
              <el-select v-model="form.inputFidelity" class="w-full">
                <el-option v-for="item in imageInputFidelityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="用户标识">
              <el-input v-model="form.user" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交任务</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="生图记录详情" size="72%" destroy-on-close>
      <template v-if="selectedRecord">
        <div v-if="selectedRecord.status === 'FAILED'" class="image-record-page__detail-actions">
          <el-button
            type="warning"
            :loading="retryingRecordId === selectedRecord.id"
            @click="retryRecord(selectedRecord)"
          >
            重试失败任务
          </el-button>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="ID">{{ selectedRecord.id }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(selectedRecord.generationType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(selectedRecord.status)" size="small">{{ statusLabel(selectedRecord.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="接口配置">{{ selectedRecord.aiSettingKey }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ selectedRecord.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="服务商">{{ selectedRecord.providerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尺寸">{{ selectedRecord.imageSize || '-' }}</el-descriptions-item>
          <el-descriptions-item label="质量">{{ selectedRecord.quality || '-' }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ selectedRecord.outputFormat || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedRecord.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ selectedRecord.startedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ selectedRecord.finishedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="image-record-page__detail-grid">
          <section>
            <h3>结果图片</h3>
            <el-image
              v-if="selectedRecord.resultFileUrl"
              class="image-record-page__result"
              :src="selectedRecord.resultFileUrl"
              :preview-src-list="[selectedRecord.resultFileUrl]"
              fit="contain"
              preview-teleported
            />
            <EmptyBlock v-else description="暂无结果图片" />
          </section>
          <section v-if="selectedRecord.sourceImageUrl">
            <h3>原图</h3>
            <el-image
              class="image-record-page__result"
              :src="selectedRecord.sourceImageUrl"
              :preview-src-list="[selectedRecord.sourceImageUrl]"
              fit="contain"
              preview-teleported
            />
          </section>
          <section v-if="selectedRecord.maskImageUrl">
            <h3>Mask</h3>
            <el-image
              class="image-record-page__result"
              :src="selectedRecord.maskImageUrl"
              :preview-src-list="[selectedRecord.maskImageUrl]"
              fit="contain"
              preview-teleported
            />
          </section>
        </div>

        <section class="image-record-page__section">
          <h3>最终提示词</h3>
          <pre>{{ selectedRecord.finalPrompt }}</pre>
        </section>
        <section class="image-record-page__section">
          <h3>请求内容</h3>
          <pre>{{ formatJson(selectedRecord.requestPayload) }}</pre>
        </section>
        <section class="image-record-page__section">
          <h3>响应内容</h3>
          <pre>{{ formatResponseJson(selectedRecord.responsePayload) }}</pre>
        </section>
        <section v-if="selectedRecord.errorMessage" class="image-record-page__section">
          <h3>错误信息</h3>
          <pre>{{ selectedRecord.errorMessage }}</pre>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import { fetchAiSettings } from '@/api/system'
import {
  createImageEdit,
  createImageGeneration,
  fetchImageGenerationRecord,
  fetchImageGenerationRecords,
  fetchImagePromptTemplates,
  retryImageGeneration,
} from '@/api/image'
import {
  imageBackgroundOptions,
  imageGenerationStatusLabelMap,
  imageGenerationStatusOptions,
  imageGenerationTypeLabelMap,
  imageGenerationTypeOptions,
  imageInputFidelityOptions,
  imageModerationOptions,
  imageOutputFormatOptions,
  imageQualityOptions,
  imageResponseFormatOptions,
  imageSizeOptions,
} from '@/types/options'
import type { AiModelSetting } from '@/types/system'
import type {
  CreateImageGenerationPayload,
  ImageGenerationRecord,
  ImageGenerationRecordQuery,
  ImageGenerationStatus,
  ImageGenerationType,
  ImagePromptTemplate,
} from '@/types/image'

interface GenerationForm {
  aiSettingKey: string
  promptTemplateCodes: string[]
  negativeTemplateCodes: string[]
  positivePromptText: string
  negativePromptText: string
  size: string
  quality: string
  outputFormat: string
  background: string
  moderation: string
  responseFormat: string
  inputFidelity: string
  user: string
}

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const createMode = ref<ImageGenerationType>('TEXT_TO_IMAGE')
const records = ref<ImageGenerationRecord[]>([])
const imageSettings = ref<AiModelSetting[]>([])
const templates = ref<ImagePromptTemplate[]>([])
const selectedRecord = ref<ImageGenerationRecord | null>(null)
const imageFile = ref<File | null>(null)
const maskFile = ref<File | null>(null)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const retryingRecordId = ref<number | null>(null)
const filters = ref<ImageGenerationRecordQuery>(emptyFilters())
const form = ref<GenerationForm>(emptyForm())

const createTitle = computed(() => (createMode.value === 'IMAGE_EDIT' ? '新建图生图任务' : '新建文生图任务'))
const positiveTemplates = computed(() => templates.value.filter((item) => item.templateType === 'POSITIVE'))
const negativeTemplates = computed(() => templates.value.filter((item) => item.templateType === 'NEGATIVE'))

onMounted(async () => {
  await Promise.all([loadSettings(), loadTemplates(), loadRecords()])
})

async function loadSettings() {
  imageSettings.value = await fetchAiSettings({ supportImageFlag: true, enabledFlag: true })
}

async function loadTemplates() {
  templates.value = await fetchImagePromptTemplates({ enabledFlag: true })
}

async function loadRecords() {
  loading.value = true
  try {
    const result = await fetchImageGenerationRecords({
      ...normalizeFilters(filters.value),
      page: page.value,
      pageSize: pageSize.value,
    })
    records.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  page.value = 1
  await loadRecords()
}

async function resetFilters() {
  filters.value = emptyFilters()
  page.value = 1
  await loadRecords()
}

async function handlePageSizeChange(nextPageSize: number) {
  pageSize.value = nextPageSize
  page.value = 1
  await loadRecords()
}

function openCreate(type: ImageGenerationType) {
  if (!imageSettings.value.length) {
    ElMessage.warning('请先配置启用的生图接口')
    return
  }
  createMode.value = type
  imageFile.value = null
  maskFile.value = null
  form.value = emptyForm()
  form.value.aiSettingKey = imageSettings.value[0]?.settingKey || ''
  createVisible.value = true
}

function handleImageChange(uploadFile: UploadFile) {
  imageFile.value = uploadFile.raw || null
}

function handleMaskChange(uploadFile: UploadFile) {
  maskFile.value = uploadFile.raw || null
}

async function handleSubmit() {
  const payload = normalizeSubmitPayload()
  if (!payload) {
    return
  }
  submitting.value = true
  try {
    if (createMode.value === 'IMAGE_EDIT') {
      if (!imageFile.value) {
        ElMessage.warning('请选择原图')
        return
      }
      await createImageEdit({
        ...payload,
        inputFidelity: form.value.inputFidelity,
        image: imageFile.value,
        mask: maskFile.value || undefined,
      })
    } else {
      await createImageGeneration(payload)
    }
    ElMessage.success('任务已提交')
    createVisible.value = false
    await loadRecords()
  } finally {
    submitting.value = false
  }
}

async function openDetail(row: ImageGenerationRecord) {
  selectedRecord.value = row
  detailVisible.value = true
  try {
    selectedRecord.value = await fetchImageGenerationRecord(row.id)
  } catch {
    // 列表数据仍然可以查看。
  }
}

function openImage(row: ImageGenerationRecord) {
  if (row.resultFileUrl) {
    window.open(row.resultFileUrl, '_blank')
  }
}

async function retryRecord(row: ImageGenerationRecord) {
  if (row.status !== 'FAILED') {
    return
  }
  retryingRecordId.value = row.id
  try {
    const retried = await retryImageGeneration(row.id)
    ElMessage.success('已重新提交生图任务')
    if (selectedRecord.value?.id === row.id) {
      selectedRecord.value = retried
    }
    await loadRecords()
  } finally {
    retryingRecordId.value = null
  }
}

function normalizeSubmitPayload(): CreateImageGenerationPayload | null {
  const aiSettingKey = form.value.aiSettingKey.trim()
  if (!aiSettingKey) {
    ElMessage.warning('请选择接口配置')
    return null
  }
  const positivePromptLines = splitLines(form.value.positivePromptText)
  const negativePromptLines = splitLines(form.value.negativePromptText)
  if (!form.value.promptTemplateCodes.length && !form.value.negativeTemplateCodes.length && !positivePromptLines.length && !negativePromptLines.length) {
    ElMessage.warning('请选择模板或填写提示词')
    return null
  }
  return {
    aiSettingKey,
    promptTemplateCodes: form.value.promptTemplateCodes,
    negativeTemplateCodes: form.value.negativeTemplateCodes,
    positivePromptLines,
    negativePromptLines,
    size: form.value.size,
    quality: form.value.quality,
    outputFormat: form.value.outputFormat,
    background: form.value.background,
    moderation: form.value.moderation,
    responseFormat: form.value.responseFormat,
    n: 1,
    user: form.value.user.trim() || undefined,
  }
}

function emptyForm(): GenerationForm {
  return {
    aiSettingKey: '',
    promptTemplateCodes: [],
    negativeTemplateCodes: [],
    positivePromptText: '',
    negativePromptText: '',
    size: '1024x1024',
    quality: 'high',
    outputFormat: 'png',
    background: 'opaque',
    moderation: 'auto',
    responseFormat: 'b64_json',
    inputFidelity: 'high',
    user: '',
  }
}

function emptyFilters(): ImageGenerationRecordQuery {
  return {
    status: '',
    generationType: '',
    aiSettingKey: '',
    keyword: '',
  }
}

function splitLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeFilters(value: ImageGenerationRecordQuery) {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => typeof item !== 'string' || item.trim()),
  ) as ImageGenerationRecordQuery
}

function settingLabel(item: AiModelSetting) {
  return `${item.settingKey} / ${item.modelName}`
}

function templateLabel(item: ImagePromptTemplate) {
  return `${item.templateName} (${item.templateCode})`
}

function typeLabel(value?: string) {
  return imageGenerationTypeLabelMap[value || ''] || value || '-'
}

function statusLabel(value?: string) {
  return imageGenerationStatusLabelMap[value || ''] || value || '-'
}

function statusTagType(value?: ImageGenerationStatus) {
  switch (value) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'primary'
    default:
      return 'info'
  }
}

function preview(value?: string) {
  if (!value) {
    return '-'
  }
  const normalized = value.replace(/\s+/g, ' ').trim()
  return normalized.length > 120 ? `${normalized.slice(0, 120)}...` : normalized
}

function formatJson(value?: string) {
  if (!value) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatResponseJson(value?: string) {
  if (!value) {
    return '-'
  }
  if (value.includes('b64_json') && value.length > 5000) {
    return '响应包含 b64_json 大内容，页面已隐藏具体内容。请查看后端保存的本地响应文件或结果图片。'
  }
  return formatJson(value)
}
</script>

<style scoped>
.image-record-page__thumb {
  width: 58px;
  height: 58px;
  border-radius: 10px;
  background: #f1f5f9;
}

.image-record-page__preview {
  color: var(--el-text-color-secondary);
}

.image-record-page__file-name {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
}

.image-record-page__detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.image-record-page__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.image-record-page__result {
  width: 100%;
  min-height: 240px;
  max-height: 460px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: #f8fafc;
}

.image-record-page__section {
  margin-top: 18px;
}

.image-record-page__section h3,
.image-record-page__detail-grid h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.image-record-page__section pre {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .image-record-page__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
