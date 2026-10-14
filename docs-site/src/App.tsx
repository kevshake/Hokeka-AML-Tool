import { Navigate, Route, Routes } from "react-router-dom";
import { Layout } from "@/components/Layout";
import { CatalogPage } from "@/pages/CatalogPage";
import { DocPage } from "@/pages/DocPage";
import { HomePage } from "@/pages/HomePage";
import { ManagementShell } from "@/components/management/ManagementShell";
import { ManagementHomePage } from "@/pages/management/ManagementHomePage";
import { ManagementEndpointPage } from "@/pages/management/ManagementEndpointPage";
import { ManagementReferenceIndexPage } from "@/pages/management/ManagementReferenceIndexPage";

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="docs/:slug" element={<DocPage />} />
      </Route>
      <Route path="management-api" element={<ManagementShell />}>
        <Route index element={<ManagementHomePage />} />
        <Route path="reference" element={<ManagementReferenceIndexPage />} />
        <Route path="reference/:slug" element={<ManagementEndpointPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
