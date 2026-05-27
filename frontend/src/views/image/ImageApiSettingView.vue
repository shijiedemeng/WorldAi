<template>
  <div class="admin-page image-setting-page">
    <PageHeader title="生图接口">
      <el-button :loading="loading" @click="loadList()">刷新</el-button>
      <el-button type="primary" @click="openDialog()">新增接口配置</el-button>
    </PageHeader>

    <el-card class="table-card" shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="settingKey" label="配置键" width="220" />
        <el-table-column prop="providerName" label="服务商" width="150" />
        <el-table-column prop="modelName" label="模型" width="170" />
        <el-table-column prop="baseUrl" label="接口地址" min-width="240" show-overflow-tooltip />
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
              <el-input v-model="form.settingKey" placeholder="如 image-gpt" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="服务商" required>
              <el-input v-model="form.providerName" placeholder="如 OpenAI / PackyAPI" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="模型名称" required>
              <el-input v-model="form.modelName" placeholder="gpt-image-2" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="接口地址" required>
              <el-input v-model="form.baseUrl" placeholder="https://www.packyapi.com/v1" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="API Key" required>
              <el-input v-model="form.apiKey" type="password" show-password placeholder="token 或 sk-..." />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="启用">
              <el-switch v-model="form.enabledFlag" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="基础提示词">
              <el-input v-model="form.promptTemplate" type="textarea" :rows="5" placeholder="可作为每次生图的基础要求" />
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

const loading = ref(false)
const saving = ref(false)
const list = ref<AiModelSetting[]>([])
const dialogVisible = ref(false)
const form = ref<AiModelSetting>(emptyForm())

const dialogTitle = computed(() => (form.value.id ? '编辑生图接口配置' : '新增生图接口配置'))

onMounted(async () => {
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    list.value = await fetchAiSettings({ supportImageFlag: true })
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
    modelName: 'gpt-image-2',
    supportImageFlag: true,
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
        supportImageFlag: true,
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
  const baseUrl = form.value.baseUrl?.trim()
  const apiKey = form.value.apiKey?.trim()
  if (!settingKey || !providerName || !modelName || !baseUrl || !apiKey) {
    ElMessage.warning('配置键、服务商、模型、接口地址和 API Key 不能为空')
    return null
  }
  return {
    id: form.value.id,
    settingKey,
    providerName,
    baseUrl,
    apiKey,
    modelName,
    supportImageFlag: true,
    promptTemplate: trimToUndefined(form.value.promptTemplate),
    enabledFlag: form.value.enabledFlag,
  }
}

function trimToUndefined(value?: string) {
  const trimmed = value?.trim()
  return trimmed || undefined
}
</script>
