<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
}>()

defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <div v-if="open" class="dialog-overlay" @click.self="$emit('cancel')">
    <div class="dialog-card" role="dialog" aria-modal="true" :aria-label="title">
      <h3 class="dialog-title">{{ title }}</h3>
      <p v-if="description" class="dialog-description">{{ description }}</p>
      <div class="dialog-actions">
        <button type="button" class="ghost-button" @click="$emit('cancel')">
          {{ cancelLabel ?? '取消' }}
        </button>
        <button type="button" class="dialog-confirm" :class="{ danger }" @click="$emit('confirm')">
          {{ confirmLabel ?? '确认' }}
        </button>
      </div>
    </div>
  </div>
</template>
