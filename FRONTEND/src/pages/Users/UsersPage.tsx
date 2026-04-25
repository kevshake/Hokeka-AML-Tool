import { Box, Typography } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import UsersTab from "./UsersTab";
import RolesTab from "./RolesTab";

export default function UsersPage() {
  const tabs = [
    { label: "USERS", value: "users", path: "/users/list" },
    { label: "ROLES", value: "roles", path: "/users/roles" },
  ];

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 0.5, fontWeight: 600 }}>
        User Management
      </Typography>

      <TabNavigation tabs={tabs} />

      <Routes>
        <Route path="/" element={<Navigate to="/users/list" replace />} />
        <Route path="/list" element={<UsersTab />} />
        <Route path="/roles" element={<RolesTab />} />
      </Routes>
    </Box>
  );
}

