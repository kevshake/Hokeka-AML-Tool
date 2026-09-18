export interface SubscriptionRequest {
  pspId: number;
  tierCode: string;
  billingCycle: 'MONTHLY' | 'ANNUAL';
  billingCurrency: string;
  discountPercentage?: number;
  contractStart: string; // ISO date
  contractEnd?: string;
  trialEndsAt?: string;
  notes?: string;
}

export interface RevenueSummary {
  currentMonthRevenuePaid: number;
  currentMonthRevenueExpected: number;
  overdueAmount: number;
  activeSubscriptions: number;
  paidInvoicesThisMonth: number;
  overdueInvoicesCount: number;
  currency: string;
}

export interface InvoiceLineItem {
  lineItemId: number;
  lineNumber: number;
  description: string;
  serviceType: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Invoice {
  invoiceId: number;
  invoiceNumber: string;
  pspId: number;
  pspName?: string;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  subtotal: number;
  totalAmount: number;
  currency: string;
  status: string;
  dueDate: string;
  paidAt?: string;
  paymentMethod?: string;
  paymentReference?: string;
  lineItems?: InvoiceLineItem[];
}

export interface Subscription {
  subscriptionId: number;
  pspId: number;
  pspName?: string;
  tierCode: string;
  tierName?: string;
  billingCycle: string;
  billingCurrency: string;
  status: string;
  contractStart: string;
  contractEnd?: string;
  trialEndsAt?: string;
  discountPercentage?: number;
  monthlyFeeUsd?: number;
}

export interface UsageBreakdownItem {
  serviceType: string;
  count: number;
  costUsd: number;
}

export interface UsageSummary {
  pspId: number;
  period: string;
  totalRequests: number;
  billableRequests: number;
  totalCostUsd: number;
  breakdown: UsageBreakdownItem[];
}

export interface PricingTier {
  tierCode: string;
  tierName: string;
  monthlyFeeUsd: number;
  includedChecks: number;
  maxChecksPerMonth: number;
  perCheckPriceUsd: number;
  features?: string[];
}
