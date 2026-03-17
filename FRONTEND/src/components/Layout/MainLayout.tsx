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
          p: 0.5,
        }}
      >
        <Header />
        <Box
          sx={{
            mt: "48px",
            width: "100%",
            height: "calc(100vh - 56px)",
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
}
