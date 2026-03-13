import { Box } from "@mui/material";
import Sidebar from "./Sidebar";
import Header from "./Header";

interface MainLayoutProps {
  children: React.ReactNode;
}

export default function MainLayout({ children }: MainLayoutProps) {
  return (
    <Box sx={{ display: "flex", minHeight: "100vh", backgroundColor: "background.default" }}>
      <Sidebar />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: "calc(100% - 240px)",
          ml: "12px",
          p: 2,
        }}
      >
        <Header />
        <Box
          sx={{
            mt: "64px",
            width: "100%",
            height: "calc(100vh - 110px)", // Adjusted for padding
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
}
