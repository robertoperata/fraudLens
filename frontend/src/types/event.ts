export type EventType = 'PAGE_VISIT' | 'FORM_SUBMIT' | 'LOGIN_ATTEMPT';

export interface Event {
  id: string;
  sessionId: string;
  type: EventType;
  url: string;
  durationMs: number;
  metadata: string | null; // raw JSON string from backend, nullable
}

export interface EventRequest {
  type: EventType;
  url: string;
  durationMs: number;
  metadata?: string; // optional raw JSON string
}
