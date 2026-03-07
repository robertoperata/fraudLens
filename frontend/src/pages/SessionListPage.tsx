import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { SessionsApi } from '../api/sessions';
import { StatusBadge } from '../components/StatusBadge';
import { RiskScoreBadge } from '../components/RiskScoreBadge';
import { ConfirmModal } from '../components/ConfirmModal';
import type { SessionStatus } from '../types/session';

export function SessionListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<SessionStatus | ''>('');
  const [filterCountry, setFilterCountry] = useState('');

  const { data: sessions = [], isLoading } = useQuery({
    queryKey: ['sessions', filterStatus, filterCountry],
    queryFn: () =>
      SessionsApi.search({
        status: filterStatus || undefined,
        country: filterCountry || undefined,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => SessionsApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions'] }),
  });

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-900">Sessions</h1>
        <button
          onClick={() => navigate('/sessions/new')}
          className="inline-flex items-center gap-2 bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded-md hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" /> New Session
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value as SessionStatus | '')}
          className="border border-gray-300 rounded-md px-3 py-1.5 text-sm"
        >
          <option value="">All statuses</option>
          <option value="SAFE">SAFE</option>
          <option value="SUSPICIOUS">SUSPICIOUS</option>
          <option value="DANGEROUS">DANGEROUS</option>
        </select>
        <input
          placeholder="Filter by country"
          value={filterCountry}
          onChange={(e) => setFilterCountry(e.target.value)}
          className="border border-gray-300 rounded-md px-3 py-1.5 text-sm w-48"
        />
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500">Loading...</p>
      ) : (
        <div className="bg-white shadow rounded-lg overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['User', 'IP', 'Country', 'Device', 'Status', 'Risk Score', 'Timestamp', ''].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {sessions.map((s) => (
                <tr key={s.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <Link to={`/sessions/${s.id}`} className="text-blue-600 hover:underline font-medium">
                      {s.userId}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{s.ip}</td>
                  <td className="px-4 py-3 text-gray-600">{s.country}</td>
                  <td className="px-4 py-3 text-gray-600">{s.device}</td>
                  <td className="px-4 py-3"><StatusBadge status={s.status} /></td>
                  <td className="px-4 py-3"><RiskScoreBadge score={s.riskScore} /></td>
                  <td className="px-4 py-3 text-gray-500">{new Date(s.timestamp).toLocaleString()}</td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setDeleteId(s.id)}
                      className="text-red-400 hover:text-red-600"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
              {sessions.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-gray-400">No sessions found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {deleteId && (
        <ConfirmModal
          title="Delete session"
          message="This will permanently delete the session and all its events."
          onConfirm={() => { deleteMutation.mutate(deleteId); setDeleteId(null); }}
          onCancel={() => setDeleteId(null)}
        />
      )}
    </div>
  );
}
