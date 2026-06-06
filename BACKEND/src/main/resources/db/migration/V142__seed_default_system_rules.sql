-- V135: Seed 53 default system rules (AML / Fraud / Screening)
--
-- Source: compliance team rule catalog (R-1 through R-170, 53 entries).
-- All seeded with:
--   is_system_managed = TRUE   → cannot be deleted, only disabled or edited
--   enabled           = FALSE  → operators must explicitly enable per PSP
--   psp_id            = NULL   → global; PSP users see them via inheritance
--   created_by        = 1      → system user
--   rule_type         = SPEL   → expression bodies left blank as templates;
--                                 each rule's parameters JSON drives the
--                                 dropdown editor instead of free-form code
--
-- Action mapping from the source spec → engine actions:
--   BLOCK   → BLOCK
--   SUSPEND → HOLD     (suspend the customer/account → hold for review)
--   FLAG    → ALERT    (flag for analyst review → alert)
--
-- Idempotent via ON CONFLICT (external_code) — re-running the migration is a
-- no-op once seeded.

INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
) VALUES
-- ─────────────────────── R-1 ───────────────────────
('First Transaction of a User',
 'Flags the first transaction made by a newly onboarded user — common money laundering pattern (account opened solely to move funds).',
 'SPEL', '', 30, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'New activity', 'Transaction', 'Unusual behaviour',
 '1st transaction', 'R-1', FALSE,
 'A new user opens a bank account and soon after initiates a large international wire transfer.',
 '{}'::jsonb),

-- ─────────────────────── R-2 ───────────────────────
('High Value Transaction Threshold',
 'Suspends transactions where the amount equals or exceeds the configured threshold (in KES or local equivalent).',
 'SPEL', '', 60, 'HOLD', 20, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Pattern recognition', 'Transaction', 'Unusual behaviour',
 'Transaction amount', 'R-2', TRUE,
 'A user usually transacting under 500 SGD initiates a sudden 10,000 SGD transaction, prompting a review for unusual high-value activity.',
 '{"threshold_amount": 1000000, "currency": "KES"}'::jsonb),

-- ─────────────────────── R-3 ───────────────────────
('New Country Transaction (after N txns)',
 'Flags the first transaction to or from a country the user has not previously transacted with, after they have completed N priors.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'New activity', 'Transaction', 'Unusual behaviour',
 'Transaction country, No. of transactions', 'R-3', TRUE,
 'A user typically transacts domestically, then suddenly starts sending high-value transactions abroad.',
 '{"min_prior_transactions": 5}'::jsonb),

-- ─────────────────────── R-4 ───────────────────────
('New Currency Transaction (after N txns)',
 'Flags the first transaction in a currency the user has not previously used, after they have completed N priors.',
 'SPEL', '', 30, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'New activity', 'Transaction', 'Unusual behaviour',
 'Transaction currency, No. of transactions', 'R-4', TRUE,
 'An account mainly using USD suddenly transacts in EUR. This significant shift triggers an alert.',
 '{"min_prior_transactions": 5}'::jsonb),

-- ─────────────────────── R-5 ───────────────────────
('Reactivation After Inactivity',
 'Flags a transaction made by a user that has been inactive for the configured period.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Anomaly detection', 'Transaction', 'Money mules, Unusual behaviour',
 'User account status, Time', 'R-5', TRUE,
 'An account shows no activity for six months, then suddenly executes a high-value transaction.',
 '{"inactivity_days": 180}'::jsonb),

-- ─────────────────────── R-6 ───────────────────────
('High-Risk Currency Transaction',
 'Flags transactions involving currencies on the configured high-risk list (often used in cross-currency laundering).',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Risk exposure', 'Transaction', 'Unusual behaviour',
 'Transaction currency', 'R-6', FALSE,
 'A user frequently transacts with a high-risk currency linked to money laundering, necessitating closer scrutiny.',
 '{"high_risk_currencies": []}'::jsonb),

-- ─────────────────────── R-7 ───────────────────────
('Structuring — Incoming Sub-Threshold Burst',
 'Flags >= X consecutive low-value INCOMING transactions just below threshold Y to a user (classic structuring pattern).',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity/Transaction density', 'Transaction', 'Structuring',
 'Transaction amount, No. of transactions', 'R-7', TRUE,
 'Over a week, an individual receives multiple transactions from different recipients, all marginally under the reporting limit.',
 '{"min_transactions": 5, "threshold_amount": 9000, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-8 ───────────────────────
('Structuring — Outgoing Sub-Threshold Burst',
 'Flags >= X consecutive low-value OUTGOING transactions just below threshold Y from a user.',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity/Transaction density', 'Transaction', 'Structuring, Money mules',
 'No. of transactions, Transaction amount', 'R-8', TRUE,
 'Over a week, an individual sends multiple transactions to different recipients, all marginally under the reporting limit.',
 '{"min_transactions": 5, "threshold_amount": 9000, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-9 ───────────────────────
('Many-Senders-One-Receiver',
 'Flags when more than X distinct users send funds to a single counterparty within time T (Nigerian-prince outbound pattern).',
 'SPEL', '', 55, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity', 'Transaction', 'Hidden/unusual relationships, Money mules, Scams (romance, Nigerian Prince, inheritance), Terrorist financing',
 'No. of users, Time', 'R-9', FALSE,
 'Within a week, an unusually large number of different users send money to a single recipient.',
 '{"min_unique_senders": 10, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-10 ───────────────────────
('Many-Counterparties-One-User (Inbound)',
 'Flags when more than X distinct counterparties send funds to a single user within time T.',
 'SPEL', '', 55, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity', 'Transaction', 'Hidden/unusual relationships, Money mules, Scams (romance, Nigerian Prince, inheritance), Terrorist financing',
 'No. of users, Time', 'R-10', TRUE,
 'Within a week, an unusually large number of different counterparties send money to a single user.',
 '{"min_unique_counterparties": 10, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-11 ───────────────────────
('Anomalous Transaction Volume',
 'Flags when a customers transaction volume in time T deviates anomalously from their historical baseline.',
 'SPEL', '', 45, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Unusual behaviour',
 'Transaction amount, Time', 'R-11', FALSE,
 'A customer who typically processes $50,000 weekly suddenly processes $500,000 in one week.',
 '{"baseline_window_days": 30, "deviation_multiplier": 5}'::jsonb),

-- ─────────────────────── R-12 ───────────────────────
('Anomalous Transaction Pattern',
 'Flags when transaction count in time T deviates from the customers historical pattern.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Unusual behaviour',
 'No. of transactions, Time', 'R-12', FALSE,
 'An account that normally makes 5-10 transactions monthly suddenly executes 200 transactions in 30 days.',
 '{"baseline_window_days": 30, "deviation_multiplier": 10}'::jsonb),

-- ─────────────────────── R-13 ───────────────────────
('Receiver Wallet Blacklist',
 'Flags transactions where the receivers wallet name matches the internal blacklist.',
 'SPEL', '', 70, 'ALERT', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Blacklist', 'Transaction', 'Internal blacklists',
 'Transaction payment method count', 'R-13', FALSE,
 'During a transaction, a receivers wallet name matches an entry in the blacklist database.',
 '{"blacklist_name": "wallet_names"}'::jsonb),

-- ─────────────────────── R-14 ───────────────────────
('High-Risk Country Transaction',
 'Flags transactions to or from a country on the configured high-risk list.',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Risk exposure', 'Transaction', 'High risk transactions, Unusual behaviour',
 'Transaction country', 'R-14', FALSE,
 'Multiple transactions are initiated to and from a country with high levels of corruption and money laundering.',
 '{"high_risk_country_list": "FATF_blacklist"}'::jsonb),

-- ─────────────────────── R-15 ───────────────────────
('Multi-Bank Velocity',
 'Flags when a user sends/receives payments via more than X distinct banks within time T.',
 'SPEL', '', 45, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Diversity', 'Transaction', 'Money mules, Acquiring fraud, Layering',
 'Transaction payment method count, Time', 'R-15', FALSE,
 'A user initiates transactions from four banks within a week.',
 '{"min_distinct_banks": 4, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-17 ───────────────────────
('Consumer User Screening',
 'Suspends consumer users when external screening (sanctions / PEP / adverse media) returns a hit.',
 'SPEL', '', 80, 'HOLD', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'SCREENING', 'Screening', 'User', 'Screening hits',
 'Username, User details, Users Y.O.B', 'R-17', FALSE,
 'A consumer user is checked for sanctions/PEP/AM match against external data providers.',
 '{"providers": ["sanctions", "pep", "adverse_media"]}'::jsonb),

-- ─────────────────────── R-20 ───────────────────────
('User Onboarded From High-Risk Country',
 'Flags users whose country of residence is on the high-risk list at onboarding.',
 'SPEL', '', 45, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'New activity/Risk exposure', 'User', 'High risk transactions, Terrorist financing',
 'User details, Transaction country', 'R-20', FALSE,
 'A users country of residence is identified as a high-risk country, prompting a review of their account activity.',
 '{"high_risk_country_list": "FATF_blacklist"}'::jsonb),

-- ─────────────────────── R-22 ───────────────────────
('Card-Issued Country Blacklist',
 'Flags transactions where the cards issuing country is on the blacklist.',
 'SPEL', '', 50, 'ALERT', 30, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Blacklist', 'Transaction', 'Internal blacklists',
 'Transaction payment method issued country', 'R-22', FALSE,
 'During a transaction, an individuals card-issued country matches the blacklist database.',
 '{"blacklist_name": "card_country_blacklist"}'::jsonb),

-- ─────────────────────── R-24 ───────────────────────
('Transaction Reference Keyword Blacklist',
 'Flags transactions whose reference field contains a keyword on the blacklist.',
 'SPEL', '', 40, 'ALERT', 30, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Blacklist', 'Transaction', 'Internal blacklists',
 'Transaction details, Keywords', 'R-24', FALSE,
 'A payment with a blacklisted keyword in its reference is automatically flagged for review.',
 '{"keyword_blacklist": []}'::jsonb),

-- ─────────────────────── R-25 ───────────────────────
('Average Amount Spike',
 'Flags when the average transaction amount in a recent window deviates significantly from the customers baseline.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Unusual behaviour',
 'Transaction amount, Time', 'R-25', FALSE,
 'A retail business that usually processes $25 average transactions starts processing $2,500 average amounts.',
 '{"baseline_window_days": 30, "spike_multiplier": 100}'::jsonb),

-- ─────────────────────── R-26 ───────────────────────
('Round-Value Transaction Spike',
 'Flags when a users share of round-value (.00) transactions in a recent window is significantly above their baseline.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Structuring, Unusual behaviour',
 'Transaction amount, Time', 'R-26', FALSE,
 'A customer whose transactions are typically varied amounts suddenly makes 80% in round numbers.',
 '{"min_round_share_pct": 60, "min_transactions": 10, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-27 ───────────────────────
('Volume Ratio Across Windows',
 'Flags when total volume in window t1 is >= X times higher than total volume in window t2.',
 'SPEL', '', 45, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Volume comparison', 'Transaction', 'Money mules, Acquiring fraud, Layering',
 'Transaction amount, Time', 'R-27', FALSE,
 'In the last 15 days, a users transactions surged to $50,000 from the previous 30-day total of $5,000.',
 '{"window_t1_days": 15, "window_t2_days": 30, "ratio_x": 5}'::jsonb),

-- ─────────────────────── R-30 ───────────────────────
('Transaction Velocity (count in time)',
 'Flags when a user makes >= X transactions within time T.',
 'SPEL', '', 40, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Velocity', 'Transaction', 'Money mules, Acquiring fraud, Layering',
 'No. of transactions, Time', 'R-30', TRUE,
 'A person sends several transactions exceeding a set limit within a brief period.',
 '{"max_transactions": 10, "time_window_minutes": 1440}'::jsonb),

-- ─────────────────────── R-32 ───────────────────────
('Bank Name Screening',
 'Suspends users when their bank name matches Sanctions / PEP / Adverse Media screening.',
 'SPEL', '', 70, 'HOLD', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'SCREENING', 'Screening', 'User', 'Screening hits',
 'Users bank name', 'R-32', FALSE,
 'Resolve an IBAN to check if the bank name matches against Sanctions/PEP/AM lists.',
 '{"providers": ["sanctions", "pep", "adverse_media"]}'::jsonb),

-- ─────────────────────── R-33 ───────────────────────
('User Inactivity Watch',
 'Flags users that have been inactive for more than X days (ongoing screening).',
 'SPEL', '', 25, 'ALERT', 200, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection/Screening', 'User', 'Unusual behaviour',
 'Time', 'R-33', FALSE,
 'A user has been inactive for more than 365 days, prompting a review of the account.',
 '{"inactivity_days": 365}'::jsonb),

-- ─────────────────────── R-41 ───────────────────────
('Send-vs-Receive Volume Mismatch',
 'Flags users whose send volume is significantly higher than their receive volume in the same window.',
 'SPEL', '', 45, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Volume comparison', 'Transaction', 'Money mules, Layering',
 'Transaction amount, Transaction type, Time', 'R-41', FALSE,
 'A user sent $30,000 in a month but received only $3,000.',
 '{"window_days": 30, "send_to_receive_ratio_min": 5}'::jsonb),

-- ─────────────────────── R-45 ───────────────────────
('Bank Account Holder Name Mismatch',
 'Flags when the current payments bank-account-holder name differs from the users prior bank-account-holder names.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Unusual behaviour',
 'Account holder name, Time', 'R-45', FALSE,
 'A user used 3 different bank account names in a month, indicating possible account takeover.',
 '{"max_distinct_names": 1, "lookback_days": 30}'::jsonb),

-- ─────────────────────── R-52 ───────────────────────
('Same IP — Many Users',
 'Flags when an IP address is used by >= X distinct user IDs within time T.',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Money mules, Account takeover fraud',
 'Users IP address, No. of users', 'R-52', FALSE,
 'In a week, an IP address is used by 10 unique users for transactions.',
 '{"max_distinct_users_per_ip": 5, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-53 ───────────────────────
('Same Payment Identifier — Many Users',
 'Flags when a single payment identifier (card fingerprint, account #) is used by >= X distinct users within time T.',
 'SPEL', '', 55, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Acquiring fraud',
 'Transaction payment identifier, No. of users', 'R-53', FALSE,
 'Within a month, 20 user accounts use the same payment identifier.',
 '{"max_distinct_users_per_identifier": 5, "lookback_days": 30}'::jsonb),

-- ─────────────────────── R-55 ───────────────────────
('Same Sender — Many Payment Identifiers',
 'Flags when a single sender uses >= X distinct payment identifiers within time T (card-testing pattern).',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Diversity', 'Transaction', 'Card fraud, Acquiring fraud',
 'User payment identifier, No. of users', 'R-55', TRUE,
 'A users use of several unique cards within a week hints at possible card testing or fraud.',
 '{"max_distinct_identifiers": 5, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-61 ───────────────────────
('User Address Change',
 'Flags users whose registered address has changed (legal entity address for businesses).',
 'SPEL', '', 30, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Merchant monitoring', 'User', 'Unusual behaviour',
 'User details, Users address', 'R-61', FALSE,
 'The system temporarily halts frequent online shopping transactions if it detects multiple recent home address changes.',
 '{"max_changes": 1, "lookback_days": 90}'::jsonb),

-- ─────────────────────── R-69 ───────────────────────
('Spend/Receive Above Expected',
 'Flags users whose spend or receive volume is materially above the expected baseline.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Volume', 'Transaction', 'Unusual behaviour',
 'Transaction amount, Time', 'R-69', TRUE,
 'An account typically making $100 monthly transactions suddenly sends $3,000 in three days.',
 '{"baseline_window_days": 90, "spike_multiplier": 10}'::jsonb),

-- ─────────────────────── R-77 ───────────────────────
('Velocity From High-Risk Country',
 'Flags users that send/receive >= X transactions from/to a high-risk country within time T.',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity', 'Transaction', 'High risk transactions, Terrorist financing',
 'Transaction country, Time, Counterparty country count', 'R-77', FALSE,
 'A users account receives 10 transactions from a high-risk country within a single day.',
 '{"min_transactions": 5, "lookback_hours": 24, "high_risk_country_list": "FATF_blacklist"}'::jsonb),

-- ─────────────────────── R-87 ───────────────────────
('Transaction From High-Risk IP Country',
 'Flags transactions where the IP-resolved country is on the high-risk list.',
 'SPEL', '', 40, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Risk exposure', 'Transaction', 'Unusual behaviour',
 'Transaction country, Users IP address', 'R-87', FALSE,
 'A user initiates a transaction from a high-risk country, triggering an alert.',
 '{"high_risk_country_list": "FATF_blacklist"}'::jsonb),

-- ─────────────────────── R-88 ───────────────────────
('IP Geolocation Outside Expected',
 'Flags when a transactions IP address is outside the users expected location set.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Anomaly detection', 'Transaction', 'Account takeover fraud',
 'Users IP address', 'R-88', FALSE,
 'A regular account in Japan initiates an unusual transaction from Sweden.',
 '{"expected_country_lookback_days": 90}'::jsonb),

-- ─────────────────────── R-94 ───────────────────────
('Payment Identifier Reuse Velocity',
 'Flags when a particular payment identifier is used too many times at txn origin/destination within time T.',
 'SPEL', '', 45, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Diversity', 'Transaction', 'Hidden/unusual relationships',
 'Transaction payment identifier, Time', 'R-94', FALSE,
 'A users account number is used in 10 transactions within a single day.',
 '{"max_uses": 5, "lookback_hours": 24}'::jsonb),

-- ─────────────────────── R-113 ───────────────────────
('IP Address Change Velocity',
 'Flags users whose IP address changes more than X times within time T.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Anomaly detection', 'Transaction', 'Account takeover fraud',
 'Time, Users IP address', 'R-113', FALSE,
 'Over a week, a users IP switches between countries 10 times.',
 '{"max_ip_changes": 5, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-117 ───────────────────────
('Recurring Suspicious Amount Pattern',
 'Flags transactions whose amount ends in a recurring pattern (e.g. 999 or 000).',
 'SPEL', '', 30, 'ALERT', 200, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Pattern recognition', 'Transaction', 'Structuring',
 'Transaction amount', 'R-117', FALSE,
 'Recurring user transactions end in .999 (e.g., 150.999, 250.999), hinting at a pattern warranting investigation.',
 '{"suspect_endings": ["999", "000"]}'::jsonb),

-- ─────────────────────── R-118 ───────────────────────
('Account Holder Name Levenshtein Mismatch',
 'Flags when the account-holder name does not match the username within a configurable Levenshtein distance.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Anomaly detection', 'Transaction', 'Account takeover fraud',
 'Username', 'R-118', FALSE,
 'A transaction by JohnDoe123 with an account holder name JonDo123 triggers due to significant Levenshtein distance.',
 '{"max_levenshtein_distance": 2}'::jsonb),

-- ─────────────────────── R-119 ───────────────────────
('Circular Trading Velocity',
 'Flags when the same two parties transact among themselves >= X times within time T.',
 'SPEL', '', 50, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity', 'Transaction', 'Hidden/unusual relationships',
 'No. of transactions, Time, Both parties username, Username', 'R-119', FALSE,
 'Businesses transacting excessively among themselves within a period, initiating a circular trading investigation.',
 '{"min_transactions": 5, "lookback_days": 30}'::jsonb),

-- ─────────────────────── R-120 ───────────────────────
('Average Amount Drop/Rise Across Windows',
 'Flags when the average transaction amount in window 1 differs from window 2 by a configurable factor.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Volume comparison', 'Transaction', 'Unusual behaviour',
 'Transaction amount, Time', 'R-120', FALSE,
 'In Q1, the average sender user transaction was $1,000, dropping to $100 in Q2.',
 '{"window_t1_days": 90, "window_t2_days": 90, "ratio_x": 5}'::jsonb),

-- ─────────────────────── R-121 ───────────────────────
('Daily Transaction Count Ratio Across Windows',
 'Flags when the average daily transaction count in window t1 is >= X times higher than in window t2.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Velocity comparison', 'Transaction', 'Unusual behaviour',
 'No. of transactions, Time', 'R-121', TRUE,
 'A users avg daily transactions increase significantly in the first month and drop the next.',
 '{"window_t1_days": 30, "window_t2_days": 30, "ratio_x": 3}'::jsonb),

-- ─────────────────────── R-122 ───────────────────────
('Daily Average Amount Ratio Across Windows',
 'Flags when the average daily transaction amount in window t1 is >= X times higher than in window t2.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Volume comparison', 'Transaction', 'Unusual behaviour',
 'No. of transactions, Time', 'R-122', TRUE,
 'In early May, senders daily transaction was $200 on average, dropping to $20 daily in late May.',
 '{"window_t1_days": 7, "window_t2_days": 7, "ratio_x": 5}'::jsonb),

-- ─────────────────────── R-123 ───────────────────────
('Multi-Country Diversity',
 'Flags users that transact with > X distinct countries within time T.',
 'SPEL', '', 45, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Diversity', 'Transaction', 'Unusual behaviour',
 'Transaction country, Time, Counterparty country count', 'R-123', FALSE,
 'In a single week, a sender sent transactions to 8 distinct countries.',
 '{"max_distinct_countries": 5, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-124 ───────────────────────
('Round-Value Transaction Share',
 'Flags users whose share of round-value (.00) transactions in time T exceeds X% (after Y total transactions).',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Diversity', 'Transaction', 'Structuring, Unusual behaviour',
 'Transaction amount, Time', 'R-124', TRUE,
 'A users account receives 10 transactions of round values within a single day.',
 '{"min_round_share_pct": 50, "min_transactions": 10, "lookback_days": 1}'::jsonb),

-- ─────────────────────── R-125 ───────────────────────
('Transaction State Anomaly',
 'Flags when a configurable share of a users transactions are in a specific state (e.g. Refund) within time T.',
 'SPEL', '', 40, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Anomaly detection', 'Transaction', 'Acquiring fraud, Card fraud, Account takeover fraud, Issuing fraud',
 'Transaction state, Time', 'R-125', FALSE,
 'A users account has a high share of transactions in Refund state within a single day.',
 '{"state": "REFUND", "min_share_pct": 30, "min_transactions": 10, "lookback_days": 1}'::jsonb),

-- ─────────────────────── R-126 ───────────────────────
('Same-Parties Volume',
 'Flags when the same two parties transact among themselves for amount >= X within time T.',
 'SPEL', '', 45, 'ALERT', 50, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Volume', 'Transaction', 'Hidden/unusual relationships',
 'Transaction amount, Time, Both parties username, Username', 'R-126', FALSE,
 'Two users repeatedly transact between each other, exchanging sums above $10,000 within a single week.',
 '{"min_amount": 10000, "lookback_days": 7}'::jsonb),

-- ─────────────────────── R-128 ───────────────────────
('Business Entity Screening',
 'Suspends business users when the legal entity, shareholders, or directors hit Sanctions/PEP/AM screening.',
 'SPEL', '', 80, 'HOLD', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'SCREENING', 'Screening', 'User', 'Screening hits',
 'User details, Entity name', 'R-128', FALSE,
 'A business user is checked for sanctions/PEP/AM match of their shareholders & directors.',
 '{"providers": ["sanctions", "pep", "adverse_media"], "include_directors": true, "include_shareholders": true}'::jsonb),

-- ─────────────────────── R-129 ───────────────────────
('Payment Details Blacklist',
 'Flags transactions whose payment details match the internal blacklist.',
 'SPEL', '', 60, 'ALERT', 30, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Blacklist', 'Transaction', 'Internal blacklists',
 'Transaction payment details', 'R-129', FALSE,
 'During a transaction, an individuals payment details match an entry in the blacklist database.',
 '{"blacklist_name": "payment_details"}'::jsonb),

-- ─────────────────────── R-130 ───────────────────────
('Round-Value Transaction Velocity',
 'Flags users that send/receive >= X round-value (.00) transactions within time T.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Velocity', 'Transaction', 'Structuring, Unusual behaviour',
 'No. of transactions', 'R-130', FALSE,
 'A users account receives 10 transactions of $100.00 within a single day.',
 '{"min_round_transactions": 10, "lookback_hours": 24}'::jsonb),

-- ─────────────────────── R-131 ───────────────────────
('Daily Transaction Count Ratio',
 'Flags when total transaction count in window t1 is >= X times higher than in window t2.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Velocity comparison', 'Transaction', 'Unusual behaviour',
 'No. of transactions, Time', 'R-131', FALSE,
 'A user does 200 transactions in the first quarter, but only 20 in the next.',
 '{"window_t1_days": 90, "window_t2_days": 90, "ratio_x": 10}'::jsonb),

-- ─────────────────────── R-132 ───────────────────────
('Variable Value Blacklist (Block)',
 'Blocks transactions where a variable (card fingerprint, bank account #, etc.) matches the blacklist.',
 'SPEL', '', 90, 'BLOCK', 5, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Blacklist', 'Transaction', 'Internal blacklists',
 'User details', 'R-132', FALSE,
 'During a transaction, an individuals user ID matches an entry in the blacklist database.',
 '{"blacklist_variables": ["card_fingerprint", "bank_account_number"]}'::jsonb),

-- ─────────────────────── R-155 ───────────────────────
('Bank Name Change Velocity',
 'Flags users whose bank name has changed > X times within time T.',
 'SPEL', '', 35, 'ALERT', 100, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'AML', 'Anomaly detection', 'Transaction', 'Unusual behaviour',
 'Users bank name, Time', 'R-155', FALSE,
 'A user updated their bank details four times in a month.',
 '{"max_bank_changes": 2, "lookback_days": 30}'::jsonb),

-- ─────────────────────── R-169 ───────────────────────
('Counterparty Screening',
 'Suspends transactions where the counterparty (recipient name + bank) hits Sanctions/PEP/AM screening.',
 'SPEL', '', 80, 'HOLD', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'SCREENING', 'Screening', 'Transaction', 'Screening hits',
 'Counterparty username, Counterparty bank name', 'R-169', FALSE,
 'A transactions recipient name and bank are checked against Sanctions/PEP/AM lists.',
 '{"providers": ["sanctions", "pep", "adverse_media"]}'::jsonb),

-- ─────────────────────── R-170 ───────────────────────
('Anonymous Payment Screening',
 'Suspends transactions whose payment details hit Sanctions/PEP/AM screening when neither party user is identifiable.',
 'SPEL', '', 75, 'HOLD', 10, FALSE, NOW(), NOW(), NULL, 1,
 TRUE, 'SCREENING', 'Screening', 'Transaction', 'Screening hits',
 'Screening payment details', 'R-170', FALSE,
 'A transactions payment details are checked when no userId is mentioned, against Sanctions/PEP/AM lists.',
 '{"providers": ["sanctions", "pep", "adverse_media"]}'::jsonb)

ON CONFLICT (external_code) DO NOTHING;
