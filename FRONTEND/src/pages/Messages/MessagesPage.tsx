import { apiClient } from "../../lib/apiClient";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import { Loader2, Mail } from "lucide-react";

interface Message {
  id: string;
  subject?: string;
  title?: string;
  body?: string;
  content?: string;
  read?: boolean;
  createdAt?: string;
  sentAt?: string;
}

export default function MessagesPage() {
  const queryClient = useQueryClient();

  const { data: messages, isLoading, isError } = useQuery<Message[]>({
    queryKey: ["messages"],
    queryFn: () => apiClient.get<Message[]>("messages"),
  });

  const handleMarkRead = async (message: Message) => {
    if (message.read) return;
    try {
      await apiClient.put(`messages/${message.id}/read`, {});
      queryClient.setQueryData<Message[]>(["messages"], (prev) =>
        prev ? prev.map((m) => (m.id === message.id ? { ...m, read: true } : m)) : prev
      );
      queryClient.invalidateQueries({ queryKey: ["messages", "unread-count"] });
    } catch { /* best-effort */ }
  };

  return (
    <HokekaPageShell title="Messages" subtitle="System notifications and team communications" noCard>
      <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
        {isLoading ? (
          <div className="flex items-center gap-2 p-4 text-sm text-glass-muted">
            <Loader2 size={20} className="animate-spin" /> Loading messages...
          </div>
        ) : isError ? (
          <div className="m-3 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">
            Failed to load messages. Please try refreshing the page.
          </div>
        ) : messages && messages.length > 0 ? (
          <div>
            {messages.map((message) => (
              <button
                key={message.id}
                onClick={() => handleMarkRead(message)}
                className={`w-full border-0 border-b border-white/5 px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-white/[0.02] ${
                  message.read ? "" : "bg-burgundy-700/5"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className={`text-sm ${message.read ? "text-white/80" : "font-semibold text-white"}`}>
                        {message.subject || message.title || "(no subject)"}
                      </p>
                      {!message.read && <TwBadge variant="info">New</TwBadge>}
                    </div>
                    <p className="mt-0.5 truncate text-xs text-glass-muted">
                      {message.body || message.content || ""}
                    </p>
                    {(message.createdAt || message.sentAt) && (
                      <p className="mt-0.5 text-[11px] text-glass-muted/60">
                        {new Date(message.createdAt || message.sentAt!).toLocaleString()}
                      </p>
                    )}
                  </div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2 px-6 py-12">
            <Mail size={48} className="text-glass-muted/40" />
            <p className="text-sm font-medium text-glass-muted">No messages</p>
            <p className="text-xs text-glass-muted/60">System notifications and alerts will appear here.</p>
          </div>
        )}
      </div>
    </HokekaPageShell>
  );
}