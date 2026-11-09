<script setup lang="ts">
import { computed } from 'vue'
import ToolChips from './ToolChips.vue'
import { renderMarkdown } from '@/utils/markdown'
import type { DisplayMessage } from '@/state/useConsole'

const props = defineProps<{ message: DisplayMessage }>()

// assistant 走 Markdown 渲染（消毒后 v-html）；user 消息保持纯文本插值，天然转义防注入
const assistantHtml = computed(() =>
  props.message.role === 'assistant' && props.message.text ? renderMarkdown(props.message.text) : '',
)
</script>

<template>
  <article class="message" :class="`message-${message.role}`">
    <div class="message-role">{{ message.role === 'user' ? '你' : 'Agent' }}</div>
    <div v-if="message.role === 'assistant'" class="message-text markdown-body" v-html="assistantHtml"></div>
    <div v-else class="message-text">{{ message.text }}</div>
    <ToolChips v-if="message.tools.length > 0" :tools="message.tools" />
  </article>
</template>
