import { Grid, TextField } from "@mui/material";
import CrudTab from "./CrudTab";
import { usePspTariffs } from "../../../features/api/queries";
import { useCreatePspTariff, useDeletePspTariff } from "../../../features/api/mutations";
import { apiClient } from "../../../lib/apiClient";
import { useQueryClient } from "@tanstack/react-query";

const COLS = [
  { field: "chargeDescription", header: "Charge Description" },
  { field: "channelUsed", header: "Channel" },
  { field: "channelPartnerName", header: "Partner" },
  { field: "percentageTransactionCost", header: "% Cost" },
  { field: "absoluteTransactionCost", header: "Abs. Cost" },
];

const DEFAULT = {
  reportingDate: "",
  channelUsed: "",
  channelPartnerName: "",
  chargeDescription: "",
  percentageTransactionCost: "",
  absoluteTransactionCost: "",
};

interface Props { pspId: string }

export default function TariffsTab({ pspId }: Props) {
  const qc = useQueryClient();
  const { data = [], isLoading, isError } = usePspTariffs(pspId);
  const create = useCreatePspTariff(pspId);
  const remove = useDeletePspTariff(pspId);

  const handleUpdate = async (id: unknown, data: Record<string, unknown>) => {
    await apiClient.put(`psps/${pspId}/cbk/tariffs/${id}`, data);
    qc.invalidateQueries({ queryKey: ["psp", pspId, "tariffs"] });
  };

  return (
    <CrudTab
      title="Tariff Templates"
      description="Monthly tariff / pricing templates submitted to the CBK."
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
            <TextField fullWidth size="small" label="Charge Description *" value={form.chargeDescription}
              onChange={(e) => setForm((f) => ({ ...f, chargeDescription: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Channel Used" value={form.channelUsed}
              onChange={(e) => setForm((f) => ({ ...f, channelUsed: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Channel Partner Name" value={form.channelPartnerName}
              onChange={(e) => setForm((f) => ({ ...f, channelPartnerName: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Reporting Date" type="date"
              InputLabelProps={{ shrink: true }} value={form.reportingDate}
              onChange={(e) => setForm((f) => ({ ...f, reportingDate: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Percentage Transaction Cost (%)" type="number"
              value={form.percentageTransactionCost}
              onChange={(e) => setForm((f) => ({ ...f, percentageTransactionCost: e.target.value }))} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField fullWidth size="small" label="Absolute Transaction Cost" type="number"
              value={form.absoluteTransactionCost}
              onChange={(e) => setForm((f) => ({ ...f, absoluteTransactionCost: e.target.value }))} />
          </Grid>
        </Grid>
      )}
    />
  );
}
