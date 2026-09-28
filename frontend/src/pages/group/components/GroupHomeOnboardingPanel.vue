<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { JoinRequestItem } from '../../../api/group'
import type { WorkspaceNodeType } from '../groupWorkspaceView'

defineProps<{
  currentUserLabel: string
  hasAnyWorkspaceItem: boolean
  hasSelection: boolean
  ownedCount: number
  joinedCount: number
  invitationCount: number
  myJoinRequestCount: number
  joinGroupCode: string
  isSubmittingJoinRequest: boolean
  myJoinRequests: JoinRequestItem[]
  isMyRequestsLoading: boolean
}>()

const emit = defineEmits<{
  openCreate: []
  openSecurity: []
  submitJoinRequest: []
  focus: [section: WorkspaceNodeType]
  'update:joinGroupCode': [value: string]
}>()

function handleJoinGroupCodeInput(event: Event) {
  emit('update:joinGroupCode', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="group-home-onboarding">
    <header class="group-home-onboarding__hero">
      <div class="group-home-onboarding__copy">
        <p class="group-home-onboarding__eyebrow">{{ $t('groups.gettingStartedEyebrow') }}</p>
        <h2>
          {{ hasAnyWorkspaceItem ? (hasSelection ? $t('groups.workspaceReady') : $t('groups.selectToStart')) : $t('groups.gettingStarted') }}
        </h2>
        <p>
          {{ $t('groups.onboardingHello', { name: currentUserLabel }) }}
        </p>
      </div>

      <div class="group-home-onboarding__stats">
        <article>
          <span>{{ $t('groups.owned') }}</span>
          <strong>{{ ownedCount }}</strong>
        </article>
        <article>
          <span>{{ $t('groups.joined') }}</span>
          <strong>{{ joinedCount }}</strong>
        </article>
        <article>
          <span>{{ $t('groups.invitations') }}</span>
          <strong>{{ invitationCount }}</strong>
        </article>
      </div>
    </header>

    <section class="group-home-onboarding__steps">
      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">01</span>
        <h3>{{ $t('groups.confirmCurrentGroup') }}</h3>
        <p v-if="hasAnyWorkspaceItem">{{ $t('groups.chooseExistingHint') }}</p>
        <p v-else>{{ $t('groups.noGroupHint') }}</p>
        <div class="group-home-onboarding__actions">
          <button
            v-if="ownedCount > 0"
            class="primary-button"
            type="button"
            @click="emit('focus', 'ownedGroup')"
          >
            {{ $t('groups.viewOwned') }}
          </button>
          <button
            v-else-if="joinedCount > 0"
            class="primary-button"
            type="button"
            @click="emit('focus', 'joinedGroup')"
          >
            {{ $t('groups.viewJoined') }}
          </button>
          <button v-else class="primary-button" type="button" @click="emit('openCreate')">{{ $t('groups.createFirst') }}</button>
          <button
            v-if="invitationCount > 0"
            class="ghost-button"
            type="button"
            @click="emit('focus', 'invitation')"
          >
            {{ $t('groups.processInvitations') }}
          </button>
        </div>
      </article>

      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">02</span>
        <h3>{{ $t('groups.prepareDocuments') }}</h3>
        <p>{{ $t('groups.prepareDocumentsHint') }}</p>
        <div class="group-home-onboarding__actions">
          <RouterLink class="ghost-button group-home-onboarding__link" to="/documents">{{ $t('groups.goDocuments') }}</RouterLink>
        </div>
      </article>

      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">03</span>
        <h3>{{ $t('groups.startAsking') }}</h3>
        <p>{{ $t('groups.startAskingHint') }}</p>
        <div class="group-home-onboarding__actions">
          <RouterLink class="ghost-button group-home-onboarding__link" to="/qa">{{ $t('qa.title') }}</RouterLink>
          <button class="ghost-button" type="button" @click="emit('openSecurity')">{{ $t('groups.accountSecurity') }}</button>
        </div>
      </article>
    </section>

    <section class="group-home-onboarding__foot">
      <article class="group-home-onboarding__card">
        <div class="group-home-onboarding__card-header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.todo') }}</p>
            <h3>{{ $t('groups.nextSteps') }}</h3>
          </div>
          <span class="panel__pill panel__pill--soft">{{ invitationCount + myJoinRequestCount }}</span>
        </div>
        <ul class="group-home-onboarding__todo">
          <li v-if="invitationCount > 0">{{ $t('groups.invitationsTodo', { count: invitationCount }) }}</li>
          <li v-if="myJoinRequestCount > 0">{{ $t('groups.requestsTodo', { count: myJoinRequestCount }) }}</li>
          <li v-if="!hasAnyWorkspaceItem">{{ $t('groups.noGroupTodo') }}</li>
          <li v-if="hasAnyWorkspaceItem && !hasSelection">{{ $t('groups.noSelectionTodo') }}</li>
          <li>{{ $t('groups.securityTodo') }}</li>
        </ul>
      </article>

      <article class="group-home-onboarding__card">
        <div class="group-home-onboarding__card-header">
          <div>
            <p class="panel__eyebrow">{{ $t('groups.applyToJoin') }}</p>
            <h3>{{ $t('groups.joinByOrganizationId') }}</h3>
          </div>
        </div>

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

        <p class="group-home-onboarding__hint">{{ $t('groups.organizationIdHelp') }}</p>

        <p v-if="isMyRequestsLoading" class="placeholder-text">{{ $t('groups.loadingMyRequests') }}</p>
        <ul v-else-if="myJoinRequests.length > 0" class="join-request-list">
          <li
            v-for="request in myJoinRequests.slice(0, 3)"
            :key="`onboarding-request-${request.requestId}`"
            class="join-request-list__item"
          >
            <div>
              <strong>{{ request.groupName }}</strong>
              <span>{{ request.groupCode }} · {{ request.status }}</span>
            </div>
            <span>{{ new Date(request.createdAt).toLocaleString() }}</span>
          </li>
        </ul>
        <p v-else class="placeholder-text">{{ $t('groups.noSubmittedRequests') }}</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.group-home-onboarding {
  display: grid;
  gap: 1.25rem;
}

.group-home-onboarding__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(16rem, 0.8fr);
  gap: 1rem;
  padding: 1.25rem;
  border-radius: 0.8rem;
  background:
    radial-gradient(circle at top right, rgba(106, 167, 189, 0.22), transparent 28rem),
    linear-gradient(155deg, rgba(255, 255, 255, 0.98), rgba(233, 244, 248, 0.94));
  border: 1px solid rgba(16, 42, 59, 0.08);
  box-shadow: 0 18px 40px rgba(13, 40, 58, 0.07);
  overflow: visible;
}

.group-home-onboarding__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #195a76;
}

.group-home-onboarding__copy h2,
.group-home-onboarding__card-header h3 {
  margin: 0;
  color: #102a3b;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.group-home-onboarding__copy p {
  margin: 0.75rem 0 0;
  color: #4f6472;
  line-height: 1.7;
  max-width: 42rem;
}

.group-home-onboarding__stats {
  display: grid;
  gap: 0.8rem;
}

.group-home-onboarding__stats article,
.group-home-onboarding__card {
  display: grid;
  gap: 0.45rem;
  min-width: 0;
  padding: 0.95rem 1rem;
  border-radius: 0.7rem;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(16, 42, 59, 0.08);
}

.group-home-onboarding__stats span {
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #607684;
}

.group-home-onboarding__stats strong {
  font-size: 2rem;
  line-height: 1;
  color: #102a3b;
}

.group-home-onboarding__steps,
.group-home-onboarding__foot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.group-home-onboarding__foot {
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
}

.group-home-onboarding__step {
  display: grid;
  gap: 0.7rem;
  min-width: 0;
  padding: 1rem;
  border-radius: 0.7rem;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.9);
  overflow: visible;
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease;
}

.group-home-onboarding__step:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 42px rgba(13, 40, 58, 0.08);
}

.group-home-onboarding__step-index {
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7d99a8;
}

.group-home-onboarding__step h3 {
  margin: 0;
  color: #102a3b;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.group-home-onboarding__step p,
.group-home-onboarding__hint,
.group-home-onboarding__todo {
  margin: 0;
  color: #607684;
  line-height: 1.65;
}

.group-home-onboarding__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.group-home-onboarding__link {
  text-decoration: none;
}

.group-home-onboarding__card-header {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  align-items: start;
}

.group-home-onboarding__todo {
  display: grid;
  gap: 0.55rem;
  padding-left: 1rem;
}

@media (max-width: 1100px) {
  .group-home-onboarding__hero,
  .group-home-onboarding__steps,
  .group-home-onboarding__foot {
    grid-template-columns: 1fr;
  }
}
</style>
