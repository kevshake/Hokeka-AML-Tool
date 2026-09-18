import { Tabs, Tab, Box, Tooltip } from "@mui/material";
import { useNavigate, useLocation } from "react-router-dom";

interface TabItem {
    label: string;
    value: string;
    path: string;
}

interface TabNavigationProps {
    tabs: TabItem[];
    basePath?: string;
}

export default function TabNavigation({ tabs }: TabNavigationProps) {
    const navigate = useNavigate();
    const location = useLocation();

    const currentTab =
        tabs.find((tab) => location.pathname.startsWith(tab.path))?.value ||
        tabs[0]?.value;

    const handleChange = (_event: React.SyntheticEvent, newValue: string) => {
        const selectedTab = tabs.find((tab) => tab.value === newValue);
        if (selectedTab) {
            navigate(selectedTab.path);
        }
    };

    return (
        <Box sx={{ mb: 2.5 }}>
            {/* Tab styling comes from the shared theme (see ThemeContext). */}
            <Tabs value={currentTab} onChange={handleChange} sx={{ "& .MuiTab-root": { px: 2.25 } }}>
                {tabs.map((tab) => (
                    <Tooltip key={tab.value} title={`Navigate to ${tab.label}`} arrow>
                        <Tab label={tab.label} value={tab.value} />
                    </Tooltip>
                ))}
            </Tabs>
        </Box>
    );
}
