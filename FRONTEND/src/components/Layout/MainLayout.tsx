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
          ml: 0,
          p: 1,
        }}
      >
        <Header />
        <Box
          sx={{
            mt: "56px",
            width: "100%",
            height: "calc(100vh - 90px)", // Adjusted for compact padding
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
}
