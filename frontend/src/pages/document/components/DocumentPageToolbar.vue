<script setup lang="ts">
import { computed } from 'vue'
import type { VisibleGroup } from '../../../stores/app'

interface SelectOption {
  value: string
  label: string
}

const emit = defineEmits<{
  'change:groupId': [groupId: number | null]
  'change:fileName': [value: string]
  'change:status': [value: string]
  'refresh-groups': []
  'file-change': [file: File | null]
  'upload': []
}>()

const props = defineProps<{
  groups: VisibleGroup[]
  currentGroupId: number | null
  currentGroup: VisibleGroup | null
  isGroupsLoading: boolean
  fileName: string
  status: string
  statusOptions: ReadonlyArray<SelectOption>
  selectedFileName: string
  fileInputKey: number
  canManageCurrentGroup: boolean
  isUploading: boolean
}>()

const ownedGroups = computed(() => props.groups.filter((group) => group.relation === 'OWNER'))
const joinedGroups = computed(() => props.groups.filter((group) => group.relation === 'MEMBER'))

function handleGroupChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('change:groupId', value ? Number(value) : null)
}

function handleFileChange(event: Event) {
  emit('file-change', (event.target as HTMLInputElement).files?.[0] ?? null)
}
</script>

<template>
  <section class="document-toolbar">
    <div class="document-toolbar__primary">
      <label class="document-toolbar__field document-toolbar__field--group">
        <span>{{ $t('qa.knowledgeBase') }}</span>
        <div class="document-toolbar__select-wrap">
          <select :value="props.currentGroupId ?? ''" :disabled="props.isGroupsLoading" @change="handleGroupChange">
            <option value="">{{ $t('documents.selectKnowledgeBase') }}</option>
            <optgroup v-if="ownedGroups.length > 0" :label="$t('documents.ownedOption')">
              <option v-for="group in ownedGroups" :key="`owner-${group.groupId}`" :value="group.groupId">
                {{ group.groupName }} · {{ $t('common.owner') }}
              </option>
            </optgroup>
            <optgroup v-if="joinedGroups.length > 0" :label="$t('documents.joinedOption')">
              <option v-for="group in joinedGroups" :key="`member-${group.groupId}`" :value="group.groupId">
                {{ group.groupName }} · {{ $t('common.member') }}
              </option>
            </optgroup>
          </select>
          <button type="button" class="ghost-button" :disabled="props.isGroupsLoading" @click="emit('refresh-groups')">
            {{ props.isGroupsLoading ? $t('qa.syncing') : $t('common.refresh') }}
          </button>
        </div>
      </label>

      <label class="document-toolbar__field">
        <span>{{ $t('documents.fileName') }}</span>
        <input
          :value="props.fileName"
          type="search"
          maxlength="128"
          :placeholder="$t('documents.searchPlaceholder')"
          @input="emit('change:fileName', ($event.target as HTMLInputElement).value)"
        />
      </label>

      <label class="document-toolbar__field">
        <span>{{ $t('common.status') }}</span>
        <select :value="props.status" @change="emit('change:status', ($event.target as HTMLSelectElement).value)">
          <option v-for="option in props.statusOptions" :key="`status-${option.value || 'all'}`" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <div v-if="props.currentGroup && props.canManageCurrentGroup" class="document-toolbar__upload">
      <div class="document-toolbar__upload-copy">
        <span class="document-toolbar__label">{{ $t('documents.upload') }}</span>
        <strong>{{ props.selectedFileName }}</strong>
        <p>{{ $t('documents.uploadDescription') }}</p>
      </div>

      <div class="document-toolbar__upload-actions">
        <label class="document-toolbar__upload-picker">
          <input :key="fileInputKey" type="file" @change="handleFileChange" />
          <span>{{ $t('documents.chooseFileButton') }}</span>
        </label>
        <button type="button" class="primary-button" :disabled="props.isUploading" @click="emit('upload')">
          {{ props.isUploading ? $t('documents.uploading') : $t('documents.upload') }}
        </button>
      </div>
    </div>

    <div v-else-if="props.currentGroup" class="document-toolbar__readonly">
      <p class="filter-hint">{{ $t('documents.readOnlyGroup', { name: props.currentGroup.groupName }) }}</p>
    </div>
  </section>
</template>
