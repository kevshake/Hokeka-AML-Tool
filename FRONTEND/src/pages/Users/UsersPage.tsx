import { Routes, Route, Navigate } from "react-router-dom";
import TabNavigation from "../../components/Common/TabNavigation";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import UsersTab from "./UsersTab";
import RolesTab from "./RolesTab";

export default function UsersPage() {
  const tabs = [
    { label: "USERS", value: "users", path: "/users/list" },
    { label: "ROLES", value: "roles", path: "/users/roles" },
  ];

  return (
    <HokekaPageShell title="Users" subtitle="Manage platform users and role permissions" noCard>
      <GlassCard padding="md">
        <TabNavigation tabs={tabs} />
        <Routes>
          <Route path="/" element={<Navigate to="list" replace />} />
          <Route path="list" element={<UsersTab />} />
          <Route path="roles" element={<RolesTab />} />
        </Routes>
      </GlassCard>
    </HokekaPageShell>
  );
}
