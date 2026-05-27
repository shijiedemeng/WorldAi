<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>测试记录</span>
        <slot name="actions" />
      </div>
    </template>
    <el-table :data="pagedRecords" v-loading="loading" empty-text="暂无测试记录">
      <el-table-column prop="testType" label="测试类型" width="120" />
      <el-table-column prop="testTitle" label="测试标题" min-width="180" />
      <el-table-column prop="testerName" label="测试人" width="120" />
      <el-table-column label="结果" width="100">
        <template #default="scope">
          <StatusTag :value="scope.row.testResult" />
        </template>
      </el-table-column>
      <el-table-column prop="bugCount" label="缺陷数" width="90" />
      <el-table-column prop="riskDesc" label="风险" min-width="180" />
      <el-table-column prop="testedAt" label="测试时间" min-width="180" />
    </el-table>
    <div v-if="records.length > 0" class="requirement-table__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[5, 10, 20, 50]"
        :total="records.length"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TestRecord } from '@/types/testRecord'
import StatusTag from '@/components/common/StatusTag.vue'

const props = defineProps<{
  records: TestRecord[]
  loading?: boolean
}>()

const currentPage = ref(1)
const pageSize = ref(10)

const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return props.records.slice(start, start + pageSize.value)
})

watch(
  () => props.records.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.records.length / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  },
)
</script>

<style scoped>
.requirement-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
