import http, { type ApiResponse } from './http'
import { translate } from '../i18n'

export type SystemRole = 'ADMIN' | 'USER'

export interface CurrentUserProfile {
  userId: number
  userCode: string
  displayName: string
  systemRole: SystemRole
  mustChangePassword: boolean
}

export interface LoginPayload {
  loginId: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  displayName: string
  password: string
}

export interface AuthSessionResponse {
  accessToken: string
  currentUser: CurrentUserProfile
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export async function login(payload: LoginPayload): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/login', payload, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, translate('auth.loginFailed'))
}

export async function register(payload: RegisterPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/register', payload)
  unwrapApiResponse(data, translate('errors.register'))
}

export async function refreshSession(): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/refresh', null, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, translate('errors.loginExpired'))
}

export async function logout(): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/logout', null, {
    withCredentials: true,
  })
  unwrapApiResponse(data, translate('errors.signOut'))
}

export async function fetchCurrentUser(): Promise<CurrentUserProfile> {
  const { data } = await http.get<ApiResponse<CurrentUserProfile>>('/auth/me')

  return unwrapApiResponse(data, translate('errors.currentUser'))
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/account/change-password', payload)
  unwrapApiResponse(data, translate('errors.changePassword'))
}

function unwrapApiResponse<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success) {
    throw new Error(payload.message ?? fallbackMessage)
  }

  return payload.data
}
