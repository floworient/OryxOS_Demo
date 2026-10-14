<template>
  <DefaultTheme.Layout>
    <template #nav-bar-content-after>
      <MobileNavControls />
    </template>
    <template #doc-after v-if="frontmatter.layout !== 'home'">
    </template>
  </DefaultTheme.Layout>
</template>

<script setup>
import DefaultTheme from 'vitepress/theme'
import { useData } from 'vitepress'
import MobileNavControls from './MobileNavControls.vue'

const { frontmatter } = useData()
</script>
