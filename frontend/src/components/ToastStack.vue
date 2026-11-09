<script setup lang="ts">
import type { Toast } from '@/state/useConsole'

defineProps<{ toasts: Toast[] }>()

defineEmits<{ close: [id: number] }>()
</script>

<template>
  <div class="toast-stack" role="status" aria-live="polite">
    <div v-for="toast in toasts" :key="toast.id" class="toast" :class="toast.kind">
      <span class="toast-icon" aria-hidden="true">{{ toast.kind === 'success' ? '✓' : '✕' }}</span>
      <span class="toast-message">{{ toast.message }}</span>
      <button type="button" class="toast-close" aria-label="关闭通知"
        @click="$emit('close', toast.id)">×</button>
    </div>
  </div>
</template>
