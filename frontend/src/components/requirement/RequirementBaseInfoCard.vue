<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>需求基本信息</span>
      </div>
    </template>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="需求编号">{{ requirement.requirementNo }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ requirement.title }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ requirementTypeLabelMap[requirement.requirementType] || requirement.requirementType }}</el-descriptions-item>
      <el-descriptions-item label="项目编码">{{ requirement.projectCode }}</el-descriptions-item>
      <el-descriptions-item label="优先级">{{ requirement.priority || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <StatusTag :value="requirement.status" />
      </el-descriptions-item>
      <el-descriptions-item label="当前阶段">{{ requirement.currentStage || '-' }}</el-descriptions-item>
      <template v-if="isSubRequirement">
        <el-descriptions-item label="主控 Agent">{{ requirement.mainAgentCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="父需求">{{ requirement.parentRequirementNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会话策略">{{ sessionStrategyLabelMap[requirement.sessionStrategy || ''] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="回执可提取">{{ requirement.resultExtractableFlag === false ? '否' : '是' }}</el-descriptions-item>
        <el-descriptions-item label="MCP文件检索">{{ requirement.mcpFileSearchEnabledFlag ? '启用' : '关闭' }}</el-descriptions-item>
        <el-descriptions-item label="项目储备库">{{ projectKnowledgeSearchLabel }}</el-descriptions-item>
        <el-descriptions-item label="人工审核">
          {{ requirement.reviewRequiredFlag ? (requirement.reviewApprovedFlag ? '需要，已通过' : '需要，待通过') : '否' }}
        </el-descriptions-item>
      </template>
      <template v-else>
        <el-descriptions-item label="自动执行">{{ requirement.autoExecuteFlag ? '允许' : '禁止' }}</el-descriptions-item>
        <el-descriptions-item label="MCP检索Agent">{{ requirement.fileSearchMcpAgentCodes || '-' }}</el-descriptions-item>
      </template>
      <el-descriptions-item label="期望完成时间">{{ requirement.expectedDeadline || '-' }}</el-descriptions-item>
      <el-descriptions-item label="需求说明" :span="2">
        <div
          class="requirement-base-info__text"
          :class="{
            'is-truncated': isTruncated(requirement.requirementDesc),
            'is-editable': editableDescription,
          }"
          :title="editableDescription ? '双击编辑需求说明' : '双击查看全文'"
          @dblclick="handleDescriptionDblClick"
        >
          {{ formatPreview(requirement.requirementDesc) }}
          <span v-if="editableDescription" class="requirement-base-info__edit-hint">双击编辑</span>
        </div>
      </el-descriptions-item>
      <el-descriptions-item v-if="isSubRequirement" label="执行步骤" :span="2">
        <div
          class="requirement-base-info__text requirement-base-info__steps"
          :class="{ 'is-truncated': isTruncated(requirement.executionSteps) }"
          @dblclick="openFullText('执行步骤', requirement.executionSteps)"
        >
          {{ formatPreview(requirement.executionSteps) }}
        </div>
      </el-descriptions-item>
    </el-descriptions>

    <el-dialog v-model="fullTextDialogVisible" :title="fullTextTitle" width="760px" destroy-on-close>
      <pre class="requirement-base-info__full-text">{{ fullTextContent }}</pre>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Requirement } from '@/types/requirement'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  requirementTypeOptions,
  sessionStrategyOptions,
} from '@/types/options'

const props = defineProps<{
  requirement: Requirement
  editableDescription?: boolean
}>()

const emit = defineEmits<{
  editDescription: []
}>()

const requirementTypeLabelMap = Object.fromEntries(requirementTypeOptions.map((item) => [item.value, item.label]))
const sessionStrategyLabelMap = Object.fromEntries(sessionStrategyOptions.map((item) => [item.value, item.label]))
const isSubRequirement = computed(() => props.requirement.requirementType === 'SUB')
const projectKnowledgeSearchLabel = computed(() => {
  if (!props.requirement.projectKnowledgeSearchEnabledFlag) {
    return '关闭'
  }
  const limit = props.requirement.projectKnowledgeSearchLimit ?? 5
  const minScore = props.requirement.projectKnowledgeSearchMinScore ?? 70
  return `启用，${limit}条，准确值≥${minScore}`
})
const previewLimit = 220
const fullTextDialogVisible = ref(false)
const fullTextTitle = ref('')
const fullTextContent = ref('')

function isTruncated(value?: string) {
  return Boolean(value && value.trim().length > previewLimit)
}

function formatPreview(value?: string) {
  const text = value?.trim() || ''
  if (!text) {
    return '-'
  }
  if (text.length <= previewLimit) {
    return text
  }
  return `${text.slice(0, previewLimit)}....`
}

function openFullText(title: string, value?: string) {
  const text = value?.trim() || ''
  if (!text) {
    return
  }
  fullTextTitle.value = title
  fullTextContent.value = text
  fullTextDialogVisible.value = true
}

function handleDescriptionDblClick() {
  if (props.editableDescription) {
    emit('editDescription')
    return
  }
  openFullText('需求说明', props.requirement.requirementDesc)
}
</script>

<style scoped>
.requirement-base-info__text {
  white-space: pre-line;
  line-height: 1.75;
  cursor: zoom-in;
}

.requirement-base-info__text.is-editable {
  cursor: text;
  border-radius: 10px;
  padding: 8px 10px;
  background: rgba(21, 91, 212, 0.05);
  border: 1px dashed rgba(21, 91, 212, 0.28);
}

.requirement-base-info__edit-hint {
  display: inline-flex;
  margin-left: 10px;
  color: var(--el-color-primary);
  font-size: 12px;
}

.requirement-base-info__steps {
  white-space: pre-line;
}

.requirement-base-info__full-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  max-height: 60vh;
  overflow: auto;
  font-family: inherit;
}
</style>
