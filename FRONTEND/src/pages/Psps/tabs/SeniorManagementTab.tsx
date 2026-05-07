import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspSeniorManagement } from "../../../features/api/queries";
import { useCreatePspSeniorManagement, useDeletePspSeniorManagement } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "officerNames", header: "Name" },
  { field: "designation", header: "Designation" },
  { field: "empType", header: "Employment Type" },
  { field: "dateOfEmp", header: "Date Employed" },
  { field: "nationality", header: "Nationality" },
];

const DEFAULT = {
  officerNames: "",
  gender: "",
  designation: "",
  dob: "",
  nationality: "",
  idNo: "",
  taxId: "",
  qualification: "",
  dateOfEmp: "",
  empType: "",
  retirementDt: "",
  externalAffliates: "",
  otherDisclosure: "",
};

interface Props { pspId: string }

export default function SeniorManagementTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspSeniorManagement(pspId);
  const create = useCreatePspSeniorManagement(pspId);
  const remove = useDeletePspSeniorManagement(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/senior-management/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "senior-management"] });
  };

  return (
    <CrudTab
      title="Senior Management"
      description="Senior management schedule submitted annually to the CBK."
      columns={COLS}
      rows={data}
      isLoading={isLoading}
      isError={isError}
      onCreate={(d) => create.mutateAsync(d)}
      onUpdate={handleUpdate}
      onDelete={(id) => remove.mutateAsync(id)}
      defaultFormData={DEFAULT}
      renderForm={(form, setForm) => (
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Officer Name *" value={form.officerNames}
              onChange={(e) => setForm((f) => ({ ...f, officerNames: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Gender" value={form.gender}
              onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Designation" value={form.designation}
              onChange={(e) => setForm((f) => ({ ...f, designation: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Employment Type" value={form.empType}
              onChange={(e) => setForm((f) => ({ ...f, empType: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Date of Birth" type="date"
              InputLabelProps={{ shrink: true }} value={form.dob}
              onChange={(e) => setForm((f) => ({ ...f, dob: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Nationality" value={form.nationality}
              onChange={(e) => setForm((f) => ({ ...f, nationality: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="ID Number" value={form.idNo}
              onChange={(e) => setForm((f) => ({ ...f, idNo: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Tax ID / KRA PIN" value={form.taxId}
              onChange={(e) => setForm((f) => ({ ...f, taxId: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Date of Employment" type="date"
              InputLabelProps={{ shrink: true }} value={form.dateOfEmp}
              onChange={(e) => setForm((f) => ({ ...f, dateOfEmp: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Retirement Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.retirementDt}
              onChange={(e) => setForm((f) => ({ ...f, retirementDt: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Qualification" value={form.qualification}
              onChange={(e) => setForm((f) => ({ ...f, qualification: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="External Affiliates" value={form.externalAffliates}
              onChange={(e) => setForm((f) => ({ ...f, externalAffliates: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Other Disclosures" multiline rows={2} value={form.otherDisclosure}
              onChange={(e) => setForm((f) => ({ ...f, otherDisclosure: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
