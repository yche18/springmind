import http from './http'
import type { ApiResponse } from './http'
import { translate } from '../i18n'

export interface GroupItem {
  groupId: number
  groupCode: string
  groupName: string
}

export interface PendingInvitationItem {
  invitationId: number
  groupId: number
  groupName: string
  inviterUserId: number
  inviterDisplayName: string
  status: string
}

export interface GroupQueryResult {
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
  pendingInvitations: PendingInvitationItem[]
}

export interface CreateGroupPayload {
  name: string
  description?: string
}

export interface GroupMemberItem {
  userId: number
  userCode: string
  displayName: string
  role: string
}

export interface JoinRequestItem {
  requestId: number
  groupId: number
  groupCode: string
  groupName: string
  status: string
  createdAt: string
  decidedAt?: string | null
}

export interface OwnerJoinRequestItem {
  requestId: number
  groupId: number
  applicantUserId: number
  applicantUserCode: string
  applicantDisplayName: string
  status: string
  createdAt: string
}

export async function fetchGroups(): Promise<GroupQueryResult> {
  const { data } = await http.get<GroupQueryResult>('/groups/my')
  return data
}

export async function createGroup(payload: CreateGroupPayload): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>('/groups', payload)
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? translate('errors.createGroup'))
  }
  return data.data
}

export async function createInvitation(groupId: number, inviteeUserId: number): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>(`/groups/${groupId}/invitations`, {
    inviteeUserId,
  })
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? translate('errors.createInvitation'))
  }
  return data.data
}

export async function acceptInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/accept`, translate('groups.acceptFailed'))
}

export async function rejectInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/reject`, translate('groups.rejectFailed'))
}

export async function cancelInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/cancel`, translate('errors.cancelInvitation'))
}

export async function fetchGroupMembers(groupId: number): Promise<GroupMemberItem[]> {
  const { data } = await http.get<GroupMemberItem[]>(`/groups/${groupId}/members`)
  return data
}

export async function removeGroupMember(groupId: number, userId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/groups/${groupId}/members/${userId}`)
  if (!data.success) {
    throw new Error(data.message ?? translate('groups.removeMemberFailed'))
  }
}

export async function leaveGroup(groupId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/leave`, translate('groups.leaveFailed'))
}

export async function submitJoinRequest(groupCode: string): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>('/groups/join-requests', { groupCode })
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? translate('errors.submitJoinRequest'))
  }
  return data.data
}

export async function fetchMyJoinRequests(): Promise<JoinRequestItem[]> {
  const { data } = await http.get<ApiResponse<JoinRequestItem[]>>('/groups/join-requests/my')
  if (!data.success) {
    throw new Error(data.message ?? translate('errors.loadMyRequests'))
  }
  return data.data
}

export async function fetchOwnerJoinRequests(groupId: number): Promise<OwnerJoinRequestItem[]> {
  const { data } = await http.get<ApiResponse<OwnerJoinRequestItem[]>>(`/groups/${groupId}/join-requests`)
  if (!data.success) {
    throw new Error(data.message ?? translate('groups.loadRequestsFailed'))
  }
  return data.data
}

export async function approveJoinRequest(groupId: number, requestId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/join-requests/${requestId}/approve`, translate('groups.approveFailed'))
}

export async function rejectJoinRequest(groupId: number, requestId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/join-requests/${requestId}/reject`, translate('groups.rejectRequestFailed'))
}

async function postVoid(url: string, fallbackMessage: string) {
  const { data } = await http.post<ApiResponse<null>>(url)
  if (!data.success) {
    throw new Error(data.message ?? fallbackMessage)
  }
}
