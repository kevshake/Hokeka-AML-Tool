import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspShareholders } from "../../../features/api/queries";
import { useCreatePspShareholder, useDeletePspShareholder } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "shareholderName", header: "Name" },
  { field: "shareholderType", header: "Type" },
  { field: "nationality", header: "Nationality" },
  { field: "percentageOfShare", header: "Share %" },
  { field: "onboardingDate", header: "Onboarded" },
];

const DEFAULT = {
  shareholderName: "",
  shareholderGender: "",
  shareholderType: "",
  dobOrRegDate: "",
  nationality: "",
  residentCountry: "",
  countryOfInc: "",
  idNoPassport: "",
  pin: "",
  contactNumber: "",
  qualifications: "",
  previousEmployment: "",
  onboardingDate: "",
  noOfSharesHeld: "",
  shareValue: "",
  percentageOfShare: "",
};

interface Props { pspId: string }

export default function ShareholdersTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspShareholders(pspId);
  const create = useCreatePspShareholder(pspId);
  const remove = useDeletePspShareholder(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/shareholders/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "shareholders"] });
  };

  return (
    <CrudTab
      title="Shareholders"
      description="Schedule of PSP shareholders submitted annually to the CBK."
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
            <TextField fullWidth size="small" label="Shareholder Name *" value={form.shareholderName}
              onChange={(e) => setForm((f) => ({ ...f, shareholderName: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Gender" value={form.shareholderGender}
              onChange={(e) => setForm((f) => ({ ...f, shareholderGender: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Shareholder Type" value={form.shareholderType}
              onChange={(e) => setForm((f) => ({ ...f, shareholderType: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Date of Birth / Reg. Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.dobOrRegDate}
              onChange={(e) => setForm((f) => ({ ...f, dobOrRegDate: e.target.value }))} />
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
            <TextField fullWidth size="small" label="Country of Incorporation" value={form.countryOfInc}
              onChange={(e) => setForm((f) => ({ ...f, countryOfInc: e.target.value }))} />
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
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Number of Shares" type="number" value={form.noOfSharesHeld}
              onChange={(e) => setForm((f) => ({ ...f, noOfSharesHeld: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Share Value" type="number" value={form.shareValue}
              onChange={(e) => setForm((f) => ({ ...f, shareValue: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField fullWidth size="small" label="Shareholding %" type="number" value={form.percentageOfShare}
              onChange={(e) => setForm((f) => ({ ...f, percentageOfShare: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Onboarding Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.onboardingDate}
              onChange={(e) => setForm((f) => ({ ...f, onboardingDate: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Qualifications" value={form.qualifications}
              onChange={(e) => setForm((f) => ({ ...f, qualifications: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Previous Employment" value={form.previousEmployment}
              onChange={(e) => setForm((f) => ({ ...f, previousEmployment: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
