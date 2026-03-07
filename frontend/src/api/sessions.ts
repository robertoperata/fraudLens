import apiClient from './client';
import type { Session, SessionRequest, SessionSearchRequest } from '../types/session';

export const SessionsApi = {
  list: () =>
    apiClient.get<Session[]>('/sessions').then((r) => r.data),

  get: (id: string) =>
    apiClient.get<Session>(`/sessions/${id}`).then((r) => r.data),

  create: (body: SessionRequest) =>
    apiClient.post<Session>('/sessions', body).then((r) => r.data),

  update: (id: string, body: SessionRequest) =>
    apiClient.put<Session>(`/sessions/${id}`, body).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete(`/sessions/${id}`).then((r) => r.data),

  search: (body: SessionSearchRequest) =>
    apiClient.post<Session[]>('/sessions/search', body).then((r) => r.data),

  riskSummary: (id: string) =>
    apiClient.post<{ summary: string }>(`/sessions/${id}/risk-summary`).then((r) => r.data),
};
