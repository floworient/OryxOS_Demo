<script setup lang="ts">
import ToolChips from './ToolChips.vue'
import type { DisplayMessage } from '@/state/useConsole'

defineProps<{ message: DisplayMessage }>()
</script>

<template>
  <article class="message" :class="`message-${message.role}`">
    <div class="message-role">{{ message.role === 'user' ? '你' : 'Agent' }}</div>
    <div class="message-text">{{ message.text }}</div>
    <ToolChips v-if="message.tools.length > 0" :tools="message.tools" />
  </article>
</template>
