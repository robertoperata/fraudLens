import { LogIn, FileText, Eye } from 'lucide-react';
import type { Event, EventType } from '../types/event';

const iconMap: Record<EventType, React.ElementType> = {
  LOGIN_ATTEMPT: LogIn,
  FORM_SUBMIT: FileText,
  PAGE_VISIT: Eye,
};

function tryFormatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function EventTimeline({ events }: { events: Event[] }) {
  if (events.length === 0) {
    return <p className="text-sm text-gray-500">No events recorded.</p>;
  }

  return (
    <ol className="relative border-l border-gray-200 ml-3">
      {events.map((event) => {
        const Icon = iconMap[event.type] ?? Eye;
        return (
          <li key={event.id} className="mb-6 ml-6">
            <span className="absolute -left-3 flex items-center justify-center w-6 h-6 bg-blue-100 rounded-full ring-4 ring-white">
              <Icon className="w-3 h-3 text-blue-600" />
            </span>
            <div className="flex items-center gap-2 mb-0.5">
              <span className="text-sm font-medium text-gray-900">{event.type}</span>
              <span className="text-xs text-gray-400">{event.durationMs} ms</span>
              <time className="text-xs text-gray-400" dateTime={event.createdAt}>
                {new Date(event.createdAt).toLocaleString()}
              </time>
            </div>
            <p className="text-xs text-gray-500 mb-1 truncate max-w-sm" title={event.url}>
              {event.url}
            </p>
            {event.metadata && (
              <pre className="text-xs text-gray-600 bg-gray-50 rounded p-2 overflow-x-auto">
                {tryFormatJson(event.metadata)}
              </pre>
            )}
          </li>
        );
      })}
    </ol>
  );
}
