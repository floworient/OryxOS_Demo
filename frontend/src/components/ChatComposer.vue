<script setup lang="ts">
import { computed } from 'vue'
import { waitingText } from '@/state/useConsole'

const props = defineProps<{
  modelValue: string
  disabled: boolean
  sending: boolean
  waitingSeconds: number
  inputError: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
}>()

const waiting = computed(() => (props.sending ? waitingText(props.waitingSeconds) : ''))

function submit(): void {
  if (props.disabled || props.sending) {
    return
  }
  emit('send')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <form class="composer" @submit.prevent="submit">
    <textarea
      class="composer-input"
      rows="2"
      :value="modelValue"
      :disabled="disabled"
      placeholder="输入消息…"
      aria-label="消息输入"
      @input="onInput"
      @keydown="onKeydown"
    ></textarea>
    <div class="composer-footer">
      <span class="composer-hint" :class="{ 'is-error': inputError }">
        {{ inputError ?? (sending ? waiting : 'Enter 发送，Shift+Enter 换行') }}
      </span>
      <button type="submit" class="primary-button" :disabled="disabled || sending">
        {{ sending ? '等待中…' : '发送' }}
      </button>
    </div>
  </form>
</template>
