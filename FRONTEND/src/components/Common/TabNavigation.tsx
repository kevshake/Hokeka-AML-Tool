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

export default function TabNavigation({ tabs, basePath }: TabNavigationProps) {
    const navigate = useNavigate();
    const location = useLocation();

    // Determine current tab based on location
    const currentTab = tabs.find(tab => location.pathname === tab.path)?.value || tabs[0]?.value;

    const handleChange = (_event: React.SyntheticEvent, newValue: string) => {
        const selectedTab = tabs.find(tab => tab.value === newValue);
        if (selectedTab) {
            navigate(selectedTab.path);
        }
    };

    return (
        <Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
            <Tabs
                value={currentTab}
                onChange={handleChange}
                sx={{
                    "& .MuiTab-root": {
                        textTransform: "none",
                        fontWeight: 500,
                        fontSize: "0.9rem",
                        color: "text.secondary",
                        "&.Mui-selected": {
                            color: "#8B4049",
                            fontWeight: 600,
                        },
                    },
                    "& .MuiTabs-indicator": {
                        backgroundColor: "#8B4049",
                        height: 3,
                        borderRadius: "3px 3px 0 0",
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
