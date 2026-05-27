<template>
  <div class="admin-page">
    <PageHeader title="项目列表">
      <el-button type="primary" @click="$router.push({ name: 'project-create' })">新建项目</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="项目编码 / 名称 / 技术栈" />
        </el-form-item>
        <el-form-item label="项目状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in projectStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="page = 1">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>项目数据</strong>
          <span>共 {{ filteredList.length }} 条项目记录</span>
        </div>
      </div>
      <el-table :data="pagedList" v-loading="loading" stripe>
        <el-table-column prop="projectCode" label="项目编码" width="160" />
        <el-table-column prop="projectName" label="项目名称" />
        <el-table-column prop="techStack" label="技术栈" />
        <el-table-column prop="baseMarkdownSyncMode" label="基础 MD 同步" width="140">
          <template #default="{ row }">
            {{ baseMarkdownSyncModeLabelMap[row.baseMarkdownSyncMode] || row.baseMarkdownSyncMode }}
          </template>
        </el-table-column>
        <el-table-column prop="markdownSyncMode" label="额外 MD 冲突" width="150">
          <template #default="{ row }">
            {{ markdownSyncModeLabelMap[row.markdownSyncMode] || row.markdownSyncMode }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'project-edit', params: { projectCode: row.projectCode } })">
              编辑
            </el-button>
            <el-button link type="primary" @click="router.push({ name: 'project-markdown-config', params: { projectCode: row.projectCode } })">
              MD 管理
            </el-button>
            <el-button link type="primary" @click="router.push({ name: 'project-knowledge', query: { projectCode: row.projectCode } })">
              储备库
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="filteredList.length"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { deleteProject, fetchProjects } from '@/api/project'
import type { Project } from '@/types/project'
import { projectMarkdownBaseSyncModeOptions, projectMarkdownSyncModeOptions, projectStatusOptions } from '@/types/options'

const router = useRouter()
const list = ref<Project[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = ref({ keyword: '', status: '' })
const markdownSyncModeLabelMap = Object.fromEntries(projectMarkdownSyncModeOptions.map((item) => [item.value, item.label]))
const baseMarkdownSyncModeLabelMap = Object.fromEntries(projectMarkdownBaseSyncModeOptions.map((item) => [item.value, item.label]))
const filteredList = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return list.value.filter((item) => {
    const matchesKeyword = !keyword || [item.projectCode, item.projectName, item.techStack].some((value) => value?.toLowerCase().includes(keyword))
    const matchesStatus = !filters.value.status || item.status === filters.value.status
    return matchesKeyword && matchesStatus
  })
})
const pagedList = computed(() => filteredList.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(async () => {
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    list.value = await fetchProjects()
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.value = { keyword: '', status: '' }
  page.value = 1
}

async function handleDelete(row: Project) {
  try {
    await ElMessageBox.confirm(`确定删除项目 ${row.projectName} (${row.projectCode}) 吗？`, '删除项目', {
      type: 'warning',
    })
    await deleteProject(row.projectCode)
    ElMessage.success('删除成功')
    await loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}
</script>
