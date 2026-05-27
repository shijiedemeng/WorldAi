<template>
  <div class="admin-page defect-record-list-page">
    <PageHeader
      title="缺陷列表"
    >
      <el-button @click="goToSourceConfig">返回缺陷源</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="缺陷源">
          <el-select
            v-model="sourceCode"
            filterable
            placeholder="选择一个缺陷源"
            class="w-full"
            @change="handleSourceChange"
          >
            <el-option
              v-for="source in defectSources"
              :key="source.sourceCode"
              :label="formatSourceLabel(source)"
              :value="source.sourceCode"
            />
          </el-select>
        </el-form-item>

        <template v-if="selectedSource">
          <el-form-item label="平台">
            <el-tag>{{ platformLabelMap[selectedSource.platformType] || selectedSource.platformType }}</el-tag>
          </el-form-item>
          <el-form-item label="平台项目">
            <el-input :model-value="selectedSource.externalProjectName || selectedSource.externalProjectKey || '-'" disabled />
          </el-form-item>
        </template>

        <template v-if="selectedSource">
          <el-form-item label="关键字">
            <el-input v-model="queryForm.keyword" clearable placeholder="标题、编号、描述" />
          </el-form-item>

          <YunxiaoDefectFilterFields
            v-if="isYunxiaoSource"
            :query="queryForm"
            :members="yunxiaoMembers"
            :loading-members="loadingYunxiaoMembers"
          />

          <template v-else>
          <el-form-item label="缺陷状态">
            <el-select v-model="queryForm.status" clearable filterable class="w-full" placeholder="选择状态">
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="isZentaoSource" label="严重级别">
            <el-input v-model="queryForm.severity" clearable placeholder="按严重级别过滤" />
          </el-form-item>

          <el-form-item label="负责人">
            <el-input v-model="queryForm.assignedTo" clearable placeholder="按负责人名称过滤" />
          </el-form-item>

          <el-form-item label="创建人">
            <el-input v-model="queryForm.reporterName" clearable placeholder="按创建人名称过滤" />
          </el-form-item>
          </template>
        </template>

        <el-form-item class="filter-form__actions">
          <el-button :disabled="!selectedSource" @click="resetQuery">重置</el-button>
          <el-button type="primary" :disabled="!selectedSource" :loading="loadingDefects" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>远程缺陷</strong>
          <span v-if="selectedSource">
            {{ selectedSource.sourceName }} · {{ platformLabelMap[selectedSource.platformType] || selectedSource.platformType }}
          </span>
        </div>
      </div>

      <template v-if="selectedSource">
        <el-table :data="remoteDefects" stripe v-loading="loadingDefects" @row-dblclick="handleRowDblClick">
          <el-table-column prop="externalDefectKey" label="编号" width="140">
            <template #default="{ row }">
              {{ row.externalDefectKey || row.externalDefectId }}
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="280" />
          <el-table-column prop="defectStatus" label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :value="row.defectStatus || 'PENDING'" />
            </template>
          </el-table-column>
          <el-table-column prop="severity" label="严重级别" width="120" />
          <el-table-column prop="assignedTo" label="负责人" width="140" />
          <el-table-column prop="reporterName" label="创建人" width="140" />
          <el-table-column prop="updatedAtRemote" label="更新时间" width="180" />
          <el-table-column label="标记" width="140">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-if="row.persistedRecordId" type="success">已保存</el-tag>
                <el-tag v-if="getDefectAnalysis(row)?.pushStatus === 'ANALYZING'" type="warning">分析中</el-tag>
                <el-tag v-if="getDefectAnalysis(row)?.pushStatus === 'ANALYZED'" type="success">已分析</el-tag>
                <el-tag v-if="getDefectAnalysis(row)?.pushStatus === 'PUSHED'" type="success">已推送</el-tag>
                <el-tag v-if="row.hasImageFlag" type="warning">含图片</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="handleViewDetail(row)">详情</el-button>
              <el-button
                link
                :type="getDefectAnalysis(row) ? 'success' : 'warning'"
                @click.stop="handleOpenAnalysis(row)"
              >
                {{ getDefectAnalysis(row)?.pushStatus === 'ANALYZING' ? '分析中' : getDefectAnalysis(row) ? '查看分析' : 'AI处理' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            layout="total, sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50]"
            :total="total"
            @current-change="handlePageChange"
            @size-change="handlePageSizeChange"
          />
        </div>
      </template>

      <EmptyBlock v-else description="请先选择一个缺陷源，再查看对应平台的缺陷列表。" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import YunxiaoDefectFilterFields from './components/YunxiaoDefectFilterFields.vue'
import { fetchDefectAnalyses, fetchDefectSources, fetchRemoteDefects, fetchSourceYunxiaoMembers } from '@/api/defect'
import type { DefectAnalysis, DefectRemoteListQuery, DefectSourceConfig, RemoteDefect, YunxiaoProjectMember } from '@/types/defect'
import { defectPlatformOptions } from '@/types/options'

const route = useRoute()
const router = useRouter()

const platformLabelMap = Object.fromEntries(defectPlatformOptions.map((item) => [item.value, item.label]))

const defectSources = ref<DefectSourceConfig[]>([])
const sourceCode = ref('')
const remoteDefects = ref<RemoteDefect[]>([])
const defectAnalyses = ref<DefectAnalysis[]>([])
const queryForm = ref<DefectRemoteListQuery>({
  keyword: '',
  status: '',
  severity: '',
  assignedTo: '',
  reporterName: '',
  tag: '',
  orderBy: 'updatedAt',
  sort: 'desc',
})
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loadingDefects = ref(false)
const loadingYunxiaoMembers = ref(false)
const yunxiaoMembers = ref<YunxiaoProjectMember[]>([])
const yunxiaoMemberCache = new Map<string, YunxiaoProjectMember[]>()

const selectedSource = computed(() => defectSources.value.find((item) => item.sourceCode === sourceCode.value) || null)
const isYunxiaoSource = computed(() => selectedSource.value?.platformType === 'YUNXIAO')
const isZentaoSource = computed(() => selectedSource.value?.platformType === 'ZENTAO')
const statusOptions = computed(() => {
  const seen = new Set<string>()
  const defaults = isZentaoSource.value
    ? [
        { label: '激活', value: 'active' },
        { label: '已解决', value: 'resolved' },
        { label: '已关闭', value: 'closed' },
      ]
    : []
  return [
    ...defaults,
    ...remoteDefects.value.map((item) => ({
      label: item.defectStatus || item.defectStatusId || '-',
      value: item.defectStatusId || item.defectStatus || '',
    })),
  ]
    .filter((item) => {
      if (!item.value || seen.has(item.value)) {
        return false
      }
      seen.add(item.value)
      return true
    })
})

onMounted(async () => {
  await loadSources()
  if (sourceCode.value) {
    await loadYunxiaoMembers()
    await loadRemoteDefects()
  }
})

onActivated(async () => {
  if (remoteDefects.value.length > 0) {
    await loadDefectAnalyses()
  }
})

async function loadSources() {
  defectSources.value = await fetchDefectSources()
  const routeSourceCode = typeof route.query.sourceCode === 'string' ? route.query.sourceCode.trim() : ''
  const matched = routeSourceCode ? defectSources.value.find((item) => item.sourceCode === routeSourceCode) : null
  if (matched) {
    sourceCode.value = matched.sourceCode
    return
  }
  if (!sourceCode.value && defectSources.value.length > 0) {
    sourceCode.value = defectSources.value[0].sourceCode
    await router.replace({ query: { ...route.query, sourceCode: sourceCode.value } })
  }
}

function formatSourceLabel(source: DefectSourceConfig) {
  const platform = platformLabelMap[source.platformType] || source.platformType
  return `${source.sourceName} / ${platform} / ${source.sourceCode}`
}

async function handleSourceChange(value: string) {
  sourceCode.value = value
  queryForm.value = {
    keyword: '',
    status: '',
    severity: '',
    assignedTo: '',
    reporterName: '',
    tag: '',
    orderBy: 'updatedAt',
    sort: 'desc',
  }
  page.value = 1
  yunxiaoMembers.value = []
  await router.replace({ query: { ...route.query, sourceCode: value } })
  await loadYunxiaoMembers()
  await loadRemoteDefects()
}

function goToSourceConfig() {
  router.push({ path: '/defects' })
}

async function handleSearch() {
  page.value = 1
  await loadRemoteDefects()
}

async function resetQuery() {
  queryForm.value = {
    keyword: '',
    status: '',
    severity: '',
    assignedTo: '',
    reporterName: '',
    tag: '',
    orderBy: 'updatedAt',
    sort: 'desc',
  }
  page.value = 1
  await loadRemoteDefects()
}

async function handlePageChange(nextPage: number) {
  page.value = nextPage
  await loadRemoteDefects()
}

async function handlePageSizeChange(nextPageSize: number) {
  pageSize.value = nextPageSize
  page.value = 1
  await loadRemoteDefects()
}

async function loadRemoteDefects() {
  if (!sourceCode.value) {
    remoteDefects.value = []
    total.value = 0
    return
  }
  loadingDefects.value = true
  try {
    const response = await fetchRemoteDefects(sourceCode.value, {
      ...queryForm.value,
      page: page.value,
      pageSize: pageSize.value,
      orderBy: queryForm.value.orderBy || 'updatedAt',
      sort: queryForm.value.sort || 'desc',
    })
    remoteDefects.value = response.items
    total.value = response.total
    page.value = response.page
    pageSize.value = response.pageSize
    await loadDefectAnalyses()
  } catch (error) {
    remoteDefects.value = []
    defectAnalyses.value = []
    total.value = 0
    ElMessage.error(error instanceof Error ? error.message : '缺陷列表加载失败')
  } finally {
    loadingDefects.value = false
  }
}

async function loadDefectAnalyses() {
  const projectCode = selectedSource.value?.projectCode
  if (!projectCode) {
    defectAnalyses.value = []
    return
  }
  defectAnalyses.value = await fetchDefectAnalyses(projectCode).catch(() => [])
}

async function loadYunxiaoMembers() {
  if (!sourceCode.value || !isYunxiaoSource.value) {
    yunxiaoMembers.value = []
    return
  }
  const cachedMembers = yunxiaoMemberCache.get(sourceCode.value)
  if (cachedMembers) {
    yunxiaoMembers.value = cachedMembers
    return
  }
  loadingYunxiaoMembers.value = true
  try {
    const members = normalizeYunxiaoMembers(await fetchSourceYunxiaoMembers(sourceCode.value))
    yunxiaoMemberCache.set(sourceCode.value, members)
    yunxiaoMembers.value = members
  } catch (error) {
    yunxiaoMembers.value = []
    ElMessage.error(error instanceof Error ? error.message : '云效成员加载失败')
  } finally {
    loadingYunxiaoMembers.value = false
  }
}

function normalizeYunxiaoMembers(members: YunxiaoProjectMember[]) {
  const seen = new Set<string>()
  return members.filter((member) => {
    if (!member.userId || seen.has(member.userId)) {
      return false
    }
    seen.add(member.userId)
    return true
  })
}

function handleRowDblClick(row: RemoteDefect) {
  void handleViewDetail(row)
}

function handleViewDetail(row: RemoteDefect) {
  if (!sourceCode.value) {
    return
  }
  router.push({
    name: 'defect-record-detail',
    params: {
      sourceCode: sourceCode.value,
      externalDefectId: row.externalDefectId,
    },
  })
}

function getDefectAnalysis(row: RemoteDefect) {
  if (row.persistedRecordId) {
    const matchedByRecordId = defectAnalyses.value.find((item) => item.defectRecordId === row.persistedRecordId)
    if (matchedByRecordId) {
      return matchedByRecordId
    }
  }
  return defectAnalyses.value.find((item) => item.sourceCode === row.sourceCode && item.externalDefectId === row.externalDefectId) || null
}

function handleOpenAnalysis(row: RemoteDefect) {
  if (!sourceCode.value) {
    return
  }
  router.push({
    name: 'defect-record-analysis',
    params: {
      sourceCode: sourceCode.value,
      externalDefectId: row.externalDefectId,
    },
  })
}
</script>

<style scoped>
.defect-record-list-page__project-filter {
  width: 220px;
}

.defect-record-list-page__source-selector {
  display: grid;
  gap: 10px;
  width: 100%;
}

.defect-record-list-page__source-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.defect-record-list-page__source-meta strong {
  color: var(--el-text-color-primary);
}

@media (max-width: 960px) {
  .defect-record-list-page__project-filter {
    width: 100%;
  }
}
</style>
