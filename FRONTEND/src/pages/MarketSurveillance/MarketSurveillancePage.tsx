import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Activity, Ban, Cable, Plus, Scale, SearchCheck } from "lucide-react";
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  MenuItem, Paper, Snackbar, Tab, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Tabs, TextField, Typography,
} from "@mui/material";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import { useMarketOrders, useMarketSignals, useMultiAssetCustomers } from "../../features/api/queries";
import {
  useCancelMarketOrder, usePlaceMarketOrder, useRecordMarketExecution, useReviewMarketSignal,
} from "../../features/api/mutations";
import { apiClient } from "../../lib/apiClient";
import type {
  FixMessagePage, FixSession, MarketOrder, MarketSignalStatus, MarketSurveillanceSignal,
} from "../../types/marketSurveillance";

const orderInitial = {
  externalOrderId: "", customerId: "", accountReference: "", beneficialOwnerReference: "",
  instrumentId: "", symbol: "", side: "BUY", orderType: "LIMIT", quantity: "", limitPrice: "",
  currency: "KES", venue: "", marketCloseAt: "", referencePrice: "", deviceFingerprint: "",
};
const executionInitial = {
  externalExecutionId: "", externalOrderId: "", quantity: "", price: "", counterpartyCustomerId: "",
  counterpartyReference: "", counterpartyBeneficialOwnerReference: "", venue: "", marketCloseAt: "",
  referencePrice: "", settlementAccountReference: "",
};

function displayDate(value?: string) {
  return value ? new Date(value).toLocaleString() : "-";
}

export default function MarketSurveillancePage() {
  const [tab, setTab] = useState(0);
  const [dialog, setDialog] = useState<"order" | "execution" | "cancel" | "review" | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<MarketOrder | null>(null);
  const [selectedSignal, setSelectedSignal] = useState<MarketSurveillanceSignal | null>(null);
  const [orderForm, setOrderForm] = useState(orderInitial);
  const [executionForm, setExecutionForm] = useState(executionInitial);
  const [reviewForm, setReviewForm] = useState({ status: "UNDER_REVIEW" as MarketSignalStatus, notes: "" });
  const [cancelReason, setCancelReason] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const orders = useMarketOrders();
  const signals = useMarketSignals();
  const customers = useMultiAssetCustomers("", 0, 100);
  const placeOrder = usePlaceMarketOrder();
  const recordExecution = useRecordMarketExecution();
  const cancelOrder = useCancelMarketOrder();
  const reviewSignal = useReviewMarketSignal();
  const fixSessions = useQuery<FixSession[]>({
    queryKey: ["market-fix-sessions"],
    queryFn: () => apiClient.get("market-surveillance/fix/sessions"),
    enabled: tab === 2,
    refetchInterval: tab === 2 ? 10000 : false,
  });
  const fixMessages = useQuery<FixMessagePage>({
    queryKey: ["market-fix-messages"],
    queryFn: () => apiClient.get("market-surveillance/fix/messages?page=0&size=50"),
    enabled: tab === 2,
    refetchInterval: tab === 2 ? 10000 : false,
  });

  const closeDialog = () => setDialog(null);
  const mutationError = (value: unknown) => setError((value as { message?: string })?.message || "The request could not be completed.");

  const submitOrder = () => placeOrder.mutate({
    ...orderForm,
    customerId: Number(orderForm.customerId),
    quantity: Number(orderForm.quantity),
    limitPrice: orderForm.limitPrice ? Number(orderForm.limitPrice) : undefined,
    referencePrice: orderForm.referencePrice ? Number(orderForm.referencePrice) : undefined,
    marketCloseAt: orderForm.marketCloseAt || undefined,
  }, { onSuccess: (result) => { setNotice(`Order assessed with ${result.signals.length} market signal(s).`); setOrderForm(orderInitial); closeDialog(); }, onError: mutationError });

  const submitExecution = () => recordExecution.mutate({
    ...executionForm,
    quantity: Number(executionForm.quantity),
    price: Number(executionForm.price),
    counterpartyCustomerId: executionForm.counterpartyCustomerId ? Number(executionForm.counterpartyCustomerId) : undefined,
    referencePrice: executionForm.referencePrice ? Number(executionForm.referencePrice) : undefined,
    marketCloseAt: executionForm.marketCloseAt || undefined,
  }, { onSuccess: (result) => { setNotice(`Execution assessed with ${result.signals.length} market signal(s).`); setExecutionForm(executionInitial); closeDialog(); }, onError: mutationError });

  return <HokekaPageShell title="Market Surveillance" subtitle="Orders, executions, and market-integrity signals" noCard>
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, mb: 2, flexWrap: "wrap" }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)}><Tab label={`Signals (${signals.data?.totalElements ?? 0})`} /><Tab label={`Orders (${orders.data?.totalElements ?? 0})`} /><Tab label={`FIX feed (${fixMessages.data?.totalElements ?? 0})`} /></Tabs>
        <Box sx={{ display: "flex", gap: 1 }}>
          <Button variant="outlined" startIcon={<Activity size={16} />} onClick={() => setDialog("execution")}>Execution</Button>
          <Button variant="contained" startIcon={<Plus size={16} />} onClick={() => setDialog("order")}>Order</Button>
        </Box>
      </Box>

      {tab === 0 && <Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Created</TableCell><TableCell>Scenario</TableCell><TableCell>Customer</TableCell><TableCell>Severity</TableCell><TableCell>Score</TableCell><TableCell>Status</TableCell><TableCell>Evidence</TableCell><TableCell align="right">Action</TableCell></TableRow></TableHead><TableBody>
        {signals.isLoading && <TableRow><TableCell colSpan={8} align="center">Loading signals...</TableCell></TableRow>}
        {!signals.isLoading && !signals.data?.content.length && <TableRow><TableCell colSpan={8} align="center" sx={{ py: 5 }}>No market-abuse signals.</TableCell></TableRow>}
        {signals.data?.content.map((signal) => <TableRow key={signal.id} hover><TableCell>{displayDate(signal.createdAt)}</TableCell><TableCell><Link to={`/records/MARKET_SURVEILLANCE_SIGNAL/${signal.id}`}><Typography variant="body2" fontWeight={600} color="primary">{signal.scenarioCode.replaceAll("_", " ")}</Typography></Link><Typography variant="caption" color="text.secondary">{signal.description}</Typography></TableCell><TableCell><Link to={`/records/MULTI_ASSET_CUSTOMER/${signal.customerId}`}>{signal.customerName}</Link></TableCell><TableCell><Chip size="small" label={signal.severity} color={signal.severity === "CRITICAL" ? "error" : signal.severity === "HIGH" ? "warning" : "default"} /></TableCell><TableCell>{signal.score}</TableCell><TableCell>{signal.status.replaceAll("_", " ")}</TableCell><TableCell><Typography variant="caption" component="pre" sx={{ m: 0, maxWidth: 250, maxHeight: 72, overflow: "auto", whiteSpace: "pre-wrap" }}>{JSON.stringify(signal.evidence, null, 2)}</Typography></TableCell><TableCell align="right"><Button size="small" startIcon={<SearchCheck size={15} />} onClick={() => { setSelectedSignal(signal); setReviewForm({ status: "UNDER_REVIEW", notes: "" }); setDialog("review"); }}>Review</Button></TableCell></TableRow>)}
      </TableBody></Table></TableContainer></Paper>}

      {tab === 1 && <Paper variant="outlined"><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Placed</TableCell><TableCell>Order</TableCell><TableCell>Customer</TableCell><TableCell>Instrument</TableCell><TableCell>Side</TableCell><TableCell>Quantity</TableCell><TableCell>Price</TableCell><TableCell>Status</TableCell><TableCell align="right">Action</TableCell></TableRow></TableHead><TableBody>
        {orders.isLoading && <TableRow><TableCell colSpan={9} align="center">Loading orders...</TableCell></TableRow>}
        {!orders.isLoading && !orders.data?.content.length && <TableRow><TableCell colSpan={9} align="center" sx={{ py: 5 }}>No market orders.</TableCell></TableRow>}
        {orders.data?.content.map((order) => <TableRow key={order.id} hover><TableCell>{displayDate(order.placedAt)}</TableCell><TableCell><Link to={`/records/MARKET_ORDER/${order.id}`}><Typography variant="body2" fontFamily="monospace" color="primary">{order.externalOrderId}</Typography></Link><Typography variant="caption" color="text.secondary">{order.accountReference}</Typography></TableCell><TableCell><Link to={`/records/MULTI_ASSET_CUSTOMER/${order.customerId}`}>{order.customerName}</Link></TableCell><TableCell>{order.symbol || order.instrumentId}<Typography variant="caption" display="block" color="text.secondary">{order.venue || "Venue unavailable"}</Typography></TableCell><TableCell><Chip size="small" label={order.side} color={order.side === "BUY" ? "success" : "warning"} variant="outlined" /></TableCell><TableCell>{order.executedQuantity} / {order.quantity}</TableCell><TableCell>{order.limitPrice ?? "Market"} {order.currency}</TableCell><TableCell>{order.status.replaceAll("_", " ")}</TableCell><TableCell align="right">{(order.status === "OPEN" || order.status === "PARTIALLY_FILLED") && <Button size="small" color="error" startIcon={<Ban size={15} />} onClick={() => { setSelectedOrder(order); setCancelReason(""); setDialog("cancel"); }}>Cancel</Button>}</TableCell></TableRow>)}
      </TableBody></Table></TableContainer></Paper>}

      {tab === 2 && <Box sx={{ display: "grid", gap: 2 }}>
        <Paper variant="outlined">
          <Box sx={{ px: 2, py: 1.5, display: "flex", alignItems: "center", gap: 1 }}><Cable size={17} /><Typography variant="subtitle2">FIX sessions</Typography></Box>
          <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Session</TableCell><TableCell>PSP</TableCell><TableCell>Mode</TableCell><TableCell>State</TableCell><TableCell>Sender seq.</TableCell><TableCell>Target seq.</TableCell></TableRow></TableHead><TableBody>
            {fixSessions.isLoading && <TableRow><TableCell colSpan={6} align="center">Loading FIX sessions...</TableCell></TableRow>}
            {!fixSessions.isLoading && !fixSessions.data?.length && <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4 }}>FIX is disabled or no sessions are configured.</TableCell></TableRow>}
            {fixSessions.data?.map((session) => <TableRow key={session.sessionId}><TableCell><Typography variant="body2" fontFamily="monospace">{session.sessionId}</Typography></TableCell><TableCell>{session.pspId}</TableCell><TableCell sx={{ textTransform: "capitalize" }}>{session.connectionType}</TableCell><TableCell><Chip size="small" label={session.loggedOn ? "Logged on" : "Disconnected"} color={session.loggedOn ? "success" : "default"} /></TableCell><TableCell>{session.expectedSenderSequence ?? "-"}</TableCell><TableCell>{session.expectedTargetSequence ?? "-"}</TableCell></TableRow>)}
          </TableBody></Table></TableContainer>
        </Paper>
        <Paper variant="outlined">
          <Box sx={{ px: 2, py: 1.5 }}><Typography variant="subtitle2">Inbound message evidence</Typography><Typography variant="caption" color="text.secondary">Sanitized fields and SHA-256 receipts; credentials and raw messages are not stored.</Typography></Box>
          <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Received</TableCell><TableCell>Session / seq.</TableCell><TableCell>Type</TableCell><TableCell>Reference</TableCell><TableCell>Outcome</TableCell><TableCell>Linked records</TableCell><TableCell>Evidence</TableCell></TableRow></TableHead><TableBody>
            {fixMessages.isLoading && <TableRow><TableCell colSpan={7} align="center">Loading FIX evidence...</TableCell></TableRow>}
            {!fixMessages.isLoading && !fixMessages.data?.content.length && <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4 }}>No FIX application messages have been received.</TableCell></TableRow>}
            {fixMessages.data?.content.map((event) => <TableRow key={event.id} hover><TableCell>{displayDate(event.receivedAt)}</TableCell><TableCell><Link to={`/records/FIX_MESSAGE_EVENT/${event.id}`}><Typography variant="caption" fontFamily="monospace" color="primary">{event.sessionId}</Typography></Link><Typography variant="caption" display="block" color="text.secondary">#{event.messageSequenceNumber}</Typography></TableCell><TableCell>{event.messageType}</TableCell><TableCell>{event.businessReference || "-"}</TableCell><TableCell><Chip size="small" label={event.outcome} color={event.outcome === "ACCEPTED" ? "success" : event.outcome === "REJECTED" ? "error" : event.outcome === "IGNORED" ? "warning" : "default"} /></TableCell><TableCell><Box sx={{ display: "flex", gap: 1 }}>{event.marketOrderId && <Link to={`/records/MARKET_ORDER/${event.marketOrderId}`}>Order</Link>}{event.marketExecutionId && <Link to={`/records/MARKET_EXECUTION/${event.marketExecutionId}`}>Execution</Link>}</Box></TableCell><TableCell><Typography variant="caption" component="div" sx={{ maxWidth: 280 }}><span style={{ fontFamily: "monospace", wordBreak: "break-all" }}>{event.messageHash}</span>{event.errorMessage && <span style={{ display: "block", color: "var(--mui-palette-error-main)" }}>{event.errorMessage}</span>}</Typography></TableCell></TableRow>)}
          </TableBody></Table></TableContainer>
        </Paper>
      </Box>}

      <Dialog open={dialog === "order"} onClose={closeDialog} fullWidth maxWidth="md"><DialogTitle>Record market order</DialogTitle><DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" }, gap: 2, pt: 1 }}>
        <TextField label="External order ID" required value={orderForm.externalOrderId} onChange={(e) => setOrderForm({ ...orderForm, externalOrderId: e.target.value })} />
        <TextField select label="Customer" required value={orderForm.customerId} onChange={(e) => setOrderForm({ ...orderForm, customerId: e.target.value })}>{customers.data?.content.map((customer) => <MenuItem key={customer.id} value={customer.id}>{customer.displayName} / {customer.externalCustomerId}</MenuItem>)}</TextField>
        <TextField label="Account reference" required value={orderForm.accountReference} onChange={(e) => setOrderForm({ ...orderForm, accountReference: e.target.value })} />
        <TextField label="Beneficial owner reference" value={orderForm.beneficialOwnerReference} onChange={(e) => setOrderForm({ ...orderForm, beneficialOwnerReference: e.target.value })} />
        <TextField label="Instrument ID" required value={orderForm.instrumentId} onChange={(e) => setOrderForm({ ...orderForm, instrumentId: e.target.value })} />
        <TextField label="Symbol" value={orderForm.symbol} onChange={(e) => setOrderForm({ ...orderForm, symbol: e.target.value })} />
        <TextField select label="Side" value={orderForm.side} onChange={(e) => setOrderForm({ ...orderForm, side: e.target.value })}><MenuItem value="BUY">Buy</MenuItem><MenuItem value="SELL">Sell</MenuItem></TextField>
        <TextField select label="Order type" value={orderForm.orderType} onChange={(e) => setOrderForm({ ...orderForm, orderType: e.target.value })}><MenuItem value="LIMIT">Limit</MenuItem><MenuItem value="MARKET">Market</MenuItem><MenuItem value="STOP">Stop</MenuItem></TextField>
        <TextField label="Quantity" type="number" required value={orderForm.quantity} onChange={(e) => setOrderForm({ ...orderForm, quantity: e.target.value })} />
        <TextField label="Limit price" type="number" value={orderForm.limitPrice} onChange={(e) => setOrderForm({ ...orderForm, limitPrice: e.target.value })} />
        <TextField label="Currency" required value={orderForm.currency} onChange={(e) => setOrderForm({ ...orderForm, currency: e.target.value.toUpperCase() })} />
        <TextField label="Venue" value={orderForm.venue} onChange={(e) => setOrderForm({ ...orderForm, venue: e.target.value })} />
        <TextField label="Reference price" type="number" value={orderForm.referencePrice} onChange={(e) => setOrderForm({ ...orderForm, referencePrice: e.target.value })} />
        <TextField label="Market close" type="datetime-local" value={orderForm.marketCloseAt} InputLabelProps={{ shrink: true }} onChange={(e) => setOrderForm({ ...orderForm, marketCloseAt: e.target.value })} />
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!orderForm.externalOrderId || !orderForm.customerId || !orderForm.accountReference || !orderForm.instrumentId || !orderForm.quantity || placeOrder.isPending} onClick={submitOrder}>Assess order</Button></DialogActions></Dialog>

      <Dialog open={dialog === "execution"} onClose={closeDialog} fullWidth maxWidth="md"><DialogTitle>Record execution</DialogTitle><DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" }, gap: 2, pt: 1 }}>
        <TextField label="External execution ID" required value={executionForm.externalExecutionId} onChange={(e) => setExecutionForm({ ...executionForm, externalExecutionId: e.target.value })} />
        <TextField select label="Order" required value={executionForm.externalOrderId} onChange={(e) => { const order = orders.data?.content.find((item) => item.externalOrderId === e.target.value); setExecutionForm({ ...executionForm, externalOrderId: e.target.value, venue: order?.venue || executionForm.venue }); }}>{orders.data?.content.filter((order) => order.status === "OPEN" || order.status === "PARTIALLY_FILLED").map((order) => <MenuItem key={order.id} value={order.externalOrderId}>{order.externalOrderId} / {order.symbol || order.instrumentId}</MenuItem>)}</TextField>
        <TextField label="Quantity" type="number" required value={executionForm.quantity} onChange={(e) => setExecutionForm({ ...executionForm, quantity: e.target.value })} />
        <TextField label="Execution price" type="number" required value={executionForm.price} onChange={(e) => setExecutionForm({ ...executionForm, price: e.target.value })} />
        <TextField select label="Counterparty customer" value={executionForm.counterpartyCustomerId} onChange={(e) => setExecutionForm({ ...executionForm, counterpartyCustomerId: e.target.value })}><MenuItem value="">External or unknown</MenuItem>{customers.data?.content.map((customer) => <MenuItem key={customer.id} value={customer.id}>{customer.displayName}</MenuItem>)}</TextField>
        <TextField label="Counterparty reference" value={executionForm.counterpartyReference} onChange={(e) => setExecutionForm({ ...executionForm, counterpartyReference: e.target.value })} />
        <TextField label="Counterparty owner reference" value={executionForm.counterpartyBeneficialOwnerReference} onChange={(e) => setExecutionForm({ ...executionForm, counterpartyBeneficialOwnerReference: e.target.value })} />
        <TextField label="Settlement account" value={executionForm.settlementAccountReference} onChange={(e) => setExecutionForm({ ...executionForm, settlementAccountReference: e.target.value })} />
        <TextField label="Reference price" type="number" value={executionForm.referencePrice} onChange={(e) => setExecutionForm({ ...executionForm, referencePrice: e.target.value })} />
        <TextField label="Market close" type="datetime-local" value={executionForm.marketCloseAt} InputLabelProps={{ shrink: true }} onChange={(e) => setExecutionForm({ ...executionForm, marketCloseAt: e.target.value })} />
      </Box></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" disabled={!executionForm.externalExecutionId || !executionForm.externalOrderId || !executionForm.quantity || !executionForm.price || recordExecution.isPending} onClick={submitExecution}>Assess execution</Button></DialogActions></Dialog>

      <Dialog open={dialog === "cancel"} onClose={closeDialog} fullWidth maxWidth="sm"><DialogTitle>Cancel order</DialogTitle><DialogContent><Typography variant="body2" sx={{ mb: 2 }}>{selectedOrder?.externalOrderId}</Typography><TextField label="Cancellation reason" required fullWidth multiline minRows={3} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} /></DialogContent><DialogActions><Button onClick={closeDialog}>Back</Button><Button color="error" variant="contained" disabled={!cancelReason.trim() || cancelOrder.isPending} onClick={() => selectedOrder && cancelOrder.mutate({ externalOrderId: selectedOrder.externalOrderId, reason: cancelReason.trim() }, { onSuccess: (result) => { setNotice(`Cancellation assessed with ${result.signals.length} market signal(s).`); closeDialog(); }, onError: mutationError })}>Cancel and assess</Button></DialogActions></Dialog>

      <Dialog open={dialog === "review"} onClose={closeDialog} fullWidth maxWidth="sm"><DialogTitle>Review market signal</DialogTitle><DialogContent><Typography variant="body2" fontWeight={600} sx={{ mb: 2 }}>{selectedSignal?.scenarioCode.replaceAll("_", " ")}</Typography><TextField select label="Outcome" fullWidth value={reviewForm.status} onChange={(e) => setReviewForm({ ...reviewForm, status: e.target.value as MarketSignalStatus })}><MenuItem value="UNDER_REVIEW">Under review</MenuItem><MenuItem value="ESCALATED">Escalated</MenuItem><MenuItem value="DISMISSED">Dismissed</MenuItem></TextField><TextField label="Review notes" required fullWidth multiline minRows={3} value={reviewForm.notes} onChange={(e) => setReviewForm({ ...reviewForm, notes: e.target.value })} sx={{ mt: 2 }} /></DialogContent><DialogActions><Button onClick={closeDialog}>Cancel</Button><Button variant="contained" startIcon={<Scale size={16} />} disabled={!reviewForm.notes.trim() || reviewSignal.isPending} onClick={() => selectedSignal && reviewSignal.mutate({ id: selectedSignal.id, ...reviewForm }, { onSuccess: () => { setNotice("Signal review recorded."); closeDialog(); }, onError: mutationError })}>Record outcome</Button></DialogActions></Dialog>

      <Snackbar open={!!notice} autoHideDuration={5000} onClose={() => setNotice("")}><Alert severity="success" onClose={() => setNotice("")}>{notice}</Alert></Snackbar>
      <Snackbar open={!!error} autoHideDuration={8000} onClose={() => setError("")}><Alert severity="error" onClose={() => setError("")}>{error}</Alert></Snackbar>
    </Box>
  </HokekaPageShell>;
}
