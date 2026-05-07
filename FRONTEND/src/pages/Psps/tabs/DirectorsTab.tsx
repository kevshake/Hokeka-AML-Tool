import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspDirectors } from "../../../features/api/queries";
import {
  useCreatePspDirector,
  useDeletePspDirector,
} from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "directorNames", header: "Name" },
  { field: "typeOfDirector", header: "Type" },
  { field: "nationality", header: "Nationality" },
  { field: "dateOfAppointment", header: "Appointed" },
  { field: "contactNumber", header: "Contact" },
];

const DEFAULT = {
  directorNames: "",
  directorGender: "",
  typeOfDirector: "",
  dob: "",
  nationality: "",
  residentCountry: "",
  idNoPassport: "",
  pin: "",
  contactNumber: "",
  qualifications: "",
  otherDirectorships: "",
  dateOfAppointment: "",
  dateOfRetirement: "",
  retirementReason: "",
  disclosures: "",
};

interface Props { pspId: string }

export default function DirectorsTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspDirectors(pspId);
  const create = useCreatePspDirector(pspId);
  const remove = useDeletePspDirector(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/directors/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "directors"] });
  };

  return (
    <CrudTab
      title="Directors"
      description="Schedule of PSP directors submitted annually to the CBK."
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
            <TextField fullWidth size="small" label="Full Name *" value={form.directorNames}
              onChange={(e) => setForm((f) => ({ ...f, directorNames: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Gender" value={form.directorGender}
              onChange={(e) => setForm((f) => ({ ...f, directorGender: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Type of Director" value={form.typeOfDirector}
              onChange={(e) => setForm((f) => ({ ...f, typeOfDirector: e.target.value }))} />
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
            <TextField fullWidth size="small" label="Date of Appointment" type="date"
              InputLabelProps={{ shrink: true }} value={form.dateOfAppointment}
              onChange={(e) => setForm((f) => ({ ...f, dateOfAppointment: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Date of Retirement" type="date"
              InputLabelProps={{ shrink: true }} value={form.dateOfRetirement}
              onChange={(e) => setForm((f) => ({ ...f, dateOfRetirement: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Retirement Reason" value={form.retirementReason}
              onChange={(e) => setForm((f) => ({ ...f, retirementReason: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Qualifications" value={form.qualifications}
              onChange={(e) => setForm((f) => ({ ...f, qualifications: e.target.value }))} />
          </Grid>
          <Grid item xs={12}>
            <TextField fullWidth size="small" label="Other Directorships" value={form.otherDirectorships}
              onChange={(e) => setForm((f) => ({ ...f, otherDirectorships: e.target.value }))} />
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
