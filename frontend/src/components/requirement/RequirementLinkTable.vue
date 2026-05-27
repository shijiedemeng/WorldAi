<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>开发链路</span>
        <slot name="actions" />
      </div>
    </template>
    <el-table :data="pagedLinks" v-loading="loading" empty-text="暂无链路数据">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="requirementNo" label="需求编号" min-width="150" />
      <el-table-column prop="linkType" label="链路类型" width="120" />
      <el-table-column prop="taskTitle" label="任务标题" min-width="180" />
      <el-table-column prop="agentCode" label="Agent" width="140" />
      <el-table-column prop="developerName" label="负责人" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <StatusTag :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="240">
        <template #default="scope">
          <div class="requirement-link-table__summary">
            <div v-if="scope.row.resultSummary" class="requirement-link-table__summary-text">
              {{ scope.row.resultSummary }}
            </div>
            <span v-else>-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :disabled="!hasReceipt(scope.row)"
            @click="openReceipt(scope.row)"
          >
            查看回执
          </el-button>
          <slot name="row-actions" :row="scope.row" />
        </template>
      </el-table-column>
    </el-table>
    <div v-if="links.length > 0" class="requirement-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[5, 10, 20, 50]"
        :total="links.length"
      />
    </div>
  </el-card>

  <el-dialog
    v-model="receiptVisible"
    title="开发链路详情"
    width="720px"
    destroy-on-close
  >
    <template v-if="selectedLink">
      <div class="requirement-link-table__meta">
        <div><strong>需求编号：</strong>{{ selectedLink.requirementNo }}</div>
        <div><strong>链路类型：</strong>{{ selectedLink.linkType }}</div>
        <div><strong>任务标题：</strong>{{ selectedLink.taskTitle }}</div>
        <div><strong>Agent：</strong>{{ selectedLink.agentCode }}</div>
        <div><strong>状态：</strong>{{ selectedLink.status }}</div>
      </div>

      <div class="requirement-link-table__panel">
        <div class="requirement-link-table__panel-title">结果摘要</div>
        <div class="requirement-link-table__panel-content">
          {{ selectedLink.resultSummary || '-' }}
        </div>
      </div>

      <div class="requirement-link-table__panel">
        <div class="requirement-link-table__panel-title">执行回执</div>
        <div class="requirement-link-table__panel-content">
          {{ selectedLink.executionDetails || '-' }}
        </div>
      </div>

      <div class="requirement-link-table__panel">
        <div class="requirement-link-table__panel-title">交付物</div>
        <div class="requirement-link-table__panel-content">
          {{ selectedLink.deliverablePath || '-' }}
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RequirementLink } from '@/types/link'
import StatusTag from '@/components/common/StatusTag.vue'

const props = defineProps<{
  links: RequirementLink[]
  loading?: boolean
}>()

const currentPage = ref(1)
const pageSize = ref(10)
const receiptVisible = ref(false)
const selectedLink = ref<RequirementLink | null>(null)

const pagedLinks = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return props.links.slice(start, start + pageSize.value)
})

watch(
  () => props.links.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.links.length / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  },
)

function hasReceipt(link: RequirementLink) {
  return Boolean(link.resultSummary || link.executionDetails || link.deliverablePath)
}

function openReceipt(link: RequirementLink) {
  selectedLink.value = link
  receiptVisible.value = true
}
</script>

<style scoped>
.requirement-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.requirement-link-table__summary {
  white-space: pre-line;
}

.requirement-link-table__summary-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.5;
}

.requirement-link-table__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
  margin-bottom: 20px;
}

.requirement-link-table__panel {
  margin-top: 16px;
}

.requirement-link-table__panel-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.requirement-link-table__panel-content {
  white-space: pre-line;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
