import { Routes, Route, Navigate, useLocation, useNavigate } from "react-router-dom";
import GlassCard from "../../components/Common/GlassCard";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import SettingsTabBar from "../../components/Settings/SettingsTabBar";
import UsersTab from "./UsersTab";
import RolesTab from "./RolesTab";

const USER_TABS = [
  { id: "users", label: "Users" },
  { id: "roles", label: "Roles" },
];

export default function UsersPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const activeIndex = location.pathname.includes("/roles") ? 1 : 0;

  return (
    <HokekaPageShell title="Users" subtitle="Manage platform users and role permissions" noCard>
      <GlassCard padding="md" glowVariant="gold" static>
        <SettingsTabBar
          tabs={USER_TABS}
          activeIndex={activeIndex}
          onChange={(index) => navigate(index === 0 ? "/users/list" : "/users/roles")}
          ariaLabel="User management sections"
        />
        <Routes>
          <Route path="/" element={<Navigate to="list" replace />} />
          <Route path="list" element={<UsersTab />} />
          <Route path="roles" element={<RolesTab />} />
        </Routes>
      </GlassCard>
    </HokekaPageShell>
  );
}
