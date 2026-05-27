<template>
  <div class="admin-page">
    <PageHeader
      title="需求列表"
    >
      <el-button :loading="loading" @click="refreshList">刷新</el-button>
      <el-button type="primary" @click="$router.push({ name: 'requirement-create' })">新建总模块</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="需求编号 / 标题"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="需求类型">
          <el-select v-model="filters.requirementType" clearable placeholder="全部类型" class="w-full">
            <el-option v-for="opt in requirementTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in requirementStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
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
          <strong>需求数据</strong>
          <span>共 {{ filteredRows.length }} 条需求记录</span>
        </div>
      </div>
      <el-table :data="pagedRows" v-loading="loading" stripe>
        <el-table-column prop="requirementNo" label="需求编号" width="170" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="requirementType" label="类型" width="110">
          <template #default="{ row }">
            {{ requirementTypeLabelMap[row.requirementType] || row.requirementType }}
          </template>
        </el-table-column>
        <el-table-column prop="projectCode" label="项目" width="140" />
        <el-table-column prop="currentStage" label="当前阶段" width="140" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="$router.push({ name: 'requirement-detail', params: { requirementNo: row.requirementNo } })"
            >
              详情
            </el-button>
            <el-button
              link
              type="warning"
              @click="$router.push({ name: 'inspection-detail', params: { requirementNo: row.requirementNo } })"
            >
              巡检
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
          :total="filteredRows.length"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { deleteRequirement, fetchRequirementTree } from '@/api/requirement'
import { requirementStatusOptions, requirementTypeOptions } from '@/types/options'
import type { Requirement } from '@/types/requirement'

const rows = ref<Requirement[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = ref({ keyword: '', requirementType: '', status: '' })
const requirementTypeLabelMap = Object.fromEntries(requirementTypeOptions.map((item) => [item.value, item.label]))
const filteredRows = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return rows.value.filter((item) => {
    const matchesKeyword = !keyword || [item.requirementNo, item.title].some((value) => value?.toLowerCase().includes(keyword))
    const matchesType = !filters.value.requirementType || item.requirementType === filters.value.requirementType
    const matchesStatus = !filters.value.status || item.status === filters.value.status
    return matchesKeyword && matchesType && matchesStatus
  })
})
const pagedRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

onMounted(loadList)
watch(filters, () => {
  page.value = 1
}, { deep: true })

async function loadList() {
  loading.value = true
  try {
    rows.value = (await fetchRequirementTree()).map((item) => item.requirement)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.value = { keyword: '', requirementType: '', status: '' }
  page.value = 1
  void loadList()
}

async function handleSearch() {
  page.value = 1
  await loadList()
}

async function refreshList() {
  await loadList()
}

async function handleDelete(row: Requirement) {
  const message = row.requirementType === 'MASTER'
    ? `确定删除需求模块 ${row.title} (${row.requirementNo}) 吗？删除后会直接移除该模块、子模块、链路、测试记录和会话绑定。`
    : `确定删除需求 ${row.title} (${row.requirementNo}) 吗？`
  try {
    await ElMessageBox.confirm(message, '删除需求', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRequirement(row.requirementNo)
    ElMessage.success('删除成功')
    await loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}
</script>
