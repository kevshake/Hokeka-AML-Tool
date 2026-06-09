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
        <Box sx={{ borderBottom: 1, borderColor: "rgba(255,255,255,0.12)", mb: 2 }}>
            <Tabs
                value={currentTab}
                onChange={handleChange}
                sx={{
                    minHeight: 40,
                    "& .MuiTab-root": {
                        textTransform: "none",
                        fontWeight: 500,
                        fontSize: "0.8rem",
                        color: "rgba(255,255,255,0.55)",
                        minHeight: 40,
                        px: 2,
                        "&.Mui-selected": {
                            color: "#C9A96E",
                            fontWeight: 600,
                        },
                    },
                    "& .MuiTabs-indicator": {
                        backgroundColor: "#C9A96E",
                        height: 2,
                        borderRadius: "2px 2px 0 0",
                    },
                }}
            >
                {tabs.map((tab) => (
                    <Tooltip key={tab.value} title={`Navigate to ${tab.label}`} arrow>
                        <Tab label={tab.label} value={tab.value} />
                    </Tooltip>
                ))}
            </Tabs>
        </Box>
    );
}
