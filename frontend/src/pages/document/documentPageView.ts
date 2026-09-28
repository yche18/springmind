import type { DocumentItem } from '../../api/document'
import type { GroupRelation, VisibleGroup } from '../../stores/app'
import { getAppLocale, translate } from '../../i18n'

const STATUS_META: Record<string, { key: string; tone: string }> = {
  UPLOADED: { key: 'documents.uploaded', tone: 'queued' },
  PROCESSING: { key: 'documents.pending', tone: 'progress' },
  READY: { key: 'documents.ready', tone: 'ready' },
  FAILED: { key: 'documents.failed', tone: 'failed' },
}

export interface DocumentFilterForm {
  groupId: number | null
  fileName: string
  status: string
  uploadedFrom: string
  uploadedTo: string
}

export interface DocumentStatusSummaryItem {
  key: string
  label: string
  value: string
  description: string
  tone: string
}

export interface DocumentFailureItem {
  documentId: number
  fileName: string
  reason: string
  uploadedAt: string
}

export function getDocumentStatusOptions() {
  return [
    { value: '', label: translate('documents.allStatuses') },
    ...Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: translate(meta.key) })),
  ]
}

export function createDocumentFilterForm(
  context: { groupId?: number | null; relation?: GroupRelation | null } = {},
): DocumentFilterForm {
  void context
  return {
    groupId: context.groupId ?? null,
    fileName: '',
    status: '',
    uploadedFrom: '',
    uploadedTo: '',
  }
}

export function countReadyDocuments(documents: DocumentItem[]) {
  return documents.filter((item) => item.status === 'READY').length
}

export function countFailedDocuments(documents: DocumentItem[]) {
  return documents.filter((item) => item.status === 'FAILED').length
}

export function calculateTotalDocumentSize(documents: DocumentItem[]) {
  return documents.reduce((sum, item) => sum + (Number.isFinite(item.fileSize) ? item.fileSize : 0), 0)
}

export function formatDocumentFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function formatDocumentDateTime(raw: string) {
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return raw || translate('documents.unknownTime')
  return new Intl.DateTimeFormat(getAppLocale() === 'zh' ? 'zh-CN' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
}

export function getDocumentStatusMeta(status: string) {
  const meta = STATUS_META[status]
  return meta ? { label: translate(meta.key), tone: meta.tone } : { label: status, tone: 'queued' }
}

export function formatGroupRelationLabel(relation: GroupRelation | null | undefined) {
  if (relation === 'OWNER') return translate('documents.ownGroup')
  if (relation === 'MEMBER') return translate('documents.joinedGroup')
  return translate('documents.unboundGroup')
}

export function formatUploaderLabel(item: DocumentItem) {
  if (item.uploaderDisplayName) return item.uploaderDisplayName
  return translate('documents.unknownUploader')
}

export function canPreviewDocument(item: DocumentItem, relation: GroupRelation | null) {
  void relation
  return item.status === 'READY'
}

export function getPreviewButtonLabel(item: DocumentItem, relation: GroupRelation | null) {
  return canPreviewDocument(item, relation) ? translate('common.view') : translate('documents.waitingUntilReady')
}

export function truncatePreviewText(raw: string | null | undefined) {
  const value = typeof raw === 'string' ? raw.trim() : ''
  return value.slice(0, 200)
}

export function createDocumentStatusSummary(
  documents: DocumentItem[],
  totalSize: number,
): DocumentStatusSummaryItem[] {
  const queuedCount = documents.filter((item) => item.status === 'UPLOADED' || item.status === 'PROCESSING').length
  const failedCount = countFailedDocuments(documents)

  return [
    { key: 'ready', label: translate('documents.ready'), value: String(countReadyDocuments(documents)), description: translate('documents.readyDescription'), tone: 'ready' },
    { key: 'progress', label: translate('documents.pending'), value: String(queuedCount), description: translate('documents.processingDescription'), tone: 'progress' },
    { key: 'failed', label: translate('documents.failedFiles'), value: String(failedCount), description: translate(failedCount > 0 ? 'documents.failureCheck' : 'documents.noFailedFiles'), tone: 'failed' },
    { key: 'size', label: translate('documents.currentSize'), value: formatDocumentFileSize(totalSize), description: translate('documents.sizeDescription'), tone: 'neutral' },
  ]
}

export function collectRecentDocumentFailures(
  documents: DocumentItem[],
  limit = 3,
): DocumentFailureItem[] {
  return documents
    .filter((item) => item.status === 'FAILED' && item.failureReason)
    .sort((left, right) => parseDateTime(right.uploadedAt) - parseDateTime(left.uploadedAt))
    .slice(0, limit)
    .map((item) => ({
      documentId: item.documentId,
      fileName: item.fileName,
      reason: item.failureReason ?? translate('documents.failureReasonMissing'),
      uploadedAt: formatDocumentDateTime(item.uploadedAt),
    }))
}

export function createDocumentActionItems(options: {
  currentGroup: VisibleGroup | null
  canManageCurrentGroup: boolean
}): string[] {
  if (options.currentGroup === null) {
    return [translate('documents.actionSelectGroup')]
  }

  if (options.canManageCurrentGroup) {
    return ['documents.actionUpload', 'documents.actionPreview', 'documents.actionRetry', 'documents.actionFilter'].map((key) => translate(key))
  }

  return ['documents.actionViewStatus', 'documents.actionPreview', 'documents.actionFilter', 'documents.actionReadOnly'].map((key) => translate(key))
}

export function createDocumentFilterContext(
  filters: DocumentFilterForm,
  relation: GroupRelation | null,
): string[] {
  void relation
  const items = [
    filters.fileName.trim() ? translate('documents.fileNameContains', { name: filters.fileName.trim() }) : translate('documents.fileNameAll'),
    filters.status ? translate('documents.statusValue', { status: getDocumentStatusMeta(filters.status).label }) : translate('documents.statusAll'),
  ]

  items.push(
    filters.uploadedFrom || filters.uploadedTo
      ? translate('documents.uploadTimeRange', { from: filters.uploadedFrom || translate('documents.noLimit'), to: filters.uploadedTo || translate('documents.noLimit') })
      : translate('documents.uploadTimeAny'),
  )

  return items
}

export function matchesDocumentFilters(
  item: DocumentItem,
  filters: DocumentFilterForm,
  currentRelation: GroupRelation | null,
) {
  void currentRelation
  if (filters.groupId !== null && item.groupId !== filters.groupId) return false
  if (filters.fileName.trim() && !item.fileName.toLowerCase().includes(filters.fileName.trim().toLowerCase())) return false
  if (filters.status && item.status !== filters.status) return false

  const uploadedAt = parseDate(filtersDateFallback(item.uploadedAt))
  const uploadedFrom = parseDate(filters.uploadedFrom)
  const uploadedTo = parseDate(filters.uploadedTo, true)

  if (uploadedAt !== null && uploadedFrom !== null && uploadedAt < uploadedFrom) return false
  if (uploadedAt !== null && uploadedTo !== null && uploadedAt > uploadedTo) return false

  return true
}

export function parsePositiveInteger(raw: string) {
  const value = raw.trim()
  if (!/^\d+$/.test(value)) return null

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function parseDate(raw: string, endOfDay = false) {
  const value = raw.trim()
  if (!value) return null

  const parsed = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function parseDateTime(raw: string) {
  const parsed = new Date(raw)
  return Number.isNaN(parsed.getTime()) ? 0 : parsed.getTime()
}

function filtersDateFallback(raw: string) {
  return raw.includes('T') ? raw.slice(0, 10) : raw
}
