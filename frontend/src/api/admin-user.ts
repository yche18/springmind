import http, { type ApiResponse } from './http'
import type { SystemRole } from './auth'
import { translate } from '../i18n'

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface AdminUserItem {
  userId: number
  userCode: string
  username: string
  email: string
  displayName: string
  systemRole: SystemRole
  status: UserStatus
  mustChangePassword: boolean
  lastLoginAt: string | null
}

export async function fetchAdminUsers(): Promise<AdminUserItem[]> {
  const { data } = await http.get<ApiResponse<AdminUserItem[]>>('/admin/users')
  return unwrapApiResponse(data, translate('errors.loadUsers'))
}

export async function fetchAdminUserDetail(userId: number): Promise<AdminUserItem> {
  const { data } = await http.get<ApiResponse<AdminUserItem>>(`/admin/users/${userId}`)
  return unwrapApiResponse(data, translate('errors.loadUser'))
}

export async function updateAdminUserStatus(userId: number, status: UserStatus): Promise<void> {
  const { data } = await http.patch<ApiResponse<null>>(`/admin/users/${userId}/status`, { status })
  unwrapApiResponse(data, translate('errors.updateUserStatus'))
}

export async function resetAdminUserPassword(userId: number, newPassword: string): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>(`/admin/users/${userId}/reset-password`, {
    newPassword,
  })
  unwrapApiResponse(data, translate('errors.resetPassword'))
}

function unwrapApiResponse<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success) {
    throw new Error(payload.message ?? fallbackMessage)
  }
  return payload.data
}
