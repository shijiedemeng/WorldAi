<template>
  <div v-loading="loading">
    <PageHeader title="需求巡检" />
    <template v-if="inspection">
      <InspectionSummaryCard :inspection="inspection" />
    </template>
    <el-empty v-else-if="!loading" description="暂无巡检数据" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import InspectionSummaryCard from '@/components/requirement/InspectionSummaryCard.vue'
import { fetchRequirementInspection } from '@/api/inspection'
import type { RequirementInspection } from '@/types/inspection'

const props = defineProps<{ requirementNo: string }>()
const inspection = ref<RequirementInspection | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    inspection.value = await fetchRequirementInspection(props.requirementNo)
  } finally {
    loading.value = false
  }
})
</script>
