// CBK GDI API types — field names are camelCase versions of the snake_case CBK wire format.
// Wire-to-CBK field names with spaces (e.g. "BANK ID") are the CBK-side contract only;
// the BE-to-FE contract always uses camelCase.

// ─────────────────────────────────────────────
// Endpoint type union (matches CbkEndpointType enum on backend)
// ─────────────────────────────────────────────
export type CbkEndpointType =
  | "SENIOR_MANAGEMENT"
  | "DIRECTORS"
  | "TRUSTEES"
  | "SHAREHOLDERS"
  | "CUSTOMER_COMPLAINTS"
  | "CYBER_INCIDENT"
  | "FRAUD_INCIDENTS"
  | "SYSTEM_STABILITY"
  | "SYSTEM_ACTIVITY"
  | "PRODUCTS_INFO"
  | "TRUST_ACCOUNT"
  | "CARD_BRANDS"
  | "BILLING_TEMPLATE"
  | "TRANSACTION_DETAILS"
  | "TRANSACTION_TARIFFS"
  | "MERCHANT_TRANSACTIONS"
  | "FAILED_TRANSACTIONS";

export const CBK_ENDPOINT_LABELS: Record<CbkEndpointType, string> = {
  SENIOR_MANAGEMENT: "Senior Management Schedule",
  DIRECTORS: "Schedule of Directors",
  TRUSTEES: "Schedule of Trustees",
  SHAREHOLDERS: "Schedule of Shareholders",
  CUSTOMER_COMPLAINTS: "Customer Complaints",
  CYBER_INCIDENT: "Cybersecurity Incidents",
  FRAUD_INCIDENTS: "Fraud / Theft / Robbery Incidents",
  SYSTEM_STABILITY: "System Stability & Service Interruptions",
  SYSTEM_ACTIVITY: "System Activity (TPS/TPH)",
  PRODUCTS_INFO: "Products Info",
  TRUST_ACCOUNT: "Trust Accounts",
  CARD_BRANDS: "Card Brands",
  BILLING_TEMPLATE: "Billing Template",
  TRANSACTION_DETAILS: "Transaction Details",
  TRANSACTION_TARIFFS: "Transaction Tariffs",
  MERCHANT_TRANSACTIONS: "Merchant Transactions",
  FAILED_TRANSACTIONS: "Failed / Rejected Transactions",
};

// ─────────────────────────────────────────────
// CBK Submission history record
// ─────────────────────────────────────────────
export interface CbkSubmission {
  id: number;
  pspId: number;
  endpointType: CbkEndpointType;
  status: "SUCCESS" | "FAILED" | "PENDING" | "RETRYING";
  requestId?: string;
  attemptedAt: string;
  resolvedAt?: string;
  errorMessage?: string;
  recordCount?: number;
}

// ─────────────────────────────────────────────
// 1. Senior Management (#1)
// ─────────────────────────────────────────────
export interface PspSeniorManagement {
  id?: number;
  pspId?: number;
  officerNames: string;
  gender: string;
  designation: string;
  dob?: string;
  nationality?: string;
  idNo?: string;
  taxId?: string;
  qualification?: string;
  dateOfEmp?: string;
  empType?: string;
  retirementDt?: string;
  externalAffliates?: string;
  otherDisclosure?: string;
}

// ─────────────────────────────────────────────
// 2. Directors (#2)
// ─────────────────────────────────────────────
export interface PspDirector {
  id?: number;
  pspId?: number;
  directorNames: string;
  directorGender?: string;
  typeOfDirector?: string;
  dob?: string;
  nationality?: string;
  residentCountry?: string;
  idNoPassport?: string;
  pin?: string;
  contactNumber?: string;
  qualifications?: string;
  otherDirectorships?: string;
  dateOfAppointment?: string;
  dateOfRetirement?: string;
  retirementReason?: string;
  disclosures?: string;
}

// ─────────────────────────────────────────────
// 3. Trustees (#3)
// ─────────────────────────────────────────────
export interface PspTrustee {
  id?: number;
  pspId?: number;
  trustCompName?: string;
  directorsTrustComp?: string;
  trusteeNames: string;
  trusteeGender?: string;
  dob?: string;
  nationality?: string;
  residentCountry?: string;
  idNoPassport?: string;
  pin?: string;
  contactNumber?: string;
  qualifications?: string;
  othersTrusteeships?: string;
  disclosures?: string;
  shareholders?: string;
  shareholdingPercentage?: number;
}

// ─────────────────────────────────────────────
// 4. Shareholders (#4)
// ─────────────────────────────────────────────
export interface PspShareholder {
  id?: number;
  pspId?: number;
  shareholderName: string;
  shareholderGender?: string;
  shareholderType?: string;
  dobOrRegDate?: string;
  nationality?: string;
  residentCountry?: string;
  countryOfInc?: string;
  idNoPassport?: string;
  pin?: string;
  contactNumber?: string;
  qualifications?: string;
  previousEmployment?: string;
  onboardingDate?: string;
  noOfSharesHeld?: number;
  shareValue?: number;
  percentageOfShare?: number;
}

// ─────────────────────────────────────────────
// 5. Customer Complaints (#5)
// ─────────────────────────────────────────────
export interface PspComplaint {
  id?: number;
  pspId?: number;
  complaintId?: string;
  complaintCode?: string;
  complainantGender?: string;
  complaintFrequency?: number;
  complainantName: string;
  complainantAge?: number;
  complainantContactNumber?: string;
  complainantSubCountyLocation?: string;
  complainantEducationLevel?: string;
  othersComplainantDetails?: string;
  agentId?: string;
  dateOfOccurrence?: string;
  dateReportedToTheInstitution?: string;
  dateResolved?: string;
  remedialStatus?: string;
  amountLost?: number;
  amountRecovered?: number;
}

// ─────────────────────────────────────────────
// 6. Cybersecurity Incidents (#6)
// ─────────────────────────────────────────────
export interface PspCyberIncident {
  id?: number;
  pspId?: number;
  incidentNumber?: string;
  reportingDate?: string;
  locationOfAttacker?: string;
  incidentMode?: string;
  dateAndTimeOfIncidentHappened?: string;
  lossType?: string;
  detailsOfTheIncident?: string;
  actionTakenToManageTheIncident?: string;
  dateAndTimeOfTheIncidentResolution?: string;
  actionTakenToMitigateFutureIncidents?: string;
  amountInvolved?: number;
  amountLost?: number;
}

// ─────────────────────────────────────────────
// 7. Fraud / Theft / Robbery Incidents (#7)
// ─────────────────────────────────────────────
export interface PspFraudIncident {
  id?: number;
  pspId?: number;
  reportingDate?: string;
  subCountyCode?: string;
  subFraudCode?: string;
  fraudCategoryFlag?: string;
  victimCategory?: string;
  victimInformation?: string;
  dateOfOccurrence?: string;
  numberOfIncidences?: number;
  amountInvolved?: number;
  amountLost?: number;
  amountRecovered?: number;
  actionTaken?: string;
  recoveryDetails?: string;
}

// ─────────────────────────────────────────────
// 8. System Stability / Service Interruptions (#8)
// ─────────────────────────────────────────────
export interface PspSystemInterruption {
  id?: number;
  pspId?: number;
  reportingDate?: string;
  subCountyCode?: string;
  systemOwnerFlag?: string;
  thirdPartyOwnedCategory?: string;
  thirdPartyName?: string;
  productType?: string;
  systemUnavailabilityTypeCod?: string;
  thirdPartySystemAffected?: string;
  serviceInterruptionCauseCod?: string;
  severityInterruptionCode?: string;
  recoveryTimeCode?: string;
  remedialStatusCode?: string;
  systemUptimePercentage?: number;
}

// ─────────────────────────────────────────────
// 10. Products (#10)
// ─────────────────────────────────────────────
export interface PspProduct {
  id?: number;
  pspId?: number;
  reportingDate?: string;
  productOwnershipFlag?: string;
  productOwnershipCategory?: string;
  productPartnerName?: string;
  productTransactionCode?: string;
  gender?: string;
  statusCode?: string;
  bandCode?: string;
  noOfCustomers?: number;
  noOfTransactions?: number;
  valueOfTransactions?: number;
  productName: string;
}

// ─────────────────────────────────────────────
// 11. Trust Accounts (#11)
// ─────────────────────────────────────────────
export interface PspTrustAccount {
  id?: number;
  pspId?: number;
  reportingDate?: string;
  bankId?: string;
  bankAccountNumber?: string;
  trustAccDrTypeCode?: string;
  orgReceivingDonation?: string;
  sectorCode?: string;
  trustAccIntUtilizedDetails?: string;
  trustAccOpeningBalance?: number;
  principalAmount?: number;
  trustAccInterestEarned?: number;
  closingBalance?: number;
  trustAccInterestUtilized?: number;
  trustFields?: string;
}

// ─────────────────────────────────────────────
// 15. Tariff Templates (#15)
// ─────────────────────────────────────────────
export interface PspTariff {
  id?: number;
  pspId?: number;
  reportingDate?: string;
  channelUsed?: string;
  channelPartnerName?: string;
  chargeDescription?: string;
  percentageTransactionCost?: number;
  absoluteTransactionCost?: number;
}
