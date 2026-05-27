<template>
  <div class="admin-page defect-record-analysis-page">
    <PageHeader
      title="AI 分析与需求处理"
    >
      <el-button @click="goBackToDetail">返回详情</el-button>
      <el-button :disabled="loadingDetail" :loading="loadingDetail" @click="loadDetail">刷新</el-button>
    </PageHeader>

    <div
      v-if="selectedRemoteDefect"
      class="defect-record-analysis-page__content"
      v-loading="operationLocked"
      element-loading-text="AI 处理中，请勿操作"
    >
      <el-card class="analysis-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>缺陷摘要</span>
            <el-space wrap>
              <el-tag>{{ platformLabelMap[selectedRemoteDefect.platformType] || selectedRemoteDefect.platformType }}</el-tag>
              <el-tag type="info">{{ selectedSource?.sourceName || currentSourceCode }}</el-tag>
              <el-tag type="info">{{ selectedRemoteDefect.externalDefectKey || selectedRemoteDefect.externalDefectId }}</el-tag>
              <el-tag v-if="hasPushed" type="success">已推送需求</el-tag>
            </el-space>
          </div>
        </template>
        <h2>{{ selectedRemoteDefect.title }}</h2>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="缺陷源">{{ selectedSource?.sourceName || currentSourceCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedRemoteDefect.defectStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="严重级别">{{ selectedRemoteDefect.severity || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ selectedRemoteDefect.assignedTo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ selectedRemoteDefect.reporterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ selectedRemoteDefect.updatedAtRemote || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联需求">{{ analysisResult?.nextRequirementNo || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="analysis-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>AI 分析配置</span>
            <StatusTag v-if="analysisResult" :value="analysisResult.pushStatus" />
          </div>
        </template>

        <el-alert
          v-if="hasPushed"
          type="success"
          show-icon
          :closable="false"
          title="该缺陷已经推送到需求，不允许重复推送。"
        />
        <el-alert
          v-else-if="analysisInProgress"
          type="warning"
          show-icon
          :closable="false"
          title="后台正在分析该缺陷，当前页面会自动刷新结果，不能重复发起分析。"
        />
        <el-alert
          v-else-if="hasExistingAnalysis"
          type="success"
          show-icon
          :closable="false"
          title="该缺陷已有 AI 分析记录，当前页面展示的是已有分析结果，不会重复分析。"
        />

        <el-form :model="analysisForm" label-width="110px" class="analysis-form">
          <el-form-item label="分析编号" required>
            <el-input v-model="analysisForm.analysisNo" :disabled="operationLocked || hasPushed" placeholder="analysis-defect-001" />
          </el-form-item>
          <el-form-item label="AI 配置">
            <el-select v-model="analysisForm.aiSettingKey" :disabled="operationLocked || hasPushed" clearable filterable class="w-full">
              <el-option v-for="item in aiSettings" :key="item.settingKey" :label="`${item.providerName} / ${item.modelName}`" :value="item.settingKey" />
            </el-select>
          </el-form-item>
          <el-form-item label="当前 Agent">
            <el-select v-model="agentScopeList" :disabled="operationLocked || hasPushed" multiple filterable allow-create default-first-option class="w-full">
              <el-option v-for="agent in projectAgents" :key="agent.agentCode" :label="agent.agentName" :value="agent.agentCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="人工摘要">
            <el-input v-model="analysisForm.editedSummary" :disabled="operationLocked || hasPushed" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="分析提示">
            <el-input v-model="analysisForm.promptText" :disabled="operationLocked || hasPushed" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="analyzing || analysisInProgress" :disabled="operationLocked || hasPushed || hasExistingAnalysis || analysisInProgress" @click="handleAnalyze">AI 分析缺陷</el-button>
          </el-form-item>
        </el-form>

        <div v-if="analysisResult && !analysisInProgress" class="analysis-result">
          <div class="card-header">
            <span>分析结果</span>
          </div>
          <pre>{{ analysisResult.analysisResult }}</pre>

          <el-form :model="pushForm" label-width="110px" class="push-form">
            <el-form-item label="需求编号">
              <el-input :model-value="analysisResult.nextRequirementNo || '系统自动生成'" disabled />
            </el-form-item>
            <el-form-item label="需求标题">
              <el-input v-model="pushForm.title" :disabled="operationLocked || hasPushed" placeholder="修复某条外部缺陷" />
            </el-form-item>
            <el-form-item label="需求类型" required>
              <el-select v-model="pushForm.executionMode" :disabled="operationLocked || hasPushed" class="w-full">
                <el-option
                  v-for="opt in requirementExecutionModeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="主负责 Agent">
              <el-select v-model="pushForm.mainAgentCode" :disabled="operationLocked || hasPushed" clearable filterable class="w-full">
                <el-option v-for="agent in projectAgents" :key="agent.agentCode" :label="agent.agentName" :value="agent.agentCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="子任务步骤">
              <el-input v-model="pushForm.executionSteps" :disabled="operationLocked || hasPushed" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item>
          <el-button type="success" :loading="pushing" :disabled="operationLocked || hasPushed || analysisInProgress" @click="handlePush">推送到需求列表</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-card>
    </div>

    <el-card v-else class="analysis-card" shadow="never">
      <EmptyBlock :description="loadingDetail ? '正在加载缺陷详情' : '未找到当前缺陷，请返回列表重新选择'" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import { fetchAgents } from '@/api/agent'
import {
  analyzeDefect,
  fetchDefectAnalyses,
  fetchDefectSources,
  pushDefectToRequirement,
  viewSourceDefectDetail,
} from '@/api/defect'
import { fetchAiSettings } from '@/api/system'
import type { Agent } from '@/types/agent'
import type { AnalyzeDefectPayload, DefectAnalysis, DefectSourceConfig, PushDefectToRequirementPayload, RemoteDefect } from '@/types/defect'
import type { AiModelSetting } from '@/types/system'
import { defectPlatformOptions, requirementExecutionModeOptions } from '@/types/options'
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
const aiSettings = ref<AiModelSetting[]>([])
const agents = ref<Agent[]>([])
const analysisResult = ref<DefectAnalysis | null>(null)
const analyzing = ref(false)
const pushing = ref(false)
const loadingDetail = ref(false)
const currentSourceCode = ref(props.sourceCode || '')
const currentExternalDefectId = ref(props.externalDefectId || '')
let analysisPollTimer: number | undefined

const analysisForm = ref<AnalyzeDefectPayload>({
  analysisNo: '',
  projectCode: '',
  aiSettingKey: '',
  agentScope: '',
  promptText: '',
  editedSummary: '',
})

const pushForm = ref<PushDefectToRequirementPayload>({
  title: '',
  executionMode: 'NORMAL',
  mainAgentCode: '',
  executionSteps: '',
})

const analysisInProgress = computed(() => analysisResult.value?.pushStatus === 'ANALYZING')
const operationLocked = computed(() => analyzing.value || pushing.value || analysisInProgress.value)
const hasPushed = computed(() => analysisResult.value?.pushStatus === 'PUSHED' || Boolean(analysisResult.value?.nextRequirementNo))
const hasExistingAnalysis = computed(() => Boolean(analysisResult.value?.analysisResult))
const agentScopeList = computed({
  get: () => (analysisForm.value.agentScope ? analysisForm.value.agentScope.split(',').map((item) => item.trim()).filter(Boolean) : []),
  set: (value: string[]) => {
    analysisForm.value.agentScope = value.join(',')
  },
})
const projectAgents = computed(() => {
  if (!analysisForm.value.projectCode) {
    return agents.value
  }
  return agents.value.filter((item) => item.projectCode === analysisForm.value.projectCode)
})

function buildDefaultAnalysisNo(defect: RemoteDefect) {
  return `analysis-${defect.sourceCode}-${defect.externalDefectId}`.replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 64)
}

onMounted(async () => {
  await Promise.allSettled([loadSources(), loadAiSettings(), loadAgents()])
  await loadDetail()
})

onBeforeUnmount(() => {
  stopAnalysisPolling()
})

onActivated(() => {
  if (analysisInProgress.value) {
    startAnalysisPolling()
  }
})

onDeactivated(() => {
  stopAnalysisPolling()
})

watch(
  () => [props.sourceCode, props.externalDefectId],
  async () => {
    if (analyzing.value || pushing.value) {
      return
    }
    currentSourceCode.value = props.sourceCode || ''
    currentExternalDefectId.value = props.externalDefectId || ''
    await loadDetail()
  },
)

watch(analysisInProgress, (value) => {
  if (value) {
    startAnalysisPolling()
  } else {
    stopAnalysisPolling()
  }
})

async function loadAiSettings() {
  aiSettings.value = await fetchAiSettings({ modelPurpose: 'LANGUAGE', enabledFlag: true })
}

async function loadAgents() {
  agents.value = await fetchAgents()
}

async function loadSources() {
  defectSources.value = await fetchDefectSources()
}

async function loadDetail() {
  if (analyzing.value || pushing.value || !currentSourceCode.value || !currentExternalDefectId.value) {
    return
  }
  loadingDetail.value = true
  try {
    const defect = await viewSourceDefectDetail(currentSourceCode.value, currentExternalDefectId.value)
    selectedRemoteDefect.value = defect
    const analyses = defect.persistedRecordId
      ? await fetchDefectAnalyses(defect.projectCode)
      : []
    analysisResult.value = analyses.find((item) => item.defectRecordId === defect.persistedRecordId && item.pushStatus === 'ANALYZING')
      || analyses.find((item) => item.defectRecordId === defect.persistedRecordId && (item.nextRequirementNo || item.pushStatus === 'PUSHED'))
      || analyses.find((item) => item.defectRecordId === defect.persistedRecordId)
      || null
    analysisForm.value = {
      analysisNo: analysisResult.value?.analysisNo || buildDefaultAnalysisNo(defect),
      projectCode: defect.projectCode,
      defectRecordId: defect.persistedRecordId,
      sourceCode: defect.sourceCode,
      externalDefectId: defect.externalDefectId,
      externalDefectKey: defect.externalDefectKey,
      title: defect.title,
      severity: defect.severity,
      defectStatus: defect.defectStatus,
      defectType: defect.defectType,
      assignedTo: defect.assignedTo,
      reporterName: defect.reporterName,
      summary: defect.summary,
      descriptionText: defect.descriptionText,
      aiSettingKey: analysisResult.value?.aiSettingKey || '',
      agentScope: analysisResult.value?.agentScope || '',
      promptText: analysisResult.value?.promptText || '',
      editedSummary: analysisResult.value?.editedSummary || defect.summary || defect.descriptionText || '',
    }
    pushForm.value = {
      title: `修复缺陷：${defect.title}`,
      executionMode: 'NORMAL',
      mainAgentCode: '',
      executionSteps: '',
    }
  } catch (error) {
    selectedRemoteDefect.value = null
    analysisResult.value = null
    ElMessage.error(error instanceof Error ? error.message : '缺陷处理信息加载失败')
  } finally {
    loadingDetail.value = false
  }
}

function goBackToDetail() {
  stopAnalysisPolling()
  appStore.closeActiveTabAndOpen({
    name: 'defect-record-detail',
    params: {
      sourceCode: currentSourceCode.value,
      externalDefectId: currentExternalDefectId.value,
    },
  }, router)
}

async function handleAnalyze() {
  if (!selectedRemoteDefect.value) {
    ElMessage.warning('请先加载缺陷详情')
    return
  }
  if (hasPushed.value) {
    ElMessage.warning('该缺陷已经推送到需求，不允许重复处理')
    return
  }
  if (hasExistingAnalysis.value) {
    ElMessage.warning('该缺陷已有 AI 分析记录，请直接查看当前分析结果')
    return
  }
  if (analysisInProgress.value) {
    ElMessage.warning('后台正在分析该缺陷，请等待当前分析完成')
    return
  }
  analysisForm.value.sourceCode = selectedRemoteDefect.value.sourceCode
  analysisForm.value.externalDefectId = selectedRemoteDefect.value.externalDefectId
  analysisForm.value.externalDefectKey = selectedRemoteDefect.value.externalDefectKey
  analysisForm.value.title = selectedRemoteDefect.value.title
  analysisForm.value.severity = selectedRemoteDefect.value.severity
  analysisForm.value.defectStatus = selectedRemoteDefect.value.defectStatus
  analysisForm.value.defectType = selectedRemoteDefect.value.defectType
  analysisForm.value.assignedTo = selectedRemoteDefect.value.assignedTo
  analysisForm.value.reporterName = selectedRemoteDefect.value.reporterName
  analysisForm.value.summary = selectedRemoteDefect.value.summary
  analysisForm.value.descriptionText = selectedRemoteDefect.value.descriptionText
  analysisForm.value.defectRecordId = selectedRemoteDefect.value.persistedRecordId
  analyzing.value = true
  try {
    analysisResult.value = await analyzeDefect(analysisForm.value)
    if (analysisResult.value.pushStatus === 'ANALYZING') {
      ElMessage.warning('后台已有分析任务正在执行，已切换为等待结果')
    } else {
      ElMessage.success('分析完成，已保存基础缺陷信息')
    }
    selectedRemoteDefect.value.persistedRecordId = analysisResult.value.defectRecordId
  } catch (error) {
    await refreshCurrentAnalysis(true)
  } finally {
    analyzing.value = false
  }
}

async function handlePush() {
  if (!analysisResult.value) {
    ElMessage.warning('请先完成分析')
    return
  }
  if (hasPushed.value) {
    ElMessage.warning('该缺陷已经推送到需求，不允许重复推送')
    return
  }
  if (analysisInProgress.value) {
    ElMessage.warning('后台正在分析该缺陷，请等待当前分析完成')
    return
  }
  if (!pushForm.value.executionMode) {
    ElMessage.warning('请选择需求类型')
    return
  }
  pushing.value = true
  try {
    analysisResult.value = await pushDefectToRequirement(analysisResult.value.analysisNo, pushForm.value)
    ElMessage.success('已推送到需求列表')
    if (analysisResult.value.nextRequirementNo) {
      appStore.closeActiveTabAndOpen({
        name: 'requirement-detail',
        params: { requirementNo: analysisResult.value.nextRequirementNo },
      }, router)
    }
  } finally {
    pushing.value = false
  }
}

function startAnalysisPolling() {
  if (analysisPollTimer !== undefined) {
    return
  }
  analysisPollTimer = window.setInterval(() => {
    void refreshCurrentAnalysis()
  }, 3000)
}

function stopAnalysisPolling() {
  if (analysisPollTimer === undefined) {
    return
  }
  window.clearInterval(analysisPollTimer)
  analysisPollTimer = undefined
}

async function refreshCurrentAnalysis(force = false) {
  const projectCode = analysisForm.value.projectCode || selectedRemoteDefect.value?.projectCode
  const defectRecordId = selectedRemoteDefect.value?.persistedRecordId || analysisResult.value?.defectRecordId
  if (!projectCode || (!defectRecordId && !analysisForm.value.analysisNo) || (!force && (analyzing.value || pushing.value))) {
    return
  }
  const analyses = await fetchDefectAnalyses(projectCode).catch(() => [])
  const latest = analyses.find((item) => defectRecordId && item.defectRecordId === defectRecordId && item.pushStatus === 'ANALYZING')
    || analyses.find((item) => item.analysisNo === analysisForm.value.analysisNo && item.pushStatus === 'ANALYZING')
    || analyses.find((item) => defectRecordId && item.defectRecordId === defectRecordId && (item.nextRequirementNo || item.pushStatus === 'PUSHED'))
    || analyses.find((item) => item.analysisNo === analysisForm.value.analysisNo && (item.nextRequirementNo || item.pushStatus === 'PUSHED'))
    || analyses.find((item) => defectRecordId && item.defectRecordId === defectRecordId)
    || analyses.find((item) => item.analysisNo === analysisForm.value.analysisNo)
    || null
  if (!latest) {
    return
  }
  analysisResult.value = latest
  if (selectedRemoteDefect.value && latest.defectRecordId) {
    selectedRemoteDefect.value.persistedRecordId = latest.defectRecordId
  }
  analysisForm.value.aiSettingKey = latest.aiSettingKey || analysisForm.value.aiSettingKey
  analysisForm.value.agentScope = latest.agentScope || analysisForm.value.agentScope
  analysisForm.value.promptText = latest.promptText || analysisForm.value.promptText
  analysisForm.value.editedSummary = latest.editedSummary || analysisForm.value.editedSummary
}
</script>

<style scoped>
.defect-record-analysis-page {
  display: grid;
  gap: 18px;
}

.analysis-card {
  margin-bottom: 0;
}

.defect-record-analysis-page__content {
  position: relative;
  min-height: 240px;
}

.analysis-form {
  margin-top: 16px;
}

.analysis-result {
  margin-top: 18px;
}

.analysis-result pre {
  margin: 14px 0 0;
  padding: 16px;
  border-radius: 16px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-family: 'IBM Plex Mono', 'SFMono-Regular', monospace;
}

.push-form {
  margin-top: 18px;
}

.w-full {
  width: 100%;
}
</style>
