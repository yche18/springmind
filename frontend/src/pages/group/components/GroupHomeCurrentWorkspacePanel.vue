<script setup lang="ts">
import type {
  GroupItem,
  GroupMemberItem,
  OwnerJoinRequestItem,
  PendingInvitationItem,
} from '../../../api/group'

defineProps<{
  isCreateComposerOpen: boolean
  isCreatingGroup: boolean
  createGroupName: string
  createGroupDescription: string
  selectedInvitation: PendingInvitationItem | null
  selectedOwnedGroup: GroupItem | null
  selectedJoinedGroup: GroupItem | null
  selectedOwnerMemberCount: string
  selectedMemberMessage: string
  groupMembers: GroupMemberItem[]
  ownerJoinRequests: OwnerJoinRequestItem[]
  isMembersLoading: boolean
  isOwnerRequestsLoading: boolean
  isInviting: boolean
  inviteeUserId: string
  invitationActionIds: Set<number>
  joinRequestActionIds: Set<number>
  removingMemberKeys: Set<string>
  leavingGroupIds: Set<number>
}>()

const emit = defineEmits<{
  closeCreate: []
  createGroup: []
  inviteMember: []
  invitationDecision: [invitationId: number, action: 'accept' | 'reject']
  joinRequestDecision: [requestId: number, action: 'approve' | 'reject']
  removeMember: [userId: number]
  leaveGroup: [groupId: number]
  'update:createGroupName': [value: string]
  'update:createGroupDescription': [value: string]
  'update:inviteeUserId': [value: string]
}>()

function handleCreateGroupNameInput(event: Event) {
  emit('update:createGroupName', (event.target as HTMLInputElement).value)
}

function handleCreateGroupDescriptionInput(event: Event) {
  emit('update:createGroupDescription', (event.target as HTMLTextAreaElement).value)
}

function handleInviteeUserIdInput(event: Event) {
  emit('update:inviteeUserId', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="group-home-current">
    <div class="group-home-current__header">
      <div>
        <p class="panel__eyebrow">{{ isCreateComposerOpen ? $t('common.create') : $t('common.details') }}</p>
        <h2>{{ isCreateComposerOpen ? $t('groups.createNewGroup') : $t('groups.groupDetails') }}</h2>
        <p>
          {{
            isCreateComposerOpen
              ? $t('groups.createDetails')
              : $t('groups.detailHint')
          }}
        </p>
      </div>
    </div>

    <section v-if="isCreateComposerOpen" class="detail-card detail-card--composer">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">{{ $t('common.create') }}</p>
          <h2>{{ $t('groups.newGroupInfo') }}</h2>
        </div>
        <button class="ghost-button" type="button" @click="emit('closeCreate')">{{ $t('common.cancel') }}</button>
      </div>

      <div class="detail-card__stack">
        <label class="groups-form-field">
          <span>{{ $t('groups.groupName') }}</span>
          <input
            :value="createGroupName"
            type="text"
            maxlength="128"
            :placeholder="$t('groups.groupNameExample')"
            @input="handleCreateGroupNameInput"
          />
        </label>
        <label class="groups-form-field">
          <span>{{ $t('groups.groupDescription') }}</span>
          <textarea
            :value="createGroupDescription"
            maxlength="512"
            rows="4"
            :placeholder="$t('groups.groupDescriptionPlaceholder')"
            @input="handleCreateGroupDescriptionInput"
          />
        </label>
      </div>

      <div class="detail-card__actions">
        <button class="primary-button" :disabled="isCreatingGroup" type="button" @click="emit('createGroup')">
          {{ isCreatingGroup ? $t('common.processing') : $t('groups.createGroup') }}
        </button>
      </div>
    </section>

    <section v-else-if="selectedInvitation" class="detail-card">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">{{ $t('groups.invite') }}</p>
          <h2>{{ selectedInvitation.groupName }}</h2>
        </div>
      </div>

      <div class="detail-card__stack">
        <div class="detail-meta">
          <div>
            <span>{{ $t('groups.inviter') }}</span>
            <strong>{{ selectedInvitation.inviterDisplayName }}</strong>
          </div>
          <div>
            <span>{{ $t('groups.targetGroup') }}</span>
            <strong>#{{ selectedInvitation.groupId }}</strong>
          </div>
          <div>
            <span>{{ $t('common.status') }}</span>
            <strong>{{ selectedInvitation.status }}</strong>
          </div>
        </div>
        <p class="detail-note">{{ $t('groups.invitationDecisionHint') }}</p>
      </div>

      <div class="detail-card__actions">
        <button
          class="primary-button"
          :disabled="invitationActionIds.has(selectedInvitation.invitationId)"
          type="button"
          @click="emit('invitationDecision', selectedInvitation.invitationId, 'accept')"
        >
          {{ $t('groups.acceptInvitation') }}
        </button>
        <button
          class="ghost-button"
          :disabled="invitationActionIds.has(selectedInvitation.invitationId)"
          type="button"
          @click="emit('invitationDecision', selectedInvitation.invitationId, 'reject')"
        >
          {{ $t('groups.rejectInvitation') }}
        </button>
      </div>

    </section>

    <section v-else-if="selectedOwnedGroup" class="detail-card detail-card--owner">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">{{ $t('groups.owned') }}</p>
          <h2>{{ selectedOwnedGroup.groupName }}</h2>
        </div>
        <span class="panel__pill">{{ $t('common.owner') }}</span>
      </div>

      <div class="detail-card__stack detail-card__stack--split">
        <section class="detail-subsection">
          <h3>{{ $t('groups.basicInfo') }}</h3>
          <div class="detail-meta">
            <div>
              <span>{{ $t('groups.organizationId') }}</span>
              <strong :title="selectedOwnedGroup.groupCode">{{ selectedOwnedGroup.groupCode }}</strong>
            </div>
            <div>
              <span>{{ $t('groups.internalId') }}</span>
              <strong>#{{ selectedOwnedGroup.groupId }}</strong>
            </div>
            <div>
              <span>{{ $t('groups.currentRole') }}</span>
              <strong>{{ $t('common.owner') }}</strong>
            </div>
            <div>
              <span>{{ $t('groups.memberCount') }}</span>
              <strong>{{ selectedOwnerMemberCount }}</strong>
            </div>
          </div>
          <p class="detail-note">{{ $t('groups.ownerPermissionHint') }}</p>
        </section>

        <section class="detail-subsection">
          <div class="detail-subsection__header">
            <h3>{{ $t('groups.members') }}</h3>
            <span class="panel__pill panel__pill--soft">{{ $t('groups.selectedGroup') }}</span>
          </div>

          <label class="groups-form-field">
            <span>{{ $t('groups.inviteUserId') }}</span>
            <div class="groups-inline-form">
              <input
                :value="inviteeUserId"
                type="number"
                min="1"
                :placeholder="$t('groups.inviteeIdExample')"
                @input="handleInviteeUserIdInput"
              />
              <button class="primary-button" :disabled="isInviting" type="button" @click="emit('inviteMember')">
                {{ isInviting ? $t('groups.inviting') : $t('groups.inviteMember') }}
              </button>
            </div>
          </label>
          <p class="detail-note">{{ $t('groups.inviteHelp') }}</p>

          <p v-if="isMembersLoading" class="placeholder-text">{{ $t('groups.loadingMembers') }}</p>
          <ul v-else class="groups-member-list">
            <li v-for="member in groupMembers" :key="`member-${member.userId}`" class="groups-member-list__item">
              <div class="groups-member-list__profile">
                <strong>{{ member.displayName }}</strong>
                <span>
                  {{ $t('groups.memberMeta', { id: member.userId, code: member.userCode, role: member.role === 'OWNER' ? $t('common.owner') : member.role }) }}
                </span>
              </div>
              <button
                v-if="member.role !== 'OWNER'"
                class="ghost-button"
                :disabled="removingMemberKeys.has(`${selectedOwnedGroup.groupId}:${member.userId}`)"
                type="button"
                @click="emit('removeMember', member.userId)"
              >
                {{ removingMemberKeys.has(`${selectedOwnedGroup.groupId}:${member.userId}`) ? $t('groups.removing') : $t('groups.removeMember') }}
              </button>
            </li>
          </ul>
        </section>

        <section class="detail-subsection">
          <div class="detail-subsection__header">
            <h3>{{ $t('groups.pendingRequests') }}</h3>
            <span class="panel__pill panel__pill--pending">{{ ownerJoinRequests.length }}</span>
          </div>

          <p v-if="isOwnerRequestsLoading" class="placeholder-text">{{ $t('groups.loadingApprovals') }}</p>
          <p v-else-if="ownerJoinRequests.length === 0" class="placeholder-text">{{ $t('groups.noRequests') }}</p>
          <ul v-else class="groups-member-list">
            <li
              v-for="request in ownerJoinRequests"
              :key="`owner-request-${request.requestId}`"
              class="groups-member-list__item"
            >
              <div>
                <strong>{{ request.applicantDisplayName }}</strong>
                <span>{{ new Date(request.createdAt).toLocaleString() }}</span>
              </div>
              <div class="detail-card__actions">
                <button
                  class="primary-button"
                  :disabled="joinRequestActionIds.has(request.requestId)"
                  type="button"
                  @click="emit('joinRequestDecision', request.requestId, 'approve')"
                >
                  {{ $t('groups.approve') }}
                </button>
                <button
                  class="ghost-button"
                  :disabled="joinRequestActionIds.has(request.requestId)"
                  type="button"
                  @click="emit('joinRequestDecision', request.requestId, 'reject')"
                >
                  {{ $t('groups.reject') }}
                </button>
              </div>
            </li>
          </ul>
        </section>

      </div>
    </section>

    <section v-else-if="selectedJoinedGroup" class="detail-card detail-card--member">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">{{ $t('groups.joined') }}</p>
          <h2>{{ selectedJoinedGroup.groupName }}</h2>
        </div>
        <span class="panel__pill panel__pill--member">{{ $t('common.member') }}</span>
      </div>

      <div class="detail-card__stack detail-card__stack--split">
        <section class="detail-subsection">
          <h3>{{ $t('groups.basicInfo') }}</h3>
          <div class="detail-meta">
            <div>
              <span>{{ $t('groups.organizationId') }}</span>
              <strong :title="selectedJoinedGroup.groupCode">{{ selectedJoinedGroup.groupCode }}</strong>
            </div>
            <div>
              <span>{{ $t('groups.internalId') }}</span>
              <strong>#{{ selectedJoinedGroup.groupId }}</strong>
            </div>
            <div>
              <span>{{ $t('groups.currentRole') }}</span>
              <strong>{{ $t('common.member') }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-subsection">
          <h3>{{ $t('groups.permissionBoundary') }}</h3>
          <ul class="permissions-list">
            <li>{{ selectedMemberMessage }}</li>
            <li>{{ $t('groups.memberRestrictionInvite') }}</li>
            <li>{{ $t('groups.memberRestrictionOwner') }}</li>
          </ul>
        </section>

      </div>

      <div class="detail-card__actions">
        <button
          class="ghost-button"
          :disabled="leavingGroupIds.has(selectedJoinedGroup.groupId)"
          type="button"
          @click="emit('leaveGroup', selectedJoinedGroup.groupId)"
        >
          {{ leavingGroupIds.has(selectedJoinedGroup.groupId) ? $t('groups.leaving') : $t('groups.leave') }}
        </button>
      </div>
    </section>

    <section v-else class="detail-empty">
      <p class="detail-empty__eyebrow">{{ $t('common.notSelected') }}</p>
      <h2>{{ $t('groups.noSelection') }}</h2>
      <p>{{ $t('groups.selectionHint') }}</p>
    </section>
  </section>
</template>

<style scoped>
.group-home-current {
  display: grid;
  gap: 1rem;
}

.group-home-current__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.group-home-current__header h2 {
  margin: 0;
  color: #102a3b;
}

.group-home-current__header p:last-child {
  margin: 0.45rem 0 0;
  color: #607684;
  line-height: 1.65;
  max-width: 38rem;
}

.group-home-current__request-list {
  display: grid;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.group-home-current__request-list li {
  display: grid;
  gap: 0.18rem;
  padding: 0.8rem 0.9rem;
  border-radius: 18px;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.84);
}

.group-home-current__request-list strong {
  color: #102a3b;
}

.group-home-current__request-list span {
  color: #607684;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .group-home-current__header {
    flex-direction: column;
  }
}
</style>
