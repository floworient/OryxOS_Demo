<script setup lang="ts">
import type { RuntimeHealth } from '@/state/useConsole'
import logoSvg from '@/assets/oryxos-logo.svg'

defineProps<{
  health: RuntimeHealth
  profileCount: number
  toolCount: number
  providers: string[]
}>()
</script>

<template>
  <header class="status-bar">
    <div class="status-brand">
      <img :src="logoSvg" alt="OryxOS" class="brand-logo" />
      <span>OryxOS 工作台</span>
    </div>
    <div class="status-meta">
      <span class="status-indicator" :class="`status-${health.toLowerCase()}`">
        <span class="status-dot" aria-hidden="true"></span>
        {{ health === 'UP' ? '健康' : health === 'DOWN' ? '状态异常' : '状态未知' }}
      </span>
      <span class="status-sep" aria-hidden="true">·</span>
      <span>Agents {{ profileCount }}</span>
      <span class="status-sep" aria-hidden="true">·</span>
      <span>Tools {{ toolCount }}</span>
      <span class="status-sep" aria-hidden="true">·</span>
      <span>Provider {{ providers.length > 0 ? providers.join(', ') : '未配置' }}</span>
    </div>
  </header>
</template>
