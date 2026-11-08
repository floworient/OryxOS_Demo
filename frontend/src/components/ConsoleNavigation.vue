<script setup lang="ts">
import { consoleSections, type ConsoleSection } from './consoleSections'

defineProps<{ active: ConsoleSection }>()
defineEmits<{ select: [section: ConsoleSection] }>()
</script>

<template>
  <nav class="console-navigation" aria-label="控制台导航">
    <button v-for="section in consoleSections" :key="section.key" type="button"
      :data-section="section.key" :aria-current="active === section.key ? 'page' : undefined"
      @click="$emit('select', section.key)">
      {{ section.label }}
    </button>
  </nav>
</template>
