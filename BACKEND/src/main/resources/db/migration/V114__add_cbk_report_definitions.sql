-- HOK-58: CBK (Kenya Central Bank) Regulatory Report Definitions
-- Seeds 6 CBK report types for daily/weekly/monthly/quarterly/semi-annual/annual
-- regulatory submissions to the Central Bank of Kenya.
-- 
-- NOTE: report_category must be one of the allowed CHECK constraint values.
-- 'CBK_REGULATORY' is not valid; use 'REGULATORY_SUBMISSION' instead.

INSERT INTO reports (report_code, report_name, report_category, description, report_type, requires_approval, enabled, created_at)
VALUES
    ('CBK_DAILY',       'CBK Daily AML Summary',             'REGULATORY_SUBMISSION', 'Daily summary of AML activity, high-value transactions (>KES 1M), alerts, and SAR filings for CBK',    'REGULATORY', TRUE, TRUE, NOW()),
    ('CBK_WEEKLY',      'CBK Weekly Transaction Report',     'REGULATORY_SUBMISSION', 'Weekly transaction volume, high-value flows, suspicious activity, and merchant summaries for CBK',      'REGULATORY', TRUE, TRUE, NOW()),
    ('CBK_MONTHLY',     'CBK Monthly AML Report',            'REGULATORY_SUBMISSION', 'Monthly AML compliance report covering transactions, alerts, SARs, and risk metrics for CBK',           'REGULATORY', TRUE, TRUE, NOW()),
    ('CBK_QUARTERLY',   'CBK Quarterly Compliance Report',   'REGULATORY_SUBMISSION', 'Quarterly compliance and AML performance report with trend analysis for CBK',                            'REGULATORY', TRUE, TRUE, NOW()),
    ('CBK_SEMI_ANNUAL', 'CBK Semi-Annual Risk Review',       'REGULATORY_SUBMISSION', 'Semi-annual AML risk review with merchant risk profiles and regulatory metrics for CBK',                 'REGULATORY', TRUE, TRUE, NOW()),
    ('CBK_ANNUAL',      'CBK Annual Regulatory Submission',  'REGULATORY_SUBMISSION', 'Annual comprehensive AML regulatory submission covering all compliance obligations to CBK',              'REGULATORY', TRUE, TRUE, NOW())
ON CONFLICT DO NOTHING;
