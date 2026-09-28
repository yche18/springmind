<script setup lang="ts">
import type { JoinRequestItem, PendingInvitationItem } from '../../../api/group'
import type { WorkspaceNodeType } from '../groupWorkspaceView'
import GroupSummaryBar from './GroupSummaryBar.vue'

defineProps<{
  ownedCount: number
  joinedCount: number
  invitationCount: number
  totalPendingCount: number
  activeSection: WorkspaceNodeType | ''
  pendingInvitations: PendingInvitationItem[]
  myJoinRequests: JoinRequestItem[]
  isMyRequestsLoading: boolean
  joinGroupCode: string
  isSubmittingJoinRequest: boolean
}>()

const emit = defineEmits<{
  focus: [section: WorkspaceNodeType]
  create: []
  submitJoinRequest: []
  'update:joinGroupCode': [value: string]
}>()

function handleJoinGroupCodeInput(event: Event) {
  emit('update:joinGroupCode', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="group-home-overview">
    <GroupSummaryBar
      :owned-count="ownedCount"
      :joined-count="joinedCount"
      :invitation-count="invitationCount"
      :active-section="activeSection"
      @focus="emit('focus', $event)"
      @create="emit('create')"
    />

    <div class="group-home-overview__grid">
      <article class="group-home-overview__card group-home-overview__card--pending-summary">
        <div class="group-home-overview__header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.todo') }}</p>
            <h3>{{ $t('groups.pendingItems') }}</h3>
          </div>
          <span class="panel__pill panel__pill--pending">{{ totalPendingCount }}</span>
        </div>

        <div class="group-home-overview__pending-total">
          <strong>{{ totalPendingCount }}</strong>
          <span>{{ $t('groups.pendingSummary') }}</span>
        </div>
      </article>

      <article class="group-home-overview__card">
        <div class="group-home-overview__header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.invite') }}</p>
            <h3>{{ $t('groups.recentInvitations') }}</h3>
          </div>
          <span class="panel__pill panel__pill--pending">{{ pendingInvitations.length }}</span>
        </div>

        <p v-if="pendingInvitations.length === 0" class="placeholder-text">{{ $t('groups.noNewInvitations') }}</p>
        <ul v-else class="group-home-overview__list">
          <li v-for="invitation in pendingInvitations.slice(0, 3)" :key="`overview-invitation-${invitation.invitationId}`">
            <strong>{{ invitation.groupName }}</strong>
            <span>{{ $t('groups.fromUser', { name: invitation.inviterDisplayName }) }} · {{ invitation.status }}</span>
          </li>
        </ul>
      </article>

      <article class="group-home-overview__card">
        <div class="group-home-overview__header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.applyToJoin') }}</p>
            <h3>{{ $t('groups.myRequests') }}</h3>
          </div>
          <span class="panel__pill panel__pill--soft">{{ myJoinRequests.length }}</span>
        </div>

        <p v-if="isMyRequestsLoading" class="placeholder-text">{{ $t('groups.loadingRequests') }}</p>
        <p v-else-if="myJoinRequests.length === 0" class="placeholder-text">{{ $t('groups.noActiveRequests') }}</p>
        <ul v-else class="group-home-overview__list">
          <li v-for="request in myJoinRequests.slice(0, 3)" :key="`overview-request-${request.requestId}`">
            <strong>{{ request.groupName }}</strong>
            <span>{{ request.groupCode }} · {{ request.status }}</span>
          </li>
        </ul>
      </article>

      <article class="group-home-overview__card group-home-overview__card--join">
        <div class="group-home-overview__header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.applyToJoin') }}</p>
            <h3>{{ $t('groups.joinByOrganizationId') }}</h3>
          </div>
        </div>

        <p class="group-home-overview__copy">{{ $t('groups.joinPrompt') }}</p>
        <div class="groups-inline-form">
          <input
            :value="joinGroupCode"
            type="text"
            maxlength="80"
            :placeholder="$t('groups.organizationIdExample')"
            @input="handleJoinGroupCodeInput"
          />
          <button class="primary-button" :disabled="isSubmittingJoinRequest" type="button" @click="emit('submitJoinRequest')">
            {{ isSubmittingJoinRequest ? $t('common.submitting') : $t('groups.submitRequest') }}
          </button>
        </div>
        <p class="group-home-overview__hint">{{ $t('groups.organizationIdHelp') }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.group-home-overview {
  display: grid;
  gap: 1rem;
}

.group-home-overview__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.group-home-overview__card {
  display: grid;
  gap: 0.8rem;
  min-width: 0;
  padding: 1rem;
  border-radius: 0.75rem;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.92);
  overflow: visible;
}

.group-home-overview__card--join {
  grid-column: 1 / -1;
  background: linear-gradient(145deg, rgba(255, 251, 246, 0.96), rgba(239, 246, 248, 0.92));
}

.group-home-overview__card--pending-summary {
  background: linear-gradient(145deg, rgba(255, 249, 242, 0.96), rgba(250, 240, 232, 0.92));
}

.group-home-overview__header {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  align-items: start;
}

.group-home-overview__header h3 {
  margin: 0;
  color: #102a3b;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.group-home-overview__list {
  display: grid;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.group-home-overview__list li {
  display: grid;
  gap: 0.25rem;
  min-width: 0;
  padding: 0.75rem 0.85rem;
  border-radius: 0.7rem;
  background: rgba(245, 250, 253, 0.86);
  border: 1px solid rgba(16, 42, 59, 0.06);
}

.group-home-overview__list strong {
  color: #102a3b;
}

.group-home-overview__pending-total {
  display: grid;
  gap: 0.35rem;
}

.group-home-overview__pending-total strong {
  font-size: clamp(2rem, 3vw, 2.8rem);
  line-height: 1;
  color: #102a3b;
}

.group-home-overview__pending-total span,
.group-home-overview__list span,
.group-home-overview__copy,
.group-home-overview__hint {
  color: #607684;
  line-height: 1.6;
}

.group-home-overview__card--join .groups-inline-form {
  grid-template-columns: minmax(0, 1fr) auto;
}

.group-home-overview__card--join input {
  min-width: 0;
}

@media (max-width: 1100px) {
  .group-home-overview__grid {
    grid-template-columns: 1fr;
  }
}
</style>
