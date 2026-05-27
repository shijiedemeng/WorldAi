<template>
  <div v-if="hasActions" class="page-header">
    <div class="page-header__actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useSlots } from 'vue'

defineProps<{
  title: string
}>()

const slots = useSlots()
const hasActions = computed(() => Boolean(slots.default?.().length))
</script>
