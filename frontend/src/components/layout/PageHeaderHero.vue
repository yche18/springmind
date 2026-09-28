<script setup lang="ts">
interface Props {
  eyebrow?: string
  title: string
  description?: string
}

withDefaults(defineProps<Props>(), { eyebrow: '', description: '' })
</script>

<template>
  <header class="page-header-hero">
    <div class="page-header-hero__body">
      <span class="page-header-hero__eyebrow">{{ eyebrow || $t('nav.workbench') }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>

    <div v-if="$slots.actions" class="page-header-hero__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
