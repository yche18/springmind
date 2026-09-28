<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { askQuestion, type AskQuestionResponse } from '../../api/qa'
import { fetchGroups } from '../../api/group'
import { extractApiError } from '../../api/http'
import { humanizeModelErrorMessage } from '../../utils/model-error-message'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import '../../assets/page-shell.css'
import '../../assets/qa-page.css'
import QaConversationPanel from './components/QaConversationPanel.vue'
import QaPromptPanel from './components/QaPromptPanel.vue'

const appStore = useAppStore()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })
const question = ref('')
const result = ref<AskQuestionResponse | null>(null)
const askError = ref('')
const groupError = ref('')
const isSubmitting = ref(false)
const isGroupsRefreshing = ref(false)
let qaContextVersion = 0
let latestAskRequestId = 0

const currentGroupId = computed(() => appStore.currentGroupId)
const hasGroups = computed(() => appStore.visibleGroups.length > 0)
const currentGroup = computed(() => appStore.currentGroup)
const ownedGroups = computed(() => appStore.ownedGroups)
const joinedGroups = computed(() => appStore.joinedGroups)
const pendingInvitationCount = computed(() => appStore.pendingInvitations.length)
const canSubmit = computed(
  () => currentGroupId.value !== null && hasGroups.value && !appStore.isGroupsLoading,
)
const questionLength = computed(() => question.value.trim().length)
const currentRoleLabel = computed(() => {
  if (currentGroup.value?.relation === 'OWNER') return t('common.owner')
  if (currentGroup.value?.relation === 'MEMBER') return t('common.member')
  return t('common.notSelected')
})
const availableGroupCount = computed(() => ownedGroups.value.length + joinedGroups.value.length)
const currentContextKey = computed(() => `${authStore.currentUser?.userId ?? 'anonymous'}:${currentGroupId.value ?? 'none'}`)
const currentGroupDescription = computed(() => {
  if (currentGroup.value === null) {
    return t('qa.chooseFirst')
  }
  return currentGroup.value.relation === 'OWNER'
    ? t('qa.ownerScope', { name: currentGroup.value.groupName })
    : t('qa.memberScope', { name: currentGroup.value.groupName })
})
const currentRoleHint = computed(() => {
  if (currentGroup.value === null) {
    return t('qa.roleSelectionHint')
  }
  return currentGroup.value.relation === 'OWNER'
    ? t('qa.ownerHint')
    : t('qa.memberHint')
})
const pageHeroDescription = computed(() =>
  currentGroup.value === null
    ? t('qa.description')
    : t('qa.currentKnowledgeBase', { name: currentGroup.value.groupName }),
)

watch(
  () => authStore.currentUser?.userId,
  () => {
    void refreshGroups()
  },
  { immediate: true },
)

watch(
  [currentGroupId, () => appStore.isGroupsLoading],
  () => {
    qaContextVersion += 1
    latestAskRequestId += 1
    result.value = null
    askError.value = ''
    isSubmitting.value = false
  },
)

async function handleAsk() {
  const trimmedQuestion = question.value.trim()
  if (!canSubmit.value || currentGroupId.value === null) {
    askError.value = t('qa.groupRequired')
    return
  }
  if (trimmedQuestion.length === 0) {
    askError.value = t('qa.questionRequired')
    return
  }
  const contextVersion = qaContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestAskRequestId
  isSubmitting.value = true
  askError.value = ''
  try {
    const nextResult = await askQuestion({
      groupId: currentGroupId.value,
      question: trimmedQuestion,
    })
    if (!isActiveAskRequest(contextVersion, contextKey, requestId)) return
    result.value = nextResult
  } catch (error) {
    if (!isActiveAskRequest(contextVersion, contextKey, requestId)) return
    result.value = null
    askError.value = humanizeModelErrorMessage(
      extractApiError(error, t('errors.askQuestion')),
      t('errors.askQuestion'),
    )
  } finally {
    if (isActiveAskRequest(contextVersion, contextKey, requestId)) {
      isSubmitting.value = false
    }
  }
}

async function refreshGroups() {
  isGroupsRefreshing.value = true
  groupError.value = ''
  try {
    const groupQueryResult = await fetchGroups()
    appStore.applyGroupQueryResult(groupQueryResult)
  } catch (error) {
    appStore.resetGroupContext(false)
    groupError.value = extractApiError(error, t('qa.groupScopeLoadFailed'))
  } finally {
    isGroupsRefreshing.value = false
  }
}

function selectGroup(groupId: number) {
  if (groupId === appStore.currentGroupId) {
    return
  }
  appStore.setCurrentGroupId(groupId)
}

function isActiveAskRequest(contextVersion: number, contextKey: string, requestId: number) {
  return (
    contextVersion === qaContextVersion &&
    contextKey === currentContextKey.value &&
    requestId === latestAskRequestId &&
    !appStore.isGroupsLoading
  )
}
</script>

<template>
  <WorkbenchShell class="page-shell--qa">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="qa-page">
        <PageHeaderHero :eyebrow="$t('qa.eyebrow')" :title="$t('qa.title')" :description="pageHeroDescription">
          <template #actions>
            <div class="qa-page__hero-actions">
              <div class="qa-page__hero-context">
                <span>{{ $t('qa.knowledgeBase') }}</span>
                <strong>{{ currentGroup?.groupName ?? $t('common.notSelected') }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>{{ $t('qa.role') }}</span>
                <strong>{{ currentRoleLabel }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>{{ $t('qa.visibleGroups') }}</span>
                <strong>{{ availableGroupCount }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>{{ $t('qa.invitations') }}</span>
                <strong>{{ pendingInvitationCount }}</strong>
              </div>
            </div>
          </template>
        </PageHeaderHero>

        <section class="qa-page__layout">
          <QaPromptPanel
            :current-group="currentGroup"
            :owned-groups="ownedGroups"
            :joined-groups="joinedGroups"
            :current-group-id="currentGroupId"
            :question="question"
            :question-length="questionLength"
            :has-groups="hasGroups"
            :is-submitting="isSubmitting"
            :can-submit="canSubmit"
            :is-groups-refreshing="isGroupsRefreshing"
            :group-error="groupError"
            :current-group-description="currentGroupDescription"
            :current-role-hint="currentRoleHint"
            @update:question="question = $event"
            @refresh-groups="refreshGroups"
            @select-group="selectGroup"
            @submit="handleAsk"
          />

          <QaConversationPanel
            :current-group-name="currentGroup?.groupName ?? $t('qa.noKnowledgeBase')"
            :current-question="question"
            :result="result"
            :ask-error="askError"
            :is-groups-loading="appStore.isGroupsLoading"
            :current-group-id="currentGroupId"
            :is-submitting="isSubmitting"
          />
        </section>
      </main>
    </template>
  </WorkbenchShell>
</template>
