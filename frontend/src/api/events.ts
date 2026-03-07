import apiClient from './client';
import type { Event, EventRequest } from '../types/event';

export const EventsApi = {
  list: (sessionId: string) =>
    apiClient.get<Event[]>(`/sessions/${sessionId}/events`).then((r) => r.data),

  create: (sessionId: string, body: EventRequest) =>
    apiClient.post<Event>(`/sessions/${sessionId}/events`, body).then((r) => r.data),

  delete: (sessionId: string, eventId: string) =>
    apiClient.delete(`/sessions/${sessionId}/events/${eventId}`).then((r) => r.data),
};
