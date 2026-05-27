<template>
  <div class="top-bar">
    <div class="top-bar__tabs">
      <el-scrollbar>
        <div class="top-bar__tab-list">
          <button
            v-for="tab in appStore.tabs"
            :key="tab.key"
            type="button"
            class="top-bar__tab"
            :class="{ 'is-active': tab.key === appStore.activeTabKey }"
            @click="appStore.openTab(tab, router)"
          >
            <span>{{ tab.title }}</span>
            <span
              v-if="tab.closable"
              class="top-bar__tab-close"
              @click.stop="appStore.closeTab(tab.key, router)"
            >
              x
            </span>
          </button>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const appStore = useAppStore()
</script>
