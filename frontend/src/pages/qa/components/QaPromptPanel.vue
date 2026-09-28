<script setup lang="ts">
import { computed } from 'vue'
import type { GroupItem } from '../../../api/group'

const props = defineProps<{
  currentGroup: GroupItem | null
  currentGroupId: number | null
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
  question: string
  questionLength: number
  hasGroups: boolean
  canSubmit: boolean
  isGroupsRefreshing: boolean
  isSubmitting: boolean
  groupError: string
  currentGroupDescription: string
  currentRoleHint: string
}>()

const emit = defineEmits<{
  'update:question': [value: string]
  'refresh-groups': []
  'select-group': [groupId: number]
  submit: []
}>()

const selectableGroups = computed(() => props.ownedGroups.length + props.joinedGroups.length)

function handleGroupChange(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  if (Number.isInteger(value) && value > 0) {
    emit('select-group', value)
  }
}
</script>

<template>
  <article class="panel qa-prompt-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ $t('qa.ask') }}</p>
        <h2>{{ $t('qa.selectKnowledgeBase') }}</h2>
      </div>
      <button class="ghost-button" type="button" :disabled="isGroupsRefreshing" @click="emit('refresh-groups')">
        {{ isGroupsRefreshing ? $t('qa.syncing') : $t('qa.refreshList') }}
      </button>
    </div>

    <section class="qa-prompt-panel__workspace">
      <div class="qa-prompt-panel__workspace-copy">
        <span class="qa-prompt-panel__workspace-label">{{ $t('qa.currentScope') }}</span>
        <strong>{{ currentGroup?.groupName ?? $t('common.notSelected') }}</strong>
        <p>{{ currentGroupDescription }}</p>
        <small>{{ currentRoleHint }}</small>
      </div>
      <div class="qa-prompt-panel__workspace-status">
        <span class="panel__pill">{{ hasGroups ? $t('qa.groupRetrieval') : $t('qa.noGroups') }}</span>
        <span class="qa-prompt-panel__workspace-tip">
          {{ currentGroupId !== null ? $t('qa.scopeLocked') : $t('qa.selectKnowledgeBase') }}
        </span>
      </div>
    </section>

    <section class="qa-prompt-panel__selection-board">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">{{ $t('qa.stepOne') }}</p>
        <h3>{{ $t('qa.selectKnowledgeBase') }}</h3>
        <p>{{ $t('qa.selectionBoundary') }}</p>
      </div>

      <label class="qa-prompt-panel__scope-field">
        <span>{{ $t('qa.knowledgeBaseSpace') }}</span>
        <div class="qa-prompt-panel__scope-select-wrap">
          <select class="qa-prompt-panel__scope-select" :value="currentGroupId ?? ''" :disabled="!hasGroups" @change="handleGroupChange">
            <option value="">{{ hasGroups ? $t('qa.selectScope') : $t('qa.noGroups') }}</option>
            <optgroup v-if="ownedGroups.length > 0" :label="$t('qa.ownedKnowledgeBases')">
              <option
                v-for="group in ownedGroups"
                :key="`qa-owned-${group.groupId}`"
                :value="group.groupId"
              >
                {{ group.groupName }} · {{ $t('common.owner') }}
              </option>
            </optgroup>
            <optgroup v-if="joinedGroups.length > 0" :label="$t('qa.joinedKnowledgeBases')">
              <option
                v-for="group in joinedGroups"
                :key="`qa-joined-${group.groupId}`"
                :value="group.groupId"
              >
                {{ group.groupName }} · {{ $t('common.member') }}
              </option>
            </optgroup>
          </select>
        </div>
        <p class="qa-prompt-panel__scope-hint">
          {{ currentGroup ? $t('qa.lockedScope', { name: currentGroup.groupName }) : $t('qa.selectableCount', { count: selectableGroups }) }}
        </p>
      </label>
    </section>

    <section class="qa-prompt-panel__composer-card">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">{{ $t('qa.stepTwo') }}</p>
        <h3>{{ $t('qa.enterQuestion') }}</h3>
        <p>{{ $t('qa.specificQuestionHint') }}</p>
      </div>

      <label class="qa-prompt-panel__composer">
        <span>{{ $t('qa.enterQuestion') }}</span>
        <textarea
          :value="question"
          class="qa-question-box"
          maxlength="2000"
          :placeholder="$t('qa.questionPlaceholder')"
          @input="emit('update:question', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>

      <div class="qa-prompt-panel__footer">
        <div class="qa-prompt-panel__footer-copy">
          <span>{{ questionLength }}/2000</span>
          <p>{{ $t('qa.evidenceOnlyHint') }}</p>
        </div>
        <button class="primary-button" type="button" :disabled="isSubmitting || !canSubmit" @click="emit('submit')">
          {{ isSubmitting ? $t('qa.asking') : $t('qa.ask') }}
        </button>
      </div>
    </section>

    <section class="qa-prompt-panel__rules">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">{{ $t('qa.rules') }}</p>
        <h3>{{ $t('qa.answerBoundary') }}</h3>
      </div>
      <ul class="qa-prompt-panel__rules-list">
        <li>{{ $t('qa.ruleScope') }}</li>
        <li>{{ $t('qa.rulePermission') }}</li>
        <li>{{ $t('qa.ruleEvidence') }}</li>
      </ul>
    </section>

    <p v-if="groupError" class="feedback feedback--error">{{ groupError }}</p>
  </article>
</template>
