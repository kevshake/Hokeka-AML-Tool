import { Box, Paper, Typography, List, ListItem, ListItemText, Chip } from "@mui/material";
import { apiClient } from "../../lib/apiClient";
import { useQuery } from "@tanstack/react-query";

export default function MessagesPage() {
  interface Message {
  id: string; // or number, depending on your data
  subject?: string;
  title?: string;
  body?: string;
  content?: string;
  read?: boolean;
}

const { data: messages, isLoading, isError, error } = useQuery<Message[]>({ ... });
// Then, display error if needed:
{isError && (
  <Typography sx={{ color: "text.error" }}>Error: {error instanceof Error ? error.message : 'Unknown error'}</Typography>
)}
    queryKey: ["messages"],
    queryFn: () => apiClient.get("messages").then(res => res.data),
  });

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Messages
      </Typography>

      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        {isLoading ? (
          <Box sx={{ p: 3 }}>
            <Typography sx={{ color: "text.disabled" }}>Loading messages...</Typography>
          </Box>
        ) : messages && Array.isArray(messages) && messages.length > 0 ? (
          <List>
            {messages.map((message) => (
              <ListItem
                key={message.id}
                sx={{
                  borderBottom: "1px solid rgba(0,0,0,0.1)",
                  "&:hover": { backgroundColor: "rgba(255,255,255,0.05)" },
                }}
              >
                <ListItemText
                  primary={message.subject || message.title || "Message"}
                  secondary={message.body || message.content || ""}
                  primaryTypographyProps={{ sx: { color: "text.primary" } }}
                  secondaryTypographyProps={{ sx: { color: "text.secondary" } }}
                />
                {!message.read && <Chip label="New" color="primary" size="small" />}
              </ListItem>
            ))}
          </List>
        ) : (
          <Box sx={{ p: 3 }}>
            <Typography sx={{ color: "text.disabled" }}>No messages</Typography>
          </Box>
        )}
      </Paper>
    </Box>
  );
}

