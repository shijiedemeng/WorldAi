<template>
  <div class="admin-page ai-analysis-log-page">
    <PageHeader
      title="AI分析记录"
    />

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="来源">
          <el-select v-model="filters.sourceType" clearable class="w-full" placeholder="全部来源">
            <el-option
              v-for="item in sourceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable class="w-full" placeholder="全部状态">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="兜底生成" value="FALLBACK" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <el-input v-model="filters.projectCode" clearable placeholder="按项目编码过滤" />
        </el-form-item>
        <el-form-item label="业务编号">
          <el-input v-model="filters.businessNo" clearable placeholder="需求编号或分析编号" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="搜索业务编号、项目、模型、错误信息" />
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>AI 请求响应日志</strong>
          <span>共 {{ total }} 条，按创建时间倒序。</span>
        </div>
      </div>

      <el-table
        :data="logs"
        stripe
        v-loading="loading"
        row-key="id"
        empty-text="暂无 AI 分析记录"
        @row-dblclick="openDetail"
      >
        <el-table-column label="ID" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ row.id }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">{{ formatSourceType(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务编号" width="180" show-overflow-tooltip />
        <el-table-column prop="projectCode" label="项目" width="130" show-overflow-tooltip />
        <el-table-column prop="aiSettingKey" label="模型配置" width="160" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="loadLogs"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="AI分析记录详情" size="70%">
      <template v-if="selectedLog">
        <div v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ selectedLog.id }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ formatSourceType(selectedLog.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ selectedLog.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="项目编码">{{ selectedLog.projectCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型配置">{{ selectedLog.aiSettingKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ selectedLog.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="selectedLog.status" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedLog.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <section class="ai-analysis-log-page__section">
          <h3>请求内容</h3>
          <pre>{{ formatJson(selectedLog.requestPayload) }}</pre>
        </section>
        <section class="ai-analysis-log-page__section">
          <h3>响应内容</h3>
          <pre>{{ formatJson(responseDetailContent(selectedLog)) }}</pre>
        </section>
        <section v-if="selectedLog.errorMessage" class="ai-analysis-log-page__section">
          <h3>错误信息</h3>
          <pre>{{ selectedLog.errorMessage }}</pre>
        </section>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { fetchAiAnalysisLogDetail, fetchAiAnalysisLogs } from '@/api/log'
import type { AiAnalysisLog, AiAnalysisLogQuery } from '@/types/log'

const sourceTypeOptions = [
  { label: '需求 AI 拆分', value: 'REQUIREMENT_AI_SPLIT' },
  { label: '缺陷 AI 分析', value: 'DEFECT_AI_ANALYSIS' },
  { label: '项目储备知识 AI 整理', value: 'PROJECT_KNOWLEDGE_ORGANIZE' },
]

const loading = ref(false)
const logs = ref<AiAnalysisLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedLog = ref<AiAnalysisLog | null>(null)
const filters = ref<AiAnalysisLogQuery>(emptyFilters())

onMounted(async () => {
  await loadLogs()
})

async function loadLogs() {
  loading.value = true
  try {
    const response = await fetchAiAnalysisLogs({
      ...normalizeFilters(filters.value),
      page: page.value,
      pageSize: pageSize.value,
    })
    logs.value = response.items
    total.value = response.total
    page.value = response.page
    pageSize.value = response.pageSize
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  page.value = 1
  await loadLogs()
}

async function resetFilters() {
  filters.value = emptyFilters()
  page.value = 1
  await loadLogs()
}

async function handlePageSizeChange(nextPageSize: number) {
  pageSize.value = nextPageSize
  page.value = 1
  await loadLogs()
}

async function openDetail(row: AiAnalysisLog) {
  detailVisible.value = true
  detailLoading.value = true
  selectedLog.value = row
  try {
    selectedLog.value = await fetchAiAnalysisLogDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

function emptyFilters(): AiAnalysisLogQuery {
  return {
    sourceType: '',
    projectCode: '',
    businessNo: '',
    status: '',
    keyword: '',
  }
}

function normalizeFilters(value: AiAnalysisLogQuery) {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => typeof item !== 'string' || item.trim()),
  ) as AiAnalysisLogQuery
}

function formatSourceType(value?: string) {
  return sourceTypeOptions.find((item) => item.value === value)?.label || value || '-'
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

function responseDetailContent(log: AiAnalysisLog) {
  if (log.responsePayload) {
    return log.responsePayload
  }
  if (log.errorMessage) {
    return `该日志没有保存原始响应体，以下是错误信息：\n${log.errorMessage}`
  }
  return '-'
}
</script>

<style scoped>
.ai-analysis-log-page__section {
  margin-top: 18px;
}

.ai-analysis-log-page__section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.ai-analysis-log-page__section pre {
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
</style>
