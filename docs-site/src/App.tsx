import { Navigate, Route, Routes } from "react-router-dom";
import { Layout } from "@/components/Layout";
import { CatalogPage } from "@/pages/CatalogPage";
import { DocPage } from "@/pages/DocPage";
import { HomePage } from "@/pages/HomePage";

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="docs/:slug" element={<DocPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
