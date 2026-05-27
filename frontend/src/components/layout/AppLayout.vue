<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-shell__aside">
      <SideMenu />
    </el-aside>
    <el-container>
      <el-header class="app-shell__header">
        <TopBar />
      </el-header>
      <el-main class="app-shell__main">
        <RouterView v-slot="{ Component, route }">
          <KeepAlive :max="50">
            <component :is="Component" :key="appStore.getRouteCacheKey(route)" />
          </KeepAlive>
        </RouterView>
      </el-main>
    </el-container>
    <ConsolePushNotifications />
  </el-container>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'
import ConsolePushNotifications from '@/components/common/ConsolePushNotifications.vue'
import { useAppStore } from '@/stores/app'
import { addConsolePushListener, useConsolePush } from '@/composables/useConsolePush'

const appStore = useAppStore()
let removeConsolePushListener: (() => void) | undefined

useConsolePush()

onMounted(() => {
  removeConsolePushListener = addConsolePushListener(appStore.recordConsolePush)
})

onBeforeUnmount(() => {
  removeConsolePushListener?.()
})
</script>
