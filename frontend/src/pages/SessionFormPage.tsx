import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SessionsApi } from '../api/sessions';
import type { SessionRequest, SessionStatus } from '../types/session';

const STATUSES: SessionStatus[] = ['SAFE', 'SUSPICIOUS', 'DANGEROUS'];

// "2028-11-02T10:20:11Z" → "2028-11-02T10:20:11" (datetime-local input format)
function toDatetimeLocal(isoZulu: string): string {
  return isoZulu.endsWith('Z') ? isoZulu.slice(0, -1) : isoZulu;
}

// "2028-11-02T10:20" or "2028-11-02T10:20:11" → "2028-11-02T10:20:11Z"
function toZuluTimestamp(datetimeLocal: string): string {
  const withSeconds = datetimeLocal.length === 16 ? `${datetimeLocal}:00` : datetimeLocal;
  return `${withSeconds}Z`;
}

function nowDatetimeLocal(): string {
  return toDatetimeLocal(new Date().toISOString().slice(0, 19) + 'Z');
}

export function SessionFormPage() {
  const { id } = useParams<{ id?: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<SessionRequest>({
    userId: '',
    ip: '',
    country: '',
    device: '',
    status: 'SAFE',
    timestamp: nowDatetimeLocal(),
  });
  const [error, setError] = useState<string | null>(null);

  const { data: existing } = useQuery({
    queryKey: ['session', id],
    queryFn: () => SessionsApi.get(id!),
    enabled: isEdit,
  });

  useEffect(() => {
    if (existing) {
      setForm({
        userId: existing.userId,
        ip: existing.ip,
        country: existing.country,
        device: existing.device,
        status: existing.status,
        timestamp: toDatetimeLocal(existing.timestamp),
      });
    }
  }, [existing]);

  const mutation = useMutation({
    mutationFn: (body: SessionRequest) =>
      isEdit ? SessionsApi.update(id!, body) : SessionsApi.create(body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['sessions'] });
      navigate(`/sessions/${data.id}`);
    },
    onError: () => setError('Failed to save session. Please check the fields and try again.'),
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    mutation.mutate({ ...form, timestamp: toZuluTimestamp(form.timestamp) });
  };

  const field = (label: string, key: keyof SessionRequest) => (
    <div key={key}>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      <input
        value={form[key] as string}
        onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
        required
        className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </div>
  );

  return (
    <div className="p-6 max-w-lg mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-6">
        {isEdit ? 'Edit Session' : 'New Session'}
      </h1>
      <form onSubmit={handleSubmit} className="bg-white shadow rounded-lg p-6 space-y-4">
        {field('User ID', 'userId')}
        {field('IP Address', 'ip')}
        {field('Country', 'country')}
        {field('Device', 'device')}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Timestamp <span className="text-gray-400 font-normal">(UTC / Zulu)</span>
          </label>
          <input
            type="datetime-local"
            step="1"
            value={form.timestamp}
            onChange={(e) => setForm((f) => ({ ...f, timestamp: e.target.value }))}
            required
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p className="mt-1 text-xs text-gray-400">
            Enter time in UTC. Sent to the backend as Zulu (Z) format, e.g. 2028-11-02T10:20:11Z.
          </p>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select
            value={form.status}
            onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as SessionStatus }))}
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex-1 border border-gray-300 text-gray-700 text-sm font-medium py-2 rounded-md hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="flex-1 bg-blue-600 text-white text-sm font-medium py-2 rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? 'Saving...' : isEdit ? 'Update' : 'Create'}
          </button>
        </div>
      </form>
    </div>
  );
}
