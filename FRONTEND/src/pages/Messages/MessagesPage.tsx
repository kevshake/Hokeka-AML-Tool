import {
  Box,
  Paper,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  Chip,
  CircularProgress,
  Alert,
  Divider,
} from "@mui/material";
import { Mail as MailIcon } from "@mui/icons-material";
import { apiClient } from "../../lib/apiClient";
import { useQuery, useQueryClient } from "@tanstack/react-query";

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

  const {
    data: messages,
    isLoading,
    isError,
  } = useQuery<Message[]>({
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
    } catch {
      // best-effort mark-read; ignore errors silently
    }
  };

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Messages
      </Typography>

      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        {isLoading ? (
          <Box sx={{ p: 4, display: "flex", alignItems: "center", gap: 2 }}>
            <CircularProgress size={20} />
            <Typography sx={{ color: "text.secondary" }}>Loading messages…</Typography>
          </Box>
        ) : isError ? (
          <Box sx={{ p: 3 }}>
            <Alert severity="error">Failed to load messages. Please try refreshing the page.</Alert>
          </Box>
        ) : messages && messages.length > 0 ? (
          <List disablePadding>
            {messages.map((message, idx) => (
              <Box key={message.id}>
                {idx > 0 && <Divider />}
                <ListItemButton
                  onClick={() => handleMarkRead(message)}
                  sx={{
                    py: 2,
                    px: 3,
                    backgroundColor: message.read ? undefined : "rgba(25, 118, 210, 0.04)",
                    "&:hover": { backgroundColor: "rgba(255,255,255,0.05)" },
                  }}
                >
                  <ListItemText
                    primary={
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <Typography
                          variant="body1"
                          sx={{
                            color: "text.primary",
                            fontWeight: message.read ? 400 : 600,
                          }}
                        >
                          {message.subject || message.title || "(no subject)"}
                        </Typography>
                        {!message.read && <Chip label="New" color="primary" size="small" />}
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography
                          variant="body2"
                          sx={{ color: "text.secondary", mt: 0.5 }}
                          noWrap
                        >
                          {message.body || message.content || ""}
                        </Typography>
                        {(message.createdAt || message.sentAt) && (
                          <Typography variant="caption" sx={{ color: "text.disabled", mt: 0.5, display: "block" }}>
                            {new Date(message.createdAt || message.sentAt!).toLocaleString()}
                          </Typography>
                        )}
                      </Box>
                    }
                  />
                </ListItemButton>
              </Box>
            ))}
          </List>
        ) : (
          <Box
            sx={{
              p: 6,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 1.5,
            }}
          >
            <MailIcon sx={{ fontSize: 48, color: "text.disabled" }} />
            <Typography variant="body1" sx={{ color: "text.secondary", fontWeight: 500 }}>
              No messages
            </Typography>
            <Typography variant="body2" sx={{ color: "text.disabled" }}>
              System notifications and alerts will appear here.
            </Typography>
          </Box>
        )}
      </Paper>
    </Box>
  );
}
