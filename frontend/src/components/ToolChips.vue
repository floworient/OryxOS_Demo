<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ tools: string[] }>()

const uniqueTools = computed(() => Array.from(new Set(props.tools.filter((tool) => tool.length > 0))))
</script>

<template>
  <div v-if="uniqueTools.length > 0" class="tool-chips" aria-label="本轮工具调用">
    <span v-for="tool in uniqueTools" :key="tool" class="tool-chip">⚙ {{ tool }}</span>
  </div>
</template>
