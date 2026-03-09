import { useState, type FormEvent } from 'react';
import ReactMarkdown from 'react-markdown';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Sparkles, ArrowLeft, Plus, X } from 'lucide-react';
import { SessionsApi } from '../api/sessions';
import { EventsApi } from '../api/events';
import { StatusBadge } from '../components/StatusBadge';
import { RiskScoreBadge } from '../components/RiskScoreBadge';
import { EventTimeline } from '../components/EventTimeline';
import type { EventRequest, EventType } from '../types/event';

const EVENT_TYPES: EventType[] = ['PAGE_VISIT', 'FORM_SUBMIT', 'LOGIN_ATTEMPT'];

const EMPTY_FORM: EventRequest = { type: 'PAGE_VISIT', url: '', durationMs: 0, metadata: '' };

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [summary, setSummary] = useState<string | null>(null);
  const [showAddEvent, setShowAddEvent] = useState(false);
  const [eventForm, setEventForm] = useState<EventRequest>(EMPTY_FORM);
  const [eventError, setEventError] = useState<string | null>(null);

  const { data: session, isLoading: sessionLoading } = useQuery({
    queryKey: ['session', id],
    queryFn: () => SessionsApi.get(id!),
    enabled: !!id,
  });

  const { data: events = [], isLoading: eventsLoading } = useQuery({
    queryKey: ['events', id],
    queryFn: () => EventsApi.list(id!),
    enabled: !!id,
  });

  const summaryMutation = useMutation({
    mutationFn: () => SessionsApi.riskSummary(id!),
    onSuccess: (data) => setSummary(data.summary),
  });

  const addEventMutation = useMutation({
    mutationFn: (body: EventRequest) => EventsApi.create(id!, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events', id] });
      setShowAddEvent(false);
      setEventForm(EMPTY_FORM);
      setEventError(null);
    },
    onError: () => setEventError('Failed to add event. Check all required fields.'),
  });

  const handleAddEvent = (e: FormEvent) => {
    e.preventDefault();
    setEventError(null);
    const trimmedMetadata = eventForm.metadata?.trim() || undefined;
    if (trimmedMetadata) {
      try {
        JSON.parse(trimmedMetadata);
      } catch {
        setEventError('Metadata must be valid JSON (e.g. {"formFields": ["card_number"]})');
        return;
      }
    }
    addEventMutation.mutate({ ...eventForm, metadata: trimmedMetadata });
  };

  if (sessionLoading) return <p className="p-6 text-sm text-gray-500">Loading...</p>;
  if (!session) return <p className="p-6 text-sm text-red-500">Session not found.</p>;

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-gray-600">
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h1 className="text-xl font-bold text-gray-900">Session Detail</h1>
      </div>

      {/* Metadata */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <StatusBadge status={session.status} />
            <RiskScoreBadge score={session.riskScore} />
          </div>
          <Link
            to={`/sessions/${id}/edit`}
            className="inline-flex items-center gap-1 text-sm text-blue-600 hover:underline"
          >
            <Pencil className="w-4 h-4" /> Edit
          </Link>
        </div>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          {[
            ['User ID', session.userId],
            ['IP', session.ip],
            ['Country', session.country],
            ['Device', session.device],
            ['Timestamp', new Date(session.timestamp).toLocaleString()],
          ].map(([label, value]) => (
            <>
              <dt key={`dt-${label}`} className="font-medium text-gray-500">{label}</dt>
              <dd key={`dd-${label}`} className="text-gray-900">{value}</dd>
            </>
          ))}
        </dl>
      </div>

      {/* AI Risk Summary */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-gray-900">AI Risk Summary</h2>
          <button
            onClick={() => summaryMutation.mutate()}
            disabled={summaryMutation.isPending}
            className="inline-flex items-center gap-1 text-sm bg-purple-600 text-white px-3 py-1.5 rounded-md hover:bg-purple-700 disabled:opacity-50"
          >
            <Sparkles className="w-3.5 h-3.5" />
            {summaryMutation.isPending ? 'Generating...' : 'Generate'}
          </button>
        </div>
        {summary ? (
          <div className="prose prose-sm max-w-none text-gray-700">
            <ReactMarkdown>{summary}</ReactMarkdown>
          </div>
        ) : (
          <p className="text-sm text-gray-400">Click Generate to get an AI-powered risk assessment.</p>
        )}
        {summaryMutation.isError && (
          <p className="text-sm text-red-500 mt-2">Failed to generate summary. Try again.</p>
        )}
      </div>

      {/* Event Timeline */}
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-gray-900">Events</h2>
          {!showAddEvent && (
            <button
              onClick={() => setShowAddEvent(true)}
              className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800"
            >
              <Plus className="w-4 h-4" /> Add Event
            </button>
          )}
        </div>

        {/* Add Event Form */}
        {showAddEvent && (
          <form onSubmit={handleAddEvent} className="mb-6 border border-gray-200 rounded-lg p-4 space-y-3 bg-gray-50">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-semibold text-gray-600 uppercase tracking-wide">New Event</span>
              <button
                type="button"
                onClick={() => { setShowAddEvent(false); setEventForm(EMPTY_FORM); setEventError(null); }}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Type</label>
                <select
                  value={eventForm.type}
                  onChange={(e) => setEventForm((f) => ({ ...f, type: e.target.value as EventType }))}
                  className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {EVENT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Duration (ms)</label>
                <input
                  type="number"
                  min={0}
                  value={eventForm.durationMs}
                  onChange={(e) => setEventForm((f) => ({ ...f, durationMs: Number(e.target.value) }))}
                  required
                  className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">URL</label>
              <input
                type="text"
                value={eventForm.url}
                onChange={(e) => setEventForm((f) => ({ ...f, url: e.target.value }))}
                required
                placeholder="/checkout/payment"
                pattern="^/\S*$"
                title="Must be an absolute path starting with / (e.g. /checkout/payment)"
                className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">
                Metadata <span className="text-gray-400 font-normal">(optional JSON)</span>
              </label>
              <textarea
                value={eventForm.metadata ?? ''}
                onChange={(e) => setEventForm((f) => ({ ...f, metadata: e.target.value }))}
                rows={2}
                placeholder='{"formFields": ["card_number"]}'
                className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              />
            </div>

            {eventError && <p className="text-xs text-red-600">{eventError}</p>}

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => { setShowAddEvent(false); setEventForm(EMPTY_FORM); setEventError(null); }}
                className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-100"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={addEventMutation.isPending}
                className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
              >
                {addEventMutation.isPending ? 'Adding...' : 'Add Event'}
              </button>
            </div>
          </form>
        )}

        {eventsLoading ? (
          <p className="text-sm text-gray-500">Loading events...</p>
        ) : (
          <EventTimeline events={events} />
        )}
      </div>
    </div>
  );
}
