export type SessionStatus = 'SAFE' | 'SUSPICIOUS' | 'DANGEROUS';

export interface Session {
  id: string;
  userId: string;
  ip: string;
  country: string;
  device: string;
  status: SessionStatus;
  riskScore: number;
  timestamp: string;
}

export interface SessionRequest {
  userId: string;
  ip: string;
  country: string;
  device: string;
  status: SessionStatus;
  timestamp: string; // ISO 8601 Zulu, e.g. "2028-11-02T10:20:11Z"
}

export interface SessionSearchRequest {
  status?: SessionStatus;
  country?: string;
  userId?: string;
  ip?: string;
  sortBy?: 'timestamp' | 'id';
  sortDir?: 'asc' | 'desc';
}
