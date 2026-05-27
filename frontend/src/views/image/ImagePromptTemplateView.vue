<template>
  <div class="admin-page image-prompt-page">
    <PageHeader title="提示词模板">
      <el-button :loading="loading" @click="loadList()">刷新</el-button>
      <el-button type="primary" @click="openDialog()">新增模板</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="模板编码 / 名称 / 内容" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.templateType" clearable class="w-full" placeholder="全部类型">
            <el-option v-for="item in imagePromptTemplateTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.enabledFlag" clearable class="w-full" placeholder="全部状态">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        :data="list"
        stripe
        v-loading="loading"
        row-key="templateCode"
        empty-text="暂无提示词模板"
        @row-dblclick="openDialog"
      >
        <el-table-column prop="templateCode" label="模板编码" min-width="170" show-overflow-tooltip />
        <el-table-column prop="templateName" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.templateType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabledFlag ? 'success' : 'info'" size="small">{{ row.enabledFlag ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column prop="contentText" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadList"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="模板编码" required>
          <el-input v-model="form.templateCode" :disabled="isEdit" placeholder="如 warm-illustration-style" />
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="form.templateName" placeholder="如 温暖插画风格" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.templateType" class="w-full" placeholder="选择模板类型">
            <el-option v-for="item in imagePromptTemplateTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.contentText" type="textarea" :rows="8" placeholder="填写可复用提示词片段" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="启用">
              <el-switch v-model="form.enabledFlag" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  deleteImagePromptTemplate,
  fetchImagePromptTemplate,
  fetchImagePromptTemplatePage,
  saveImagePromptTemplate,
} from '@/api/image'
import {
  imagePromptTemplateTypeLabelMap,
  imagePromptTemplateTypeOptions,
} from '@/types/options'
import type {
  ImagePromptTemplate,
  ImagePromptTemplateQuery,
  ImagePromptTemplateType,
  SaveImagePromptTemplatePayload,
} from '@/types/image'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const list = ref<ImagePromptTemplate[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selected = ref<ImagePromptTemplate | null>(null)
const filters = ref<ImagePromptTemplateQuery>(emptyFilters())
const form = ref<SaveImagePromptTemplatePayload>(emptyForm())

const isEdit = computed(() => Boolean(selected.value))
const dialogTitle = computed(() => (isEdit.value ? `编辑模板 - ${selected.value?.templateCode}` : '新增提示词模板'))

onMounted(async () => {
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    const result = await fetchImagePromptTemplatePage({
      ...normalizeFilters(filters.value),
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  page.value = 1
  await loadList()
}

async function resetFilters() {
  filters.value = emptyFilters()
  page.value = 1
  await loadList()
}

async function handlePageSizeChange(nextPageSize: number) {
  pageSize.value = nextPageSize
  page.value = 1
  await loadList()
}

async function openDialog(row?: ImagePromptTemplate) {
  if (!row) {
    selected.value = null
    form.value = emptyForm()
    dialogVisible.value = true
    return
  }
  loading.value = true
  try {
    const detail = await fetchImagePromptTemplate(row.templateCode)
    selected.value = detail
    form.value = {
      templateCode: detail.templateCode,
      templateName: detail.templateName,
      templateType: detail.templateType,
      contentText: detail.contentText,
      enabledFlag: detail.enabledFlag,
      sortOrder: detail.sortOrder,
    }
    dialogVisible.value = true
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const payload = normalizeForm()
  if (!payload) {
    return
  }
  saving.value = true
  try {
    await saveImagePromptTemplate(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: ImagePromptTemplate) {
  await ElMessageBox.confirm(`确认删除模板 ${row.templateCode}？`, '删除确认', { type: 'warning' })
  await deleteImagePromptTemplate(row.templateCode)
  ElMessage.success('删除成功')
  await loadList()
}

function normalizeForm(): SaveImagePromptTemplatePayload | null {
  const templateCode = form.value.templateCode.trim()
  const templateName = form.value.templateName.trim()
  const contentText = form.value.contentText.trim()
  if (!templateCode || !templateName || !contentText) {
    ElMessage.warning('模板编码、名称和内容不能为空')
    return null
  }
  return {
    templateCode,
    templateName,
    templateType: form.value.templateType,
    contentText,
    enabledFlag: form.value.enabledFlag,
    sortOrder: form.value.sortOrder || 0,
  }
}

function emptyFilters(): ImagePromptTemplateQuery {
  return {
    keyword: '',
    templateType: '',
    enabledFlag: undefined,
  }
}

function emptyForm(): SaveImagePromptTemplatePayload {
  return {
    templateCode: '',
    templateName: '',
    templateType: 'POSITIVE' as ImagePromptTemplateType,
    contentText: '',
    enabledFlag: true,
    sortOrder: 0,
  }
}

function normalizeFilters(value: ImagePromptTemplateQuery) {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => typeof item !== 'string' || item.trim()),
  ) as ImagePromptTemplateQuery
}

function typeLabel(value?: string) {
  return imagePromptTemplateTypeLabelMap[value || ''] || value || '-'
}
</script>
