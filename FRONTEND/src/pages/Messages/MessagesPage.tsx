import { Box, Paper, Typography, List, ListItem, ListItemText, Chip } from "@mui/material";
import { apiClient } from "../../lib/apiClient";
import { useQuery } from "@tanstack/react-query";

export default function MessagesPage() {
  const { data: messages, isLoading } = useQuery({
    queryKey: ["messages"],
    queryFn: () => apiClient.get("messages"),
  });

  return (
    <Box>
      <Typography variant="h5" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Messages
      </Typography>

      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        {isLoading ? (
          <Box sx={{ p: 3 }}>
            <Typography sx={{ color: "text.disabled" }}>Loading messages...</Typography>
          </Box>
        ) : messages && Array.isArray(messages) && messages.length > 0 ? (
          <List>
            {messages.map((message: any, idx: number) => (
              <ListItem
                key={idx}
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

