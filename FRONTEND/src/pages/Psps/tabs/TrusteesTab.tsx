import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspTrustees } from "../../../features/api/queries";
import { useCreatePspTrustee, useDeletePspTrustee } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "trusteeNames", header: "Trustee Name" },
  { field: "trustCompName", header: "Trust Company" },
  { field: "nationality", header: "Nationality" },
  { field: "shareholdingPercentage", header: "Shareholding %" },
  { field: "contactNumber", header: "Contact" },
];

const DEFAULT = {
  trustCompName: "",
  directorsTrustComp: "",
  trusteeNames: "",
  trusteeGender: "",
  dob: "",
  nationality: "",
  residentCountry: "",
  idNoPassport: "",
  pin: "",
  contactNumber: "",
  qualifications: "",
  othersTrusteeships: "",
  disclosures: "",
  shareholders: "",
  shareholdingPercentage: "",
};

interface Props { pspId: string }

export default function TrusteesTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspTrustees(pspId);
  const create = useCreatePspTrustee(pspId);
  const remove = useDeletePspTrustee(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/trustees/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "trustees"] });
  };

  return (
    <CrudTab
      title="Trustees"
      description="Schedule of PSP trustees submitted annually to the CBK."
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
            <TextField fullWidth size="small" label="Trustee Name *" value={form.trusteeNames}
              onChange={(e) => setForm((f) => ({ ...f, trusteeNames: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Gender" value={form.trusteeGender}
              onChange={(e) => setForm((f) => ({ ...f, trusteeGender: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Trust Company Name" value={form.trustCompName}
              onChange={(e) => setForm((f) => ({ ...f, trustCompName: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Directors of Trust Company" value={form.directorsTrustComp}
              onChange={(e) => setForm((f) => ({ ...f, directorsTrustComp: e.target.value }))} />
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
            <TextField fullWidth size="small" label="Resident Country" value={form.residentCountry}
              onChange={(e) => setForm((f) => ({ ...f, residentCountry: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="ID / Passport No." value={form.idNoPassport}
              onChange={(e) => setForm((f) => ({ ...f, idNoPassport: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="KRA PIN" value={form.pin}
              onChange={(e) => setForm((f) => ({ ...f, pin: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Contact Number" value={form.contactNumber}
              onChange={(e) => setForm((f) => ({ ...f, contactNumber: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Shareholding %" type="number" value={form.shareholdingPercentage}
              onChange={(e) => setForm((f) => ({ ...f, shareholdingPercentage: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Qualifications" value={form.qualifications}
              onChange={(e) => setForm((f) => ({ ...f, qualifications: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Other Trusteeships" value={form.othersTrusteeships}
              onChange={(e) => setForm((f) => ({ ...f, othersTrusteeships: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Shareholders" multiline rows={2} value={form.shareholders}
              onChange={(e) => setForm((f) => ({ ...f, shareholders: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Disclosures" multiline rows={2} value={form.disclosures}
              onChange={(e) => setForm((f) => ({ ...f, disclosures: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
