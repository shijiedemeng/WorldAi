<template>
  <div class="admin-page">
    <PageHeader
      title="系统设置"
    >
      <el-button type="primary" @click="openDialog()">新增模型配置</el-button>
    </PageHeader>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>AI 模型配置列表</strong>
          <span>共 {{ list.length }} 条配置，默认按最新更新时间排序。</span>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="settingKey" label="配置键" width="220" />
        <el-table-column prop="providerName" label="服务商" width="160" />
        <el-table-column prop="modelName" label="模型" width="180" />
        <el-table-column label="模型用途" width="110">
          <template #default="{ row }">{{ aiModelPurposeLabelMap[modelPurpose(row)] || modelPurpose(row) }}</template>
        </el-table-column>
        <el-table-column label="拆分配置" width="150">
          <template #default="{ row }">
            {{ modelPurpose(row) === 'VECTOR' ? `${row.vectorChunkSize || 500} / ${row.vectorChunkOverlap ?? 100}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="接口地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.enabledFlag ? 'ENABLED' : 'DISABLED'" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
      <el-form :model="form" label-width="120px">
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="配置键" required>
              <el-input v-model="form.settingKey" placeholder="如 openai-gpt-5-vision" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="服务商" required>
              <el-input v-model="form.providerName" placeholder="如 OpenAI" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="模型名称" required>
              <el-input v-model="form.modelName" placeholder="gpt-5.4" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="模型用途" required>
              <el-select v-model="form.modelPurpose" class="w-full" placeholder="选择模型用途">
                <el-option v-for="item in aiModelPurposeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.modelPurpose === 'VECTOR'" :md="12" :xs="24">
            <el-form-item label="拆分字段长度">
              <el-input-number v-model="form.vectorChunkSize" :min="100" :max="8000" :step="100" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.modelPurpose === 'VECTOR'" :md="12" :xs="24">
            <el-form-item label="重叠度">
              <el-input-number v-model="form.vectorChunkOverlap" :min="0" :max="Math.max((form.vectorChunkSize || 500) - 1, 0)" :step="50" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="接口地址">
              <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="API Key">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="启用">
              <el-switch v-model="form.enabledFlag" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="默认提示词">
              <el-input v-model="form.promptTemplate" type="textarea" :rows="5" placeholder="用于缺陷分析的默认提示词模版" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { fetchAiSettings, saveAiSetting } from '@/api/system'
import type { AiModelSetting } from '@/types/system'
import { aiModelPurposeLabelMap, aiModelPurposeOptions } from '@/types/options'

const loading = ref(false)
const saving = ref(false)
const list = ref<AiModelSetting[]>([])
const dialogVisible = ref(false)
const form = ref<AiModelSetting>(emptyForm())

const dialogTitle = computed(() => (form.value.id ? '编辑 AI 模型配置' : '新增 AI 模型配置'))

onMounted(async () => {
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    list.value = await fetchAiSettings()
  } finally {
    loading.value = false
  }
}

function emptyForm(): AiModelSetting {
  return {
    settingKey: '',
    providerName: '',
    baseUrl: '',
    apiKey: '',
    modelName: '',
    modelPurpose: 'LANGUAGE',
    vectorChunkSize: 500,
    vectorChunkOverlap: 100,
    supportImageFlag: false,
    promptTemplate: '',
    enabledFlag: true,
  }
}

function openDialog(row?: AiModelSetting) {
  form.value = row
    ? {
        id: row.id,
        settingKey: row.settingKey,
        providerName: row.providerName,
        baseUrl: row.baseUrl || '',
        apiKey: row.apiKey || '',
        modelName: row.modelName,
        modelPurpose: modelPurpose(row),
        vectorChunkSize: row.vectorChunkSize || 500,
        vectorChunkOverlap: row.vectorChunkOverlap ?? 100,
        supportImageFlag: row.supportImageFlag,
        promptTemplate: row.promptTemplate || '',
        enabledFlag: row.enabledFlag,
        createdAt: row.createdAt,
        updatedAt: row.updatedAt,
      }
    : emptyForm()
  dialogVisible.value = true
}

async function handleSave() {
  const payload = normalizeForm()
  if (!payload) {
    return
  }
  saving.value = true
  try {
    await saveAiSetting(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

function normalizeForm(): AiModelSetting | null {
  const settingKey = form.value.settingKey.trim()
  const providerName = form.value.providerName.trim()
  const modelName = form.value.modelName.trim()
  const purpose = form.value.modelPurpose || 'LANGUAGE'
  if (!settingKey || !providerName || !modelName || !purpose) {
    ElMessage.warning('配置键、服务商、模型名称和模型用途不能为空')
    return null
  }
  const chunkSize = purpose === 'VECTOR' ? Math.max(100, Math.min(form.value.vectorChunkSize || 500, 8000)) : 500
  const chunkOverlap = purpose === 'VECTOR'
    ? Math.max(0, Math.min(form.value.vectorChunkOverlap ?? 100, chunkSize - 1))
    : 100
  return {
    id: form.value.id,
    settingKey,
    providerName,
    baseUrl: trimToUndefined(form.value.baseUrl),
    apiKey: trimToUndefined(form.value.apiKey),
    modelName,
    modelPurpose: purpose,
    vectorChunkSize: chunkSize,
    vectorChunkOverlap: chunkOverlap,
    supportImageFlag: purpose === 'IMAGE',
    promptTemplate: trimToUndefined(form.value.promptTemplate),
    enabledFlag: form.value.enabledFlag,
  }
}

function modelPurpose(row: AiModelSetting) {
  if (row.modelPurpose) {
    return row.modelPurpose
  }
  return row.supportImageFlag ? 'IMAGE' : 'LANGUAGE'
}

function trimToUndefined(value?: string) {
  const trimmed = value?.trim()
  return trimmed || undefined
}
</script>
