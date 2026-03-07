import type { SessionStatus } from '../types/session';

const styles: Record<SessionStatus, string> = {
  SAFE: 'bg-green-100 text-green-800',
  SUSPICIOUS: 'bg-yellow-100 text-yellow-800',
  DANGEROUS: 'bg-red-100 text-red-800',
};

export function StatusBadge({ status }: { status: SessionStatus }) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}>
      {status}
    </span>
  );
}
