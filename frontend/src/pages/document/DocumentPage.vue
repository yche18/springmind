<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  deleteDocument,
  fetchDocumentPreview,
  fetchDocuments,
  retryDocumentIngestion,
  type DocumentItem,
} from '../../api/document'
import { fetchGroups } from '../../api/group'
import { extractApiError } from '../../api/http'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import {
  getDocumentStatusOptions,
  calculateTotalDocumentSize,
  canPreviewDocument,
  collectRecentDocumentFailures,
  createDocumentFilterForm,
  createDocumentStatusSummary,
  formatDocumentDateTime,
  formatDocumentFileSize,
  formatGroupRelationLabel,
  formatUploaderLabel,
  getDocumentStatusMeta,
  getPreviewButtonLabel,
  matchesDocumentFilters,
  truncatePreviewText,
} from './documentPageView'
import '../../assets/page-shell.css'
import '../../assets/document-page.css'
import DocumentPageToolbar from './components/DocumentPageToolbar.vue'
import DocumentStatusBoard from './components/DocumentStatusBoard.vue'
import { uploadDocumentWithResume, type UploadStage } from './documentUpload'

const appStore = useAppStore()
const authStore = useAuthStore()
const { t } = useI18n({ useScope: 'global' })

const documents = ref<DocumentItem[]>([])
const filters = reactive(createDocumentFilterForm())
const selectedFile = ref<File | null>(null)
const fileInputKey = ref(0)
const groupLoadError = ref('')
const documentsError = ref('')
const uploadFeedback = ref('')
const uploadError = ref('')
const isLoading = ref(false)
const isUploading = ref(false)
const uploadProgress = ref(0)
const uploadStage = ref<UploadStage | 'idle'>('idle')
const deletingDocumentIds = ref<Set<number>>(new Set())
const retryingDocumentIds = ref<Set<number>>(new Set())
const isPreviewOpen = ref(false)
const isPreviewLoading = ref(false)
const previewDocumentId = ref<number | null>(null)
const previewFileName = ref('')
const previewStatus = ref('')
const previewText = ref('')
const previewMessage = ref('')
const previewMessageTone = ref<'note' | 'error'>('note')
const isPollingDocuments = ref(false)

let documentContextVersion = 0
let latestLoadRequestId = 0
let latestPreviewRequestId = 0
let latestGroupRequestToken = 0
let pollingTimer: number | null = null

const DOCUMENT_POLL_INTERVAL_MS = 4000

const visibleGroups = computed(() => appStore.visibleGroups)
const selectedFileName = computed(() => selectedFile.value?.name ?? t('documents.noFileSelected'))
const currentGroup = computed(() => appStore.currentGroup)
const currentGroupId = computed(() => currentGroup.value?.groupId ?? null)
const currentGroupRelation = computed(() => currentGroup.value?.relation ?? null)
const canLoadDocuments = computed(() => currentGroupId.value !== null && !appStore.isGroupsLoading)
const canManageCurrentGroup = computed(() => canLoadDocuments.value && appStore.canManageCurrentGroup)
const visibleDocuments = computed(() =>
  documents.value.filter((item) => matchesDocumentFilters(item, filters, currentGroupRelation.value)),
)
const totalSize = computed(() => calculateTotalDocumentSize(visibleDocuments.value))
const documentStatusOptions = computed(() => getDocumentStatusOptions())
const statusSummaryItems = computed(() =>
  createDocumentStatusSummary(visibleDocuments.value, totalSize.value),
)
const recentFailures = computed(() => collectRecentDocumentFailures(visibleDocuments.value))
const hasPendingDocuments = computed(() =>
  documents.value.some((item) => item.status === 'PROCESSING' || item.status === 'UPLOADED'),
)
const currentContextKey = computed(
  () => `${authStore.currentUser?.userId ?? 'anonymous'}:${currentGroupId.value ?? 'none'}`,
)
const pageHeroDescription = computed(() =>
  currentGroup.value === null
    ? t('documents.descriptionEmpty')
    : t('documents.currentKnowledgeBase', { name: currentGroup.value.groupName }),
)
const groupScopeSummary = computed(() => {
  if (currentGroup.value === null) {
    return t('documents.groupCounts', { owned: appStore.ownedGroups.length, joined: appStore.joinedGroups.length })
  }

  return t('documents.groupMeta', { id: currentGroup.value.groupId, relation: formatGroupRelationLabel(currentGroup.value.relation) })
})
const filterHint = computed(() => {
  if (currentGroup.value === null) {
    return t('documents.filterFirst')
  }

  return t('documents.filterHint')
})
const resultsSummary = computed(() => {
  if (documents.value.length === visibleDocuments.value.length) {
    return t('documents.totalFiles', { count: visibleDocuments.value.length })
  }

  return t('documents.matchedFiles', { total: documents.value.length, matched: visibleDocuments.value.length })
})
const emptyStateMessage = computed(() => {
  if (documents.value.length === 0) {
    return canManageCurrentGroup.value
      ? t('documents.noFiles')
      : t('documents.noReadableFiles')
  }

  return t('documents.noMatches')
})

watch(
  () => authStore.currentUser?.userId,
  () => {
    void refreshGroups()
  },
  { immediate: true },
)

watch(
  [() => authStore.currentUser?.userId, currentGroupId, () => appStore.isGroupsLoading],
  () => {
    documentContextVersion += 1
    latestLoadRequestId += 1
    latestPreviewRequestId += 1
    resetPageForContextChange()
    syncDefaultFilters()
    if (canLoadDocuments.value) {
      void loadDocuments()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopDocumentPolling()
})

async function refreshGroups() {
  const currentToken = ++latestGroupRequestToken
  appStore.setGroupsLoading(true)
  groupLoadError.value = ''

  try {
    const result = await fetchGroups()
    if (currentToken !== latestGroupRequestToken) {
      return
    }
    appStore.applyGroupQueryResult(result)
  } catch (error) {
    if (currentToken !== latestGroupRequestToken) {
      return
    }
    appStore.resetGroupContext(false)
    groupLoadError.value = extractApiError(error, t('errors.loadGroups'))
  } finally {
    if (currentToken === latestGroupRequestToken) {
      appStore.setGroupsLoading(false)
    }
  }
}

function resetPageForContextChange() {
  stopDocumentPolling()
  documents.value = []
  isLoading.value = false
  isPollingDocuments.value = false
  isUploading.value = false
  uploadProgress.value = 0
  uploadStage.value = 'idle'
  deletingDocumentIds.value = new Set()
  retryingDocumentIds.value = new Set()
  documentsError.value = ''
  uploadFeedback.value = ''
  uploadError.value = ''
  resetSelectedFile()
  closePreview()
}

function syncDefaultFilters() {
  Object.assign(
    filters,
    createDocumentFilterForm({
      groupId: currentGroupId.value,
      relation: currentGroupRelation.value,
    }),
  )
}

async function loadDocuments(options: { silent?: boolean } = {}) {
  if (!canLoadDocuments.value || currentGroupId.value === null) {
    stopDocumentPolling()
    documents.value = []
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestLoadRequestId
  clearDocumentPollingTimer()
  if (options.silent) {
    isPollingDocuments.value = true
  } else {
    isLoading.value = true
  }
  documentsError.value = ''

  try {
    const previousDocuments = documents.value
    const nextDocuments = await fetchDocuments({
      groupId: filters.groupId ?? currentGroupId.value ?? undefined,
      fileName: filters.fileName.trim() || undefined,
      status: filters.status || undefined,
      uploadedFrom: filters.uploadedFrom || undefined,
      uploadedTo: filters.uploadedTo || undefined,
    })
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      documents.value = nextDocuments
      if (options.silent) {
        syncPollingFeedback(previousDocuments, nextDocuments)
      }
    }
  } catch (error) {
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      documents.value = []
      documentsError.value = extractApiError(error, t('errors.loadDocuments'))
    }
  } finally {
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      if (options.silent) {
        isPollingDocuments.value = false
      } else {
        isLoading.value = false
      }
      syncDocumentPolling()
    }
  }
}

async function handleRefreshDocuments() {
  if (!canLoadDocuments.value) {
    return
  }
  await loadDocuments()
}

function handleGroupChange(groupId: number | null) {
  appStore.setCurrentGroupId(groupId)
}

function handleFileChange(file: File | null) {
  selectedFile.value = file
  uploadFeedback.value = ''
  uploadError.value = ''
}

function resetSelectedFile() {
  selectedFile.value = null
  fileInputKey.value += 1
}

async function handleApplyFilters() {
  if (!canLoadDocuments.value) {
    return
  }

  if (filters.uploadedFrom && filters.uploadedTo && filters.uploadedFrom > filters.uploadedTo) {
    documentsError.value = t('documents.invalidDateRange')
    return
  }

  await loadDocuments()
}

function handleResetFilters() {
  syncDefaultFilters()
  documentsError.value = ''
  if (canLoadDocuments.value) {
    void loadDocuments()
  }
}

async function handleUpload() {
  if (!canManageCurrentGroup.value || currentGroupId.value === null) {
    uploadError.value = t('documents.ownerOnly')
    return
  }

  if (selectedFile.value === null) {
    uploadError.value = t('documents.chooseFile')
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  isUploading.value = true
  uploadProgress.value = 0
  uploadStage.value = 'hashing'
  uploadFeedback.value = ''
  uploadError.value = ''

  try {
    const documentId = await uploadDocumentWithResume(
      currentGroupId.value,
      selectedFile.value,
      (payload) => {
        if (isCurrentDocumentContext(contextVersion, contextKey)) {
          uploadProgress.value = payload.percent
          uploadStage.value = payload.stage
        }
      },
    )
    if (!isCurrentDocumentContext(contextVersion, contextKey)) {
      return
    }
    uploadFeedback.value = t('documents.uploadSubmitted', { id: documentId })
    resetSelectedFile()
    await loadDocuments()
  } catch (error) {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      uploadError.value = extractApiError(error, t('errors.uploadDocument'))
    }
  } finally {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      isUploading.value = false
      uploadStage.value = 'idle'
    }
  }
}

async function handleDelete(documentId: number, fileName: string) {
  if (!canManageCurrentGroup.value || currentGroupId.value === null) {
    return
  }

  if (!window.confirm(t('documents.confirmDelete', { name: fileName }))) {
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  deletingDocumentIds.value = new Set(deletingDocumentIds.value).add(documentId)
  documentsError.value = ''

  try {
    await deleteDocument(documentId, currentGroupId.value)
    if (!isCurrentDocumentContext(contextVersion, contextKey)) {
      return
    }
    uploadFeedback.value = t('documents.deleted', { name: fileName })
    await loadDocuments()
  } catch (error) {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      documentsError.value = extractApiError(error, t('errors.deleteDocument'))
    }
  } finally {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      const nextDeletingIds = new Set(deletingDocumentIds.value)
      nextDeletingIds.delete(documentId)
      deletingDocumentIds.value = nextDeletingIds
    }
  }
}

async function handleRetryIngestion(item: DocumentItem) {
  if (!canManageCurrentGroup.value || currentGroupId.value === null || item.status !== 'FAILED') {
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  retryingDocumentIds.value = new Set(retryingDocumentIds.value).add(item.documentId)
  documentsError.value = ''

  try {
    await retryDocumentIngestion(item.documentId, currentGroupId.value)
    if (!isCurrentDocumentContext(contextVersion, contextKey)) {
      return
    }
    uploadFeedback.value = t('documents.retryQueued', { name: item.fileName })
    await loadDocuments()
  } catch (error) {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      documentsError.value = extractApiError(error, t('errors.retryDocument'))
    }
  } finally {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      const nextRetryingIds = new Set(retryingDocumentIds.value)
      nextRetryingIds.delete(item.documentId)
      retryingDocumentIds.value = nextRetryingIds
    }
  }
}

async function handlePreview(item: DocumentItem) {
  if (currentGroupId.value === null || !canPreviewDocument(item, currentGroupRelation.value)) {
    return
  }

  const cachedPreview = truncatePreviewText(item.previewText)
  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestPreviewRequestId

  previewDocumentId.value = item.documentId
  previewFileName.value = item.fileName
  previewStatus.value = item.status
  previewText.value = cachedPreview
  previewMessage.value = cachedPreview ? t('documents.latestPreview') : ''
  previewMessageTone.value = 'note'
  isPreviewOpen.value = true
  isPreviewLoading.value = true

  try {
    const preview = await fetchDocumentPreview(item.documentId, currentGroupId.value)
    if (!isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      return
    }
    const nextPreview = truncatePreviewText(preview.previewText) || cachedPreview
    previewFileName.value = preview.fileName || item.fileName
    previewStatus.value = preview.status || item.status
    previewText.value = nextPreview
    previewMessage.value = nextPreview ? '' : t('documents.emptyPreview')
    previewMessageTone.value = 'note'
  } catch (error) {
    if (!isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      return
    }
    if (cachedPreview) {
      previewText.value = cachedPreview
      previewMessage.value = t('documents.previewUnavailable')
      previewMessageTone.value = 'note'
      return
    }
    previewText.value = ''
    previewMessage.value = extractApiError(error, t('errors.loadPreview'))
    previewMessageTone.value = 'error'
  } finally {
    if (isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      isPreviewLoading.value = false
    }
  }
}

function closePreview() {
  latestPreviewRequestId += 1
  isPreviewOpen.value = false
  isPreviewLoading.value = false
  previewDocumentId.value = null
  previewFileName.value = ''
  previewStatus.value = ''
  previewText.value = ''
  previewMessage.value = ''
  previewMessageTone.value = 'note'
}

function isCurrentDocumentContext(contextVersion: number, contextKey: string) {
  return (
    contextVersion === documentContextVersion &&
    contextKey === currentContextKey.value &&
    !appStore.isGroupsLoading
  )
}

function isActiveDocumentRequest(contextVersion: number, contextKey: string, requestId: number) {
  return isCurrentDocumentContext(contextVersion, contextKey) && requestId === latestLoadRequestId
}

function isActivePreviewRequest(contextVersion: number, contextKey: string, requestId: number) {
  return (
    isCurrentDocumentContext(contextVersion, contextKey) &&
    requestId === latestPreviewRequestId &&
    isPreviewOpen.value
  )
}

function clearDocumentPollingTimer() {
  if (pollingTimer !== null) {
    window.clearTimeout(pollingTimer)
    pollingTimer = null
  }
}

function stopDocumentPolling() {
  clearDocumentPollingTimer()
  isPollingDocuments.value = false
}

function syncDocumentPolling() {
  clearDocumentPollingTimer()
  if (!canLoadDocuments.value || currentGroupId.value === null || !hasPendingDocuments.value) {
    isPollingDocuments.value = false
    return
  }
  pollingTimer = window.setTimeout(() => {
    if (!canLoadDocuments.value || currentGroupId.value === null) {
      stopDocumentPolling()
      return
    }
    void loadDocuments({ silent: true })
  }, DOCUMENT_POLL_INTERVAL_MS)
}

function syncPollingFeedback(previousDocuments: DocumentItem[], nextDocuments: DocumentItem[]) {
  const transitionedDocument = findPollingTransitionedDocument(previousDocuments, nextDocuments)
  if (transitionedDocument === null) {
    return
  }

  if (transitionedDocument.status === 'READY') {
    uploadFeedback.value = t('documents.completed', { name: transitionedDocument.fileName })
    return
  }

  if (transitionedDocument.status === 'FAILED') {
    uploadFeedback.value = t('documents.processFailed', { name: transitionedDocument.fileName })
  }
}

function findPollingTransitionedDocument(
  previousDocuments: DocumentItem[],
  nextDocuments: DocumentItem[],
): DocumentItem | null {
  const previousStatusMap = new Map(previousDocuments.map((item) => [item.documentId, item.status]))

  for (const item of nextDocuments) {
    const previousStatus = previousStatusMap.get(item.documentId)
    if (!isPendingDocumentStatus(previousStatus) || !isTerminalDocumentStatus(item.status)) {
      continue
    }
    return item
  }

  return null
}

function isPendingDocumentStatus(status: string | undefined) {
  return status === 'UPLOADED' || status === 'PROCESSING'
}

function isTerminalDocumentStatus(status: string) {
  return status === 'READY' || status === 'FAILED'
}

function describeDocumentRow(item: DocumentItem) {
  return item.contentType ?? item.fileExt ?? t('documents.unknownType')
}

const uploadStageText = computed(() => {
  switch (uploadStage.value) {
    case 'hashing':
      return t('documents.hashing')
    case 'checking':
      return t('documents.checkingUpload')
    case 'uploading':
      return t('documents.uploadingChunks', { percent: uploadProgress.value })
    case 'completing':
      return t('documents.completingUpload')
    default:
      return ''
  }
})
</script>

<template>
  <WorkbenchShell class="page-shell--documents">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="documents-page">
        <PageHeaderHero :eyebrow="$t('documents.eyebrow')" :title="$t('documents.title')" :description="pageHeroDescription">
        </PageHeaderHero>

        <div class="documents-page__feedback">
          <p v-if="groupLoadError" class="feedback feedback--error">{{ groupLoadError }}</p>
          <p v-if="uploadFeedback" class="feedback feedback--success">{{ uploadFeedback }}</p>
          <p v-if="uploadError" class="feedback feedback--error">{{ uploadError }}</p>
          <p v-if="documentsError" class="feedback feedback--error">{{ documentsError }}</p>
          <div v-if="isUploading" class="document-upload-progress">
            <div class="document-upload-progress__meta">
              <strong>{{ selectedFileName }}</strong>
              <span>{{ uploadStageText }}</span>
            </div>
            <div class="document-upload-progress__bar">
              <div class="document-upload-progress__value" :style="{ width: `${uploadProgress}%` }"></div>
            </div>
          </div>
        </div>

        <DocumentPageToolbar
          :groups="visibleGroups"
          :current-group-id="currentGroupId"
          :current-group="currentGroup"
          :is-groups-loading="appStore.isGroupsLoading"
          :file-name="filters.fileName"
          :status="filters.status"
          :status-options="documentStatusOptions"
          :selected-file-name="selectedFileName"
          :file-input-key="fileInputKey"
          :can-manage-current-group="canManageCurrentGroup"
          :is-uploading="isUploading"
          @change:group-id="handleGroupChange"
          @change:file-name="filters.fileName = $event"
          @change:status="filters.status = $event"
          @refresh-groups="refreshGroups"
          @file-change="handleFileChange"
          @upload="handleUpload"
        />

        <DocumentStatusBoard
          :summary-items="statusSummaryItems"
          :recent-failures="recentFailures"
          :results-summary="resultsSummary"
          :matched-count="visibleDocuments.length"
        />

        <article class="panel panel--wide documents-page__results">
          <div class="panel__header">
            <div>
              <p class="panel__eyebrow">{{ $t('common.result') }}</p>
              <h2>{{ $t('documents.fileList') }}</h2>
            </div>
            <div class="documents-page__results-actions">
              <span v-if="hasPendingDocuments" class="document-auto-refresh-hint">
                {{ isPollingDocuments ? $t('documents.autoRefreshing') : $t('documents.processingRefresh') }}
              </span>
              <button
                type="button"
                class="ghost-button"
                :disabled="!canLoadDocuments || isLoading || isPollingDocuments"
                @click="handleRefreshDocuments"
              >
                {{ isLoading ? $t('common.refreshing') : $t('common.refresh') }}
              </button>
              <span class="panel__pill">{{ currentGroup ? currentGroup.groupName : $t('common.notSelected') }}</span>
            </div>
          </div>

          <form class="document-filter-form" @submit.prevent="handleApplyFilters">
            <label class="document-filter-form__field">
              <span>{{ $t('documents.from') }}</span>
              <input v-model="filters.uploadedFrom" type="date" />
            </label>

            <label class="document-filter-form__field">
              <span>{{ $t('documents.to') }}</span>
              <input v-model="filters.uploadedTo" type="date" />
            </label>

            <div class="document-filter-form__actions">
              <div class="document-filter-form__meta">
                <p class="filter-hint">{{ filterHint }}</p>
                <p class="filter-hint">{{ groupScopeSummary }}</p>
              </div>
              <div class="document-filter-form__buttons">
                <button type="button" class="ghost-button" @click="handleResetFilters">{{ $t('documents.reset') }}</button>
                <button type="submit" class="primary-button" :disabled="isLoading || !canLoadDocuments">
                  {{ isLoading ? $t('documents.filtering') : $t('documents.apply') }}
                </button>
              </div>
            </div>
          </form>

          <p v-if="appStore.isGroupsLoading" class="placeholder-text">{{ $t('qa.syncingKnowledgeBases') }}</p>
          <p v-else-if="currentGroup === null" class="placeholder-text">{{ $t('documents.selectKnowledgeBase') }}</p>
          <p v-else-if="isLoading" class="placeholder-text">{{ $t('documents.loadingFiles') }}</p>
          <p v-else-if="visibleDocuments.length === 0" class="placeholder-text">{{ emptyStateMessage }}</p>

          <div v-else class="document-table-wrap">
            <table class="document-table">
              <thead>
                <tr>
                  <th>{{ $t('documents.file') }}</th>
                  <th>{{ $t('documents.type') }}</th>
                  <th>{{ $t('documents.size') }}</th>
                  <th>{{ $t('documents.uploader') }}</th>
                  <th>{{ $t('documents.statusAndIssue') }}</th>
                  <th>{{ $t('documents.uploadedAt') }}</th>
                  <th>{{ $t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in visibleDocuments" :key="item.documentId">
                  <td>
                    <strong>{{ item.fileName }}</strong>
                  </td>
                  <td><span>{{ describeDocumentRow(item) }}</span></td>
                  <td><span>{{ formatDocumentFileSize(item.fileSize) }}</span></td>
                  <td>
                    <strong>{{ formatUploaderLabel(item) }}</strong>
                  </td>
                  <td>
                    <span class="status-chip" :data-tone="getDocumentStatusMeta(item.status).tone">
                      {{ getDocumentStatusMeta(item.status).label }}
                    </span>
                    <span v-if="item.failureReason" class="table-note table-note--danger">
                      {{ item.failureReason }}
                    </span>
                    <span v-else class="table-note">
                      {{ item.previewText ? $t('documents.cachedPreview') : $t('documents.previewOnDemand') }}
                    </span>
                  </td>
                  <td>{{ formatDocumentDateTime(item.uploadedAt) }}</td>
                  <td>
                    <div class="table-actions">
                      <button
                        class="ghost-button"
                        :class="{ 'preview-trigger--disabled': !canPreviewDocument(item, currentGroupRelation) }"
                        :disabled="!canPreviewDocument(item, currentGroupRelation)"
                        @click="handlePreview(item)"
                      >
                        {{ getPreviewButtonLabel(item, currentGroupRelation) }}
                      </button>
                      <button
                        v-if="canManageCurrentGroup && item.status === 'FAILED'"
                        class="ghost-button"
                        :disabled="retryingDocumentIds.has(item.documentId)"
                        @click="handleRetryIngestion(item)"
                      >
                        {{ retryingDocumentIds.has(item.documentId) ? $t('common.processing') : $t('documents.retryProcessing') }}
                      </button>
                      <button
                        v-if="canManageCurrentGroup"
                        class="ghost-button ghost-button--danger"
                        :disabled="deletingDocumentIds.has(item.documentId) || retryingDocumentIds.has(item.documentId)"
                        @click="handleDelete(item.documentId, item.fileName)"
                      >
                        {{ deletingDocumentIds.has(item.documentId) ? $t('documents.deleting') : $t('common.delete') }}
                      </button>
                    </div>
                    <span v-if="!canPreviewDocument(item, currentGroupRelation)" class="table-note">
                      {{ $t('documents.readyOnlyPreview') }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </main>
    </template>
  </WorkbenchShell>

  <Teleport to="body">
    <div v-if="isPreviewOpen" class="document-preview-backdrop" @click.self="closePreview">
      <section class="document-preview-panel" role="dialog" aria-modal="true" aria-labelledby="document-preview-title">
        <header>
          <div>
            <p class="panel__eyebrow">{{ $t('documents.documentPreview') }}</p>
            <h2 id="document-preview-title">{{ previewFileName }}</h2>
            <p class="document-preview-meta">
              {{ $t('documents.previewMeta', { id: previewDocumentId, status: getDocumentStatusMeta(previewStatus).label }) }}
            </p>
          </div>
          <button class="ghost-button" @click="closePreview">{{ $t('common.close') }}</button>
        </header>

        <p v-if="previewMessage" :class="previewMessageTone === 'error' ? 'feedback feedback--error' : 'document-preview-note'">
          {{ previewMessage }}
        </p>

        <p v-if="isPreviewLoading && previewText.length === 0" class="placeholder-text">{{ $t('documents.loadingPreview') }}</p>
        <div v-else class="document-preview-text">
          {{ previewText || $t('documents.emptyPreview') }}
        </div>

        <div class="document-preview-actions">
          <button class="primary-button" @click="closePreview">{{ $t('documents.done') }}</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
