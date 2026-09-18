const COLUMN_RECORD_TYPES: Record<string, string> = {
  alertid: "ALERT", caseid: "CASE", compliancecaseid: "CASE",
  txnid: "TRANSACTION", transactionid: "TRANSACTION", merchantid: "MERCHANT",
  multiassetcustomerid: "MULTI_ASSET_CUSTOMER", multiassettransactionid: "MULTI_ASSET_TRANSACTION",
  multiassetrisksignalid: "MULTI_ASSET_RISK_SIGNAL",
  mobilemoneytransactioncontextid: "MOBILE_MONEY_TRANSACTION_CONTEXT",
  mobilemoneyriskprofileid: "MOBILE_MONEY_RISK_PROFILE", mobilemoneynetworkedgeid: "MOBILE_MONEY_NETWORK_EDGE",
  vaspdirectoryentryid: "VASP_DIRECTORY_ENTRY", originatorvaspdirectoryentryid: "VASP_DIRECTORY_ENTRY",
  beneficiaryvaspdirectoryentryid: "VASP_DIRECTORY_ENTRY", cryptowalletprofileid: "CRYPTO_WALLET_PROFILE",
  walletscreeningrecordid: "WALLET_SCREENING_RECORD", vaspscreeningrecordid: "VASP_SCREENING_RECORD",
  travelrulejurisdictionpolicyid: "TRAVEL_RULE_JURISDICTION_POLICY", travelruletransferid: "TRAVEL_RULE_TRANSFER",
  virtualassetregulatoraccessgrantid: "VIRTUAL_ASSET_REGULATOR_ACCESS_GRANT",
  virtualassetregulatoraccesslogid: "VIRTUAL_ASSET_REGULATOR_ACCESS_LOG",
  marketsurveillancesignalid: "MARKET_SURVEILLANCE_SIGNAL", marketorderid: "MARKET_ORDER",
  marketexecutionid: "MARKET_EXECUTION", ruleversionid: "RULE_VERSION",
  corporateintelligencecheckid: "CORPORATE_INTELLIGENCE_CHECK",
  fixmessageeventid: "FIX_MESSAGE_EVENT",
  executionid: "REPORT_EXECUTION", reportexecutionid: "REPORT_EXECUTION",
  scheduleid: "REPORT_SCHEDULE", reportscheduleid: "REPORT_SCHEDULE",
  chargebackdisputeid: "CHARGEBACK_DISPUTE", disputeid: "CHARGEBACK_DISPUTE",
  merchantdocumentid: "MERCHANT_DOCUMENT", documentid: "MERCHANT_DOCUMENT",
  regulatorysubmissionid: "REGULATORY_SUBMISSION", submissionid: "REGULATORY_SUBMISSION",
  sarid: "SUSPICIOUS_ACTIVITY_REPORT", suspiciousactivityreportid: "SUSPICIOUS_ACTIVITY_REPORT",
  ruledefinitionid: "RULE_DEFINITION", velocityruleid: "VELOCITY_RULE", riskthresholdid: "RISK_THRESHOLD",
  userid: "USER", preparedbyid: "USER", reviewedbyid: "USER", approvedbyid: "USER", filedbyid: "USER",
  pspid: "PSP", reportid: "REPORT", invoiceid: "INVOICE", subscriptionid: "SUBSCRIPTION",
};

export function normalizeRecordType(value: string): string {
  return value.replace(/([a-z0-9])([A-Z])/g, "$1_$2").replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "").toUpperCase();
}

export function recordTypeForColumn(column: string): string | undefined {
  return COLUMN_RECORD_TYPES[column.replace(/_/g, "").toLowerCase()];
}

export function recordPath(recordType: string, recordId: unknown): string | undefined {
  if (recordId === null || recordId === undefined || String(recordId).trim() === "") return undefined;
  return `/records/${normalizeRecordType(recordType)}/${encodeURIComponent(String(recordId))}`;
}
