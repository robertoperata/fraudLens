export type EventType = 'PAGE_VISIT' | 'FORM_SUBMIT' | 'LOGIN_ATTEMPT';

export interface Event {
  id: string;
  sessionId: string;
  type: EventType;
  url: string;
  durationMs: number;
  metadata: string | null; // raw JSON string from backend, nullable
  createdAt: string; // ISO 8601 instant, e.g. "2028-11-02T10:20:11Z"
}

export interface EventRequest {
  type: EventType;
  url: string;
  durationMs: number;
  metadata?: string; // optional raw JSON string
}
