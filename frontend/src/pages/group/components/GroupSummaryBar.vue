<script setup lang="ts">
import type { WorkspaceNodeType } from '../groupWorkspaceView'

defineProps<{
  ownedCount: number
  joinedCount: number
  invitationCount: number
  activeSection: WorkspaceNodeType | ''
}>()

const emit = defineEmits<{
  focus: [section: WorkspaceNodeType]
  create: []
}>()

function handleFocus(section: WorkspaceNodeType) {
  emit('focus', section)
}
</script>

<template>
  <section class="groups-summary">
    <div class="groups-summary__heading">
      <div>
        <p class="groups-summary__eyebrow">{{ $t('groups.overview') }}</p>
        <h2>{{ $t('groups.groupOverview') }}</h2>
        <p class="groups-summary__intro">
          {{ $t('groups.overviewHint') }}
        </p>
      </div>
      <button class="primary-button groups-summary__create" type="button" @click="emit('create')">
        {{ $t('groups.createGroup') }}
      </button>
    </div>

    <div class="groups-summary__grid">
      <button
        class="groups-summary__card"
        :class="{ 'is-active': activeSection === 'ownedGroup' }"
        type="button"
        @click="handleFocus('ownedGroup')"
      >
        <span class="groups-summary__label">{{ $t('groups.owned') }}</span>
        <strong>{{ ownedCount }}</strong>
        <small>{{ $t('groups.enterOwned') }}</small>
      </button>

      <button
        class="groups-summary__card"
        :class="{ 'is-active': activeSection === 'joinedGroup' }"
        type="button"
        @click="handleFocus('joinedGroup')"
      >
        <span class="groups-summary__label">{{ $t('groups.joined') }}</span>
        <strong>{{ joinedCount }}</strong>
        <small>{{ $t('groups.enterJoined') }}</small>
      </button>

      <button
        class="groups-summary__card groups-summary__card--pending"
        :class="{ 'is-active': activeSection === 'invitation' }"
        type="button"
        @click="handleFocus('invitation')"
      >
        <span class="groups-summary__label">
          {{ $t('groups.invitations') }}
          <span v-if="invitationCount > 0" class="groups-summary__badge">{{ $t('groups.todo') }}</span>
        </span>
        <strong>{{ invitationCount }}</strong>
        <small>{{ $t('groups.processInvitationsFirst') }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.groups-summary {
  display: grid;
  gap: 1rem;
}

.groups-summary__heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.groups-summary__eyebrow {
  margin: 0 0 0.3rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1.35;
  text-transform: none;
  color: #195a76;
}

.groups-summary__heading h2 {
  margin: 0;
  color: #102a3b;
}

.groups-summary__intro {
  margin: 0.55rem 0 0;
  color: #4f6472;
  line-height: 1.65;
  max-width: 42rem;
}

.groups-summary__create {
  flex-shrink: 0;
}

.groups-summary__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.9rem;
}

.groups-summary__card {
  display: grid;
  gap: 0.4rem;
  min-width: 0;
  padding: 0.9rem 0.95rem;
  border-radius: 0.7rem;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.9);
  color: #102a3b;
  text-align: left;
  overflow: visible;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.groups-summary__card:hover {
  transform: translateY(-1px);
  border-color: rgba(25, 90, 118, 0.24);
  box-shadow: 0 16px 30px rgba(16, 42, 59, 0.08);
}

.groups-summary__card strong {
  font-size: clamp(1.7rem, 2.4vw, 2.3rem);
  line-height: 1.15;
}

.groups-summary__label {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.84rem;
  font-weight: 600;
  color: #335567;
}

.groups-summary__badge {
  padding: 0.14rem 0.42rem;
  border-radius: 0.4rem;
  background: rgba(226, 111, 60, 0.14);
  color: #9b3f1b;
  font-size: 0.72rem;
}

.groups-summary__card small {
  color: #607684;
  line-height: 1.5;
}

.groups-summary__card.is-active {
  border-color: rgba(25, 90, 118, 0.3);
  background: linear-gradient(150deg, rgba(244, 251, 255, 0.98), rgba(226, 240, 247, 0.92));
  box-shadow: 0 20px 44px rgba(16, 42, 59, 0.12);
}

.groups-summary__card--pending {
  background: linear-gradient(150deg, rgba(255, 251, 246, 0.96), rgba(255, 242, 233, 0.9));
}

@media (max-width: 900px) {
  .groups-summary__heading,
  .groups-summary__grid {
    grid-template-columns: 1fr;
  }

  .groups-summary__heading {
    display: grid;
  }
}
</style>
