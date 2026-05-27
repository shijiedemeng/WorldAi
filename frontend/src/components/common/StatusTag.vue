<template>
  <el-tag :type="tagType">{{ value || '-' }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: string
}>()

const tagType = computed(() => {
  switch (props.value) {
    case 'DONE':
    case 'PASS':
    case 'ONLINE':
    case 'ENABLED':
    case 'SUCCESS':
    case 'PUSHED':
      return 'success'
    case 'IN_PROGRESS':
    case 'ANALYZING':
    case 'DOING':
    case 'SYNCING':
    case 'ANALYZED':
    case 'RUNNING':
      return 'primary'
    case 'TESTING':
    case 'BLOCKED':
    case 'FAIL':
    case 'DISABLED':
    case 'FAILED':
      return 'danger'
    case 'SKIPPED':
    case 'FALLBACK':
      return 'warning'
    default:
      return 'info'
  }
})
</script>
