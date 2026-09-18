-- ============================================================================
-- V129: Seed CBK demo data for the three demo PSPs
--
-- Populates all CBK GDI reporting child tables for DEMO_VELOCITY,
-- DEMO_MWANANCHI, and DEMO_APEX so the platform is demo-ready end-to-end.
-- HOKEKA_PLATFORM is intentionally excluded — it is not a real PSP tenant.
--
-- Sections:
--   1.  CBK config on psps (institution_code, environment, reporting)
--   2.  Directors          (3 per PSP)
--   3.  Shareholders       (3 per PSP)
--   4.  Trustees           (2 per PSP)
--   5.  Senior management  (4 per PSP)
--   6.  Products           (3 per PSP)
--   7.  Trust accounts     (2 per PSP)
--   8.  Tariff templates   (3 per PSP)
--   9.  Cyber incidents     (2 per PSP)
--  10.  System interruptions(3 per PSP)
--  11.  Customer complaints (3 per PSP)
--  12.  Fraud incidents     (2 per PSP)
--
-- Idempotent: every statement uses NOT EXISTS / ON CONFLICT guards.
-- All psp_id references are resolved via sub-select on psp_code — no
-- hardcoded surrogate keys.
-- ============================================================================

BEGIN;

-- ============================================================================
-- 1. CBK CONFIG — update the three demo PSPs with institution codes
-- ============================================================================

UPDATE psps
   SET cbk_institution_code  = '0800001',
       cbk_reporting_enabled = TRUE,
       cbk_environment       = 'preprod',
       cbk_allow_live        = FALSE,
       updated_at            = NOW()
 WHERE psp_code = 'DEMO_VELOCITY';

UPDATE psps
   SET cbk_institution_code  = '0800015',
       cbk_reporting_enabled = TRUE,
       cbk_environment       = 'preprod',
       cbk_allow_live        = FALSE,
       updated_at            = NOW()
 WHERE psp_code = 'DEMO_MWANANCHI';

UPDATE psps
   SET cbk_institution_code  = '0800042',
       cbk_reporting_enabled = TRUE,
       cbk_environment       = 'preprod',
       cbk_allow_live        = FALSE,
       updated_at            = NOW()
 WHERE psp_code = 'DEMO_APEX';

-- ============================================================================
-- 2. DIRECTORS — 3 per PSP
--    Mix of genders, nationalities (KEN/USA/GBR), and director types.
--    date_of_retirement IS NULL → all currently serving.
-- ============================================================================

-- --- DEMO_VELOCITY directors ---

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Jonathan R. Whitfield', 'MALE', 'Executive',
       '1974-03-15', 'USA', 'USA', 'US-PP-583920147', 'N/A',
       '+12025550183', 'MBA (Harvard), CFA', 'TechPay Inc. (non-exec)',
       '2021-01-10', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Jonathan R. Whitfield'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Amelia C. Harrington', 'FEMALE', 'Non-Executive',
       '1981-07-22', 'GBR', 'USA', 'GB-PP-702934811', 'N/A',
       '+17705550294', 'LLB (Oxford), ACCA', NULL,
       '2022-04-01', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Amelia C. Harrington'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Marcus D. Okonkwo', 'MALE', 'Independent',
       '1968-11-05', 'USA', 'USA', 'US-PP-419073625', 'N/A',
       '+14045550371', 'PhD Economics (MIT), CISA',
       'Fintech Advisory Council (independent)',
       '2023-06-15', NULL, NULL,
       'Serves on audit committee; no operational role.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Marcus D. Okonkwo'
   );

-- --- DEMO_MWANANCHI directors ---

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'James Kipchoge Mwangi', 'MALE', 'Executive',
       '1971-08-20', 'KEN', 'KEN', 'A12345678', 'A001234567B',
       '+254712345678', 'BComm (UoN), CPA(K)', NULL,
       '2020-03-01', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'James Kipchoge Mwangi'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Grace Wanjiku Ndungu', 'FEMALE', 'Non-Executive',
       '1977-02-14', 'KEN', 'KEN', 'B23456789', 'B002345678C',
       '+254723456789', 'LLB (UoN), Post-Grad Banking Law',
       'Kenya Bankers Association (council member)',
       '2021-09-10', NULL, NULL,
       'Spouse employed by regulatory advisor firm; disclosed to board.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Grace Wanjiku Ndungu'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Bernard Otieno Ouma', 'MALE', 'Independent',
       '1965-06-30', 'KEN', 'KEN', 'C34567890', 'C003456789D',
       '+254734567890', 'MSc Finance (University of Leeds)', NULL,
       '2023-01-20', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Bernard Otieno Ouma'
   );

-- --- DEMO_APEX directors ---

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Priya Anand Krishnamurthy', 'FEMALE', 'Executive',
       '1979-04-18', 'GBR', 'GBR', 'GB-PP-845621390', 'N/A',
       '+442075550112', 'MSc Financial Engineering (Imperial College)', NULL,
       '2022-07-01', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Priya Anand Krishnamurthy'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Thomas E. Gallagher', 'MALE', 'Non-Executive',
       '1963-12-02', 'GBR', 'GBR', 'GB-PP-733419082', 'N/A',
       '+441614550229', 'MA Economics (Cambridge), FCA',
       'Northern Trust plc (senior advisor)',
       '2021-11-15', NULL, NULL,
       'Advisory relationship with Northern Trust disclosed to board.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Thomas E. Gallagher'
   );

INSERT INTO psp_directors (
    psp_id, director_names, director_gender, type_of_director,
    dob, nationality, resident_country, id_no_passport, pin,
    contact_number, qualifications, other_directorships,
    date_of_appointment, date_of_retirement, retirement_reason,
    disclosures, created_at, updated_at
)
SELECT p.psp_id,
       'Fatima Al-Rashidi', 'FEMALE', 'Independent',
       '1983-09-27', 'KEN', 'GBR', 'KEN-PP-299847610', 'N/A',
       '+447910550341', 'BBA (Strathmore), CAMS (ACAMS)',
       NULL,
       '2024-02-28', NULL, NULL,
       'No material conflicts declared.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_directors d
        WHERE d.psp_id = p.psp_id AND d.director_names = 'Fatima Al-Rashidi'
   );

-- ============================================================================
-- 3. SHAREHOLDERS — 3 per PSP
--    Mix of INDIVIDUAL and CORPORATE types. Percentages sum to 100% per PSP.
-- ============================================================================

-- --- DEMO_VELOCITY shareholders ---

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Velocity Capital Partners LP', NULL, 'CORPORATE',
       '2015-06-01', 'USA', 'USA', 'USA',
       'EIN-83-4912075', 'N/A', '+12025550900', NULL,
       NULL, '2019-08-15',
       5000000, 1.0000, 51.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Velocity Capital Partners LP'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Jonathan R. Whitfield', 'MALE', 'INDIVIDUAL',
       '1974-03-15', 'USA', 'USA', NULL,
       'US-PP-583920147', 'N/A', '+12025550183', NULL,
       'Chief Executive Officer, Velocity Payments LLC', '2019-08-15',
       3332000, 1.0000, 34.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Jonathan R. Whitfield'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Harrington Family Trust', NULL, 'CORPORATE',
       '2018-11-20', 'GBR', 'GBR', 'GBR',
       'UK-TR-7643210', 'N/A', '+441615550471', NULL,
       NULL, '2022-04-01',
       1470000, 1.0000, 15.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Harrington Family Trust'
   );

-- --- DEMO_MWANANCHI shareholders ---

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Mwananchi Holdings Ltd', NULL, 'CORPORATE',
       '2010-02-14', 'KEN', 'KEN', 'KEN',
       'CPR-2010-0091837', 'P051234567Q', '+254700112233', NULL,
       NULL, '2018-01-10',
       12000000, 10.0000, 60.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Mwananchi Holdings Ltd'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'James Kipchoge Mwangi', 'MALE', 'INDIVIDUAL',
       '1971-08-20', 'KEN', 'KEN', NULL,
       'A12345678', 'A001234567B', '+254712345678', NULL,
       'Chief Executive Officer, Mwananchi Bank Plc', '2018-01-10',
       5000000, 10.0000, 25.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'James Kipchoge Mwangi'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'East Africa Growth Fund II', NULL, 'CORPORATE',
       '2016-07-30', 'KEN', 'KEN', 'KEN',
       'CPR-2016-0178342', 'P052345678R', '+254711998877', NULL,
       NULL, '2021-03-15',
       3000000, 10.0000, 15.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'East Africa Growth Fund II'
   );

-- --- DEMO_APEX shareholders ---

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Apex Global Ventures Ltd', NULL, 'CORPORATE',
       '2013-09-10', 'GBR', 'GBR', 'GBR',
       'UK-CH-09183742', 'N/A', '+442075550600', NULL,
       NULL, '2020-05-01',
       4200000, 1.0000, 55.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Apex Global Ventures Ltd'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Priya Anand Krishnamurthy', 'FEMALE', 'INDIVIDUAL',
       '1979-04-18', 'GBR', 'GBR', NULL,
       'GB-PP-845621390', 'N/A', '+442075550112', NULL,
       'Chief Executive Officer, Apex Remit Global Ltd', '2020-05-01',
       2300000, 1.0000, 30.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Priya Anand Krishnamurthy'
   );

INSERT INTO psp_shareholders (
    psp_id, shareholder_name, shareholder_gender, shareholder_type,
    dob_or_reg_date, nationality, resident_country, country_of_inc,
    id_no_passport, pin, contact_number, qualifications,
    previous_employment, onboarding_date,
    no_of_shares_held, share_value, percentage_of_share,
    created_at, updated_at
)
SELECT p.psp_id,
       'Kairos FinTech Opportunity Fund', NULL, 'CORPORATE',
       '2019-01-15', 'GBR', 'GBR', 'GBR',
       'UK-CH-11987654', 'N/A', '+441615550810', NULL,
       NULL, '2022-08-20',
       1150000, 1.0000, 15.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_shareholders s
        WHERE s.psp_id = p.psp_id AND s.shareholder_name = 'Kairos FinTech Opportunity Fund'
   );

-- ============================================================================
-- 4. TRUSTEES — 2 per PSP
--    trust_comp_name references the PSP's own legal name as trustee entity.
-- ============================================================================

-- --- DEMO_VELOCITY trustees ---

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Velocity Payments LLC', 'Jonathan R. Whitfield; Amelia C. Harrington',
       'Sandra L. Meyer',
       'FEMALE', '1970-05-12', 'USA', 'USA',
       'US-PP-672810394', 'N/A', '+15105550784', 'JD (Stanford), CFE',
       NULL, 'No material conflicts declared.',
       'Velocity Capital Partners LP', 51.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Sandra L. Meyer'
   );

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Velocity Payments LLC', 'Jonathan R. Whitfield; Marcus D. Okonkwo',
       'Robert C. Ellison',
       'MALE', '1967-10-28', 'USA', 'USA',
       'US-PP-593847201', 'N/A', '+16175550210', 'MBA (Wharton), CPA',
       'Wells Fargo Advisory Trust (independent trustee)',
       'Part-time advisory role at Wells Fargo; disclosed to board.',
       'Jonathan R. Whitfield', 34.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Robert C. Ellison'
   );

-- --- DEMO_MWANANCHI trustees ---

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Mwananchi Bank Plc', 'James Kipchoge Mwangi; Grace Wanjiku Ndungu',
       'Carolyne Achieng Okello',
       'FEMALE', '1975-01-09', 'KEN', 'KEN',
       'D45678901', 'D004567890E', '+254745678901', 'LLB (UoN), ICPAK Member',
       NULL, 'No material conflicts declared.',
       'Mwananchi Holdings Ltd', 60.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Carolyne Achieng Okello'
   );

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Mwananchi Bank Plc', 'James Kipchoge Mwangi; Bernard Otieno Ouma',
       'Samson Kamau Njoroge',
       'MALE', '1969-04-17', 'KEN', 'KEN',
       'E56789012', 'E005678901F', '+254756789012', 'BCom (UoN), CPA(K)',
       'Cooperative Bank Foundation Trust (trustee)',
       'Cooperative Bank trustee role disclosed; no conflict with banking operations.',
       'James Kipchoge Mwangi', 25.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Samson Kamau Njoroge'
   );

-- --- DEMO_APEX trustees ---

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Apex Remit Global Ltd', 'Priya Anand Krishnamurthy; Thomas E. Gallagher',
       'Helena J. Broughton',
       'FEMALE', '1978-08-03', 'GBR', 'GBR',
       'GB-PP-612378940', 'N/A', '+447745550198', 'LLM International Finance (LSE), STEP',
       NULL, 'No material conflicts declared.',
       'Apex Global Ventures Ltd', 55.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Helena J. Broughton'
   );

INSERT INTO psp_trustees (
    psp_id, trust_comp_name, directors_trust_comp, trustee_names,
    trustee_gender, dob, nationality, resident_country,
    id_no_passport, pin, contact_number, qualifications,
    others_trusteeships, disclosures, shareholders, shareholding_percentage,
    created_at, updated_at
)
SELECT p.psp_id,
       'Apex Remit Global Ltd', 'Thomas E. Gallagher; Fatima Al-Rashidi',
       'Oliver P. Drummond',
       'MALE', '1972-03-21', 'GBR', 'GBR',
       'GB-PP-744509213', 'N/A', '+442035550627', 'BA Law (Durham), ACCA, CAMS',
       'UK Finance Payments Council (observer)',
       'Observer role at UK Finance; no executive duties; disclosed.',
       'Priya Anand Krishnamurthy', 30.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trustees t
        WHERE t.psp_id = p.psp_id AND t.trustee_names = 'Oliver P. Drummond'
   );

-- ============================================================================
-- 5. SENIOR MANAGEMENT — 4 per PSP
--    Designations: CEO, COO, CFO, Head of Compliance
-- ============================================================================

-- --- DEMO_VELOCITY senior management ---

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Jonathan R. Whitfield', 'MALE', 'Chief Executive Officer',
       '1974-03-15', 'USA', 'US-PP-583920147', 'SSN-***-**-9147',
       'MBA (Harvard Business School), CFA',
       '2019-08-15', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Jonathan R. Whitfield'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Renata B. Sorensen', 'FEMALE', 'Chief Operating Officer',
       '1980-06-14', 'USA', 'US-PP-710934852', 'SSN-***-**-4852',
       'BEng Industrial Engineering (Georgia Tech), PMP',
       '2020-01-06', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Renata B. Sorensen'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Derek O. Flanagan', 'MALE', 'Chief Financial Officer',
       '1976-11-28', 'USA', 'US-PP-629810743', 'SSN-***-**-0743',
       'BSc Accounting (NYU Stern), CPA, CGMA',
       '2020-07-20', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Derek O. Flanagan'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Vivienne L. Castillo', 'FEMALE', 'Head of Compliance',
       '1983-02-08', 'USA', 'US-PP-784031926', 'SSN-***-**-1926',
       'BA Political Science (UCLA), CAMS, CFE',
       '2021-03-01', 'PERMANENT', NULL,
       'ACAMS Metro Chapter (board member)',
       'ACAMS board role is non-remunerated; disclosed to CCO.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Vivienne L. Castillo'
   );

-- --- DEMO_MWANANCHI senior management ---

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'James Kipchoge Mwangi', 'MALE', 'Chief Executive Officer',
       '1971-08-20', 'KEN', 'A12345678', 'A001234567B',
       'BComm (University of Nairobi), CPA(K)',
       '2018-01-10', 'PERMANENT', NULL,
       'Kenya Private Sector Alliance (member)', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'James Kipchoge Mwangi'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Patricia Atieno Odhiambo', 'FEMALE', 'Chief Operating Officer',
       '1978-03-04', 'KEN', 'F67890123', 'F006789012G',
       'BSc Computer Science (UoN), PMP, TOGAF',
       '2019-04-01', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Patricia Atieno Odhiambo'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Elijah Nderitu Kamande', 'MALE', 'Chief Financial Officer',
       '1974-09-19', 'KEN', 'G78901234', 'G007890123H',
       'BCom Finance (Kenyatta University), CPA(K), ICPAK Fellow',
       '2018-01-10', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Elijah Nderitu Kamande'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Ruth Wambui Kariuki', 'FEMALE', 'Head of Compliance',
       '1982-12-30', 'KEN', 'H89012345', 'H008901234I',
       'LLB (UoN), Post-Grad AML Compliance (ICA), CAMS',
       '2020-09-14', 'PERMANENT', NULL,
       'Institute of Certified Anti-Money Laundering Specialists Kenya Chapter (secretary)',
       'Secretary role is voluntary; no remuneration; disclosed.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Ruth Wambui Kariuki'
   );

-- --- DEMO_APEX senior management ---

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Priya Anand Krishnamurthy', 'FEMALE', 'Chief Executive Officer',
       '1979-04-18', 'GBR', 'GB-PP-845621390', 'UTR-9182736450',
       'MSc Financial Engineering (Imperial College London)',
       '2020-05-01', 'PERMANENT', NULL,
       'UK Finance Payments Innovation Council (elected member)',
       'Council role is industry standard; no conflict; disclosed to board.', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Priya Anand Krishnamurthy'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Nathaniel K. Abara', 'MALE', 'Chief Operating Officer',
       '1981-07-05', 'GBR', 'GB-PP-720183946', 'UTR-8271635490',
       'BEng Electrical Engineering (Imperial College), MBA (Insead)',
       '2021-02-15', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Nathaniel K. Abara'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Claire M. Worthington', 'FEMALE', 'Chief Financial Officer',
       '1977-10-11', 'GBR', 'GB-PP-698274031', 'UTR-7364528190',
       'BA Accounting and Finance (University of Edinburgh), ICAEW FCA',
       '2020-05-01', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Claire M. Worthington'
   );

INSERT INTO psp_senior_management (
    psp_id, officer_names, gender, designation, dob, nationality,
    id_no, tax_id, qualification, date_of_emp, emp_type,
    retirement_dt, external_affliates, other_disclosure,
    created_at, updated_at
)
SELECT p.psp_id,
       'Ibrahim O. Suleiman', 'MALE', 'Head of Compliance',
       '1985-05-22', 'KEN', 'KEN-PP-299103847', 'UTR-6253417080',
       'BA Law (Nairobi), LLM AML Compliance (City, University of London), CAMS',
       '2022-10-03', 'PERMANENT', NULL,
       'None', 'None', NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_senior_management m
        WHERE m.psp_id = p.psp_id AND m.officer_names = 'Ibrahim O. Suleiman'
   );

-- ============================================================================
-- 6. PRODUCTS — 3 per PSP
--    no_of_customers in thousands range, no_of_transactions per month,
--    value_of_transactions in natural currency units (not cents).
-- ============================================================================

-- --- DEMO_VELOCITY products ---

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Mobile Wallet', 'OWN', 'DIGITAL_WALLET',
       NULL, 'MW001', 'MIXED',
       'ACTIVE', 'RETAIL', 48750, 312480, 9840000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Mobile Wallet'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'POS Acquiring', 'OWN', 'CARD_ACQUIRING',
       'Visa Inc.; Mastercard Inc.', 'POS001', 'MIXED',
       'ACTIVE', 'MERCHANT', 12300, 891200, 44560000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'POS Acquiring'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Cross-Border Remittance', 'PARTNERED', 'REMITTANCE',
       'TransferWise Ltd.', 'REM001', 'MIXED',
       'ACTIVE', 'DIASPORA', 5820, 42760, 18930000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Cross-Border Remittance'
   );

-- --- DEMO_MWANANCHI products ---

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Mwananchi Mobile Money', 'OWN', 'MOBILE_MONEY',
       NULL, 'MM001', 'MIXED',
       'ACTIVE', 'RETAIL', 210450, 1892300, 4761000000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Mwananchi Mobile Money'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Agency Banking', 'OWN', 'AGENCY_BANKING',
       NULL, 'AB001', 'MIXED',
       'ACTIVE', 'RURAL', 68200, 543800, 1248000000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Agency Banking'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Internet Banking Payments', 'OWN', 'INTERNET_BANKING',
       NULL, 'IB001', 'MIXED',
       'ACTIVE', 'PREMIUM', 18900, 213400, 8934000000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Internet Banking Payments'
   );

-- --- DEMO_APEX products ---

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'Apex Global Remittance', 'OWN', 'REMITTANCE',
       NULL, 'GR001', 'MIXED',
       'ACTIVE', 'DIASPORA', 34200, 187600, 42800000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'Apex Global Remittance'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'FX Exchange Service', 'PARTNERED', 'FOREIGN_EXCHANGE',
       'Barclays Treasury Services', 'FX001', 'MIXED',
       'ACTIVE', 'CORPORATE', 4100, 28400, 318000000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'FX Exchange Service'
   );

INSERT INTO psp_products (
    psp_id, product_name, product_ownership_flag, product_ownership_category,
    product_partner_name, product_transaction_code, gender_segment,
    status_code, band_code, no_of_customers, no_of_transactions,
    value_of_transactions, created_at, updated_at
)
SELECT p.psp_id,
       'API Payment Gateway', 'OWN', 'PAYMENT_GATEWAY',
       NULL, 'PG001', 'MIXED',
       'ACTIVE', 'ENTERPRISE', 820, 641200, 189000000.0000,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_products pr
        WHERE pr.psp_id = p.psp_id AND pr.product_name = 'API Payment Gateway'
   );

-- ============================================================================
-- 7. TRUST ACCOUNTS — 2 per PSP
--    Each references a different commercial bank (bank_id as BIC/code).
--    Balances in natural currency units.
-- ============================================================================

-- --- DEMO_VELOCITY trust accounts ---

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'BOFAUS3N', 'USD-4892017364',
       'OPERATING', NULL, 'FINTECH',
       'Interest reinvested to principal',
       4800000.0000, 4800000.0000, 12400.0000, 4812400.0000, 0.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'USD-4892017364'
   );

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'WFBIUS6S', 'USD-7103849260',
       'RESERVE', NULL, 'FINTECH',
       'Interest held in reserve; no distribution this period',
       2100000.0000, 2100000.0000, 5300.0000, 2105300.0000, 0.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'USD-7103849260'
   );

-- --- DEMO_MWANANCHI trust accounts ---

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'KCBLKENX', 'KES-1001200340056',
       'OPERATING', NULL, 'BANKING',
       'Interest credited monthly to principal',
       580000000.0000, 580000000.0000, 1450000.0000, 581450000.0000, 0.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'KES-1001200340056'
   );

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'EQUIKENA', 'KES-0023040100089',
       'RESERVE', NULL, 'BANKING',
       'Interest utilised for operational expenditure Q1 2026',
       120000000.0000, 120000000.0000, 300000.0000, 120300000.0000, 120000.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'KES-0023040100089'
   );

-- --- DEMO_APEX trust accounts ---

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'BARCGB22', 'GBP-20304050607080',
       'OPERATING', NULL, 'PAYMENTS',
       'Interest reinvested to principal each month-end',
       8500000.0000, 8500000.0000, 21250.0000, 8521250.0000, 0.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'GBP-20304050607080'
   );

INSERT INTO psp_trust_accounts (
    psp_id, bank_id, bank_account_number, trust_acc_dr_type_code,
    org_receiving_donation, sector_code,
    trust_acc_int_utilized_details, opening_balance, principal_amount,
    interest_earned, closing_balance, interest_utilized,
    trust_fields, as_of_date, created_at, updated_at
)
SELECT p.psp_id,
       'HSBCGB2L', 'GBP-91827364550011',
       'RESERVE', NULL, 'PAYMENTS',
       'No interest utilisation this period; held in reserve',
       3200000.0000, 3200000.0000, 8000.0000, 3208000.0000, 0.0000,
       NULL, CURRENT_DATE, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_trust_accounts ta
        WHERE ta.psp_id = p.psp_id AND ta.bank_account_number = 'GBP-91827364550011'
   );

-- ============================================================================
-- 8. TARIFF TEMPLATES — 3 per PSP (POS, ECOMMERCE, MOBILE channels)
-- ============================================================================

-- --- DEMO_VELOCITY tariffs ---

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'POS', 'Visa Inc.; Mastercard Inc.',
       'Merchant discount rate for card-present (POS) transactions',
       1.8500, 0.0000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'POS'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'ECOMMERCE', 'Visa Inc.; Mastercard Inc.; Stripe Inc.',
       'Merchant discount rate for card-not-present (e-commerce) transactions',
       2.4000, 0.3000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'ECOMMERCE'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'MOBILE', NULL,
       'Mobile wallet transaction fee (in-app peer-to-merchant)',
       1.2000, 0.0000,
       '2024-06-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'MOBILE'
   );

-- --- DEMO_MWANANCHI tariffs ---

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'POS', 'Visa Inc.; Mastercard Inc.; Pesalink',
       'MDR for card-present and PesaLink POS transactions (KES)',
       1.5000, 0.0000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'POS'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'ECOMMERCE', 'Visa Inc.; Mastercard Inc.',
       'MDR for card-not-present online transactions (KES)',
       2.0000, 50.0000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'ECOMMERCE'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'MOBILE', 'Safaricom PLC (M-Pesa)',
       'Per-transaction fee for mobile money (Mwananchi Mobile Money) transfers',
       0.0000, 30.0000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'MOBILE'
   );

-- --- DEMO_APEX tariffs ---

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'POS', NULL,
       'Not applicable — Apex Remit does not operate POS terminals',
       0.0000, 0.0000,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'POS'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'ECOMMERCE', NULL,
       'Fee for API-driven cross-border payment gateway transactions (GBP)',
       1.9000, 0.2500,
       '2024-01-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'ECOMMERCE'
   );

INSERT INTO psp_tariff_templates (
    psp_id, channel_used, channel_partner_name, charge_description,
    percentage_transaction_cost, absolute_transaction_cost,
    effective_from, effective_to, created_at, updated_at
)
SELECT p.psp_id,
       'MOBILE', 'Various mobile money operators (Africa)',
       'Remittance disbursement fee for mobile-money last-mile delivery',
       1.0000, 0.1500,
       '2024-06-01', NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_tariff_templates tt
        WHERE tt.psp_id = p.psp_id AND tt.channel_used = 'MOBILE'
   );

-- ============================================================================
-- 9. CYBER INCIDENTS — 2 per PSP
--    incident_number is UNIQUE; use PSP-prefixed codes.
--    incident_mode: ICDT04 (phishing), incident_mode ICDT07 (malware).
--    loss_type: NLSS (no loss), MLOS (minor loss).
--    Dates within past 90 days.
-- ============================================================================

-- --- DEMO_VELOCITY cyber incidents ---

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'VEL-CYB-2026-001',
       NOW() - INTERVAL '62 days',
       'Dallas, Texas, USA',
       'ICDT04',   -- Phishing
       'NLSS',     -- No loss sustained
       'Phishing email campaign targeting customer-support staff. '
       || '14 emails blocked by email gateway; 1 clicked link on isolated sandbox.',
       'Isolated affected workstation; reset credentials for all support staff; '
       || 'issued phishing awareness re-training within 24 hours.',
       (NOW() - INTERVAL '61 days')::TIMESTAMP,
       'Enabled DMARC/DKIM enforcement; deployed advanced email threat protection.',
       0.0000, 0.0000, 'USD',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'VEL-CYB-2026-001'
   );

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'VEL-CYB-2026-002',
       NOW() - INTERVAL '18 days',
       'Unknown (VPN exit node: Amsterdam, NL)',
       'ICDT07',   -- Malware/ransomware attempt
       'MLOS',     -- Minor financial loss
       'Attempted ransomware injection via compromised vendor API key. '
       || 'Endpoint detection blocked file encryption; '
       || '3 non-critical log files rendered unreadable.',
       'Revoked vendor API key immediately; engaged IR firm; '
       || 'full scan of all endpoints returned clean within 4 hours.',
       (NOW() - INTERVAL '16 days')::TIMESTAMP,
       'Rotated all third-party API credentials; implemented secrets-vault rotation policy.',
       850.0000, 200.0000, 'USD',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'VEL-CYB-2026-002'
   );

-- --- DEMO_MWANANCHI cyber incidents ---

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'MWA-CYB-2026-001',
       NOW() - INTERVAL '75 days',
       'Mombasa, Kenya',
       'ICDT04',   -- Social engineering / phishing
       'NLSS',
       'Spear-phishing attempt directed at finance department. '
       || 'Attacker impersonated CBK correspondent; no funds transferred.',
       'Quarantined sender domain; alerted staff; verified all pending payment '
       || 'instructions via out-of-band callback.',
       (NOW() - INTERVAL '74 days')::TIMESTAMP,
       'Deployed email impersonation detection rules; issued all-staff advisory.',
       0.0000, 0.0000, 'KES',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'MWA-CYB-2026-001'
   );

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'MWA-CYB-2026-002',
       NOW() - INTERVAL '31 days',
       'Unknown (exit node: Nairobi, Kenya)',
       'ICDT07',
       'MLOS',
       'Credential-stuffing attack on internet banking portal; '
       || '47 accounts temporarily locked; 2 unauthorised balance inquiries confirmed.',
       'Locked affected accounts; reset all passwords via SMS OTP; '
       || 'forced MFA enrolment on reactivation.',
       (NOW() - INTERVAL '29 days')::TIMESTAMP,
       'Implemented CAPTCHA on login; added rate-limiting (5 attempts / 10 min); '
       || 'deployed behavioural analytics for login anomaly detection.',
       0.0000, 0.0000, 'KES',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'MWA-CYB-2026-002'
   );

-- --- DEMO_APEX cyber incidents ---

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'APX-CYB-2026-001',
       NOW() - INTERVAL '55 days',
       'Unknown (Tor exit node: Frankfurt, DE)',
       'ICDT04',
       'NLSS',
       'Phishing campaign targeting corporate remittance clients. '
       || 'Spoofed Apex Remit domain used; no credential compromise confirmed.',
       'Reported spoofed domain to registrar; domain taken down within 6 hours. '
       || 'Notified impacted clients via secure messaging.',
       (NOW() - INTERVAL '54 days')::TIMESTAMP,
       'Registered defensive domain variants; strengthened DMARC policy to reject.',
       0.0000, 0.0000, 'GBP',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'APX-CYB-2026-001'
   );

INSERT INTO psp_cyber_incidents (
    psp_id, incident_number, incident_date, location_of_attacker,
    incident_mode, loss_type, details, action_taken,
    resolution_date, mitigation_actions,
    amount_involved, amount_lost, currency,
    created_at, updated_at
)
SELECT p.psp_id,
       'APX-CYB-2026-002',
       NOW() - INTERVAL '12 days',
       'Nairobi, Kenya',
       'ICDT07',
       'MLOS',
       'API abuse: automated scripts exploiting a deprecated v1 endpoint '
       || 'to enumerate account reference numbers. ~1200 probe requests logged.',
       'Deprecated endpoint removed immediately; IP ranges blocked; '
       || 'WAF ruleset updated.',
       (NOW() - INTERVAL '11 days')::TIMESTAMP,
       'Enforced API versioning policy; deployed WAF with bot-management profiles; '
       || 'scheduled quarterly API security review.',
       0.0000, 140.0000, 'GBP',
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_cyber_incidents c WHERE c.incident_number = 'APX-CYB-2026-002'
   );

-- ============================================================================
-- 10. SYSTEM INTERRUPTIONS — 3 per PSP
--     severity_interruption_code: SIL1=low, SIL2=medium, SIL3=high
--     recovery_time_code: RTC01=<1h, RTC02=1-4h, RTC03=4-8h
--     remedial_status_code: RSC01=resolved, RSC02=monitoring, RSC03=escalated
--     system_uptime_percentage: numeric(5,2)
-- ============================================================================

-- --- DEMO_VELOCITY system interruptions ---

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '80 days')::DATE,
       'Dallas-TX', 'OWN', NULL, NULL, 'MOBILE_WALLET',
       'SCHED_MAINTENANCE', NULL, 'PLANNED_MAINTENANCE',
       'SIL1', 'RTC01', 'RSC01', 99.98,
       (NOW() - INTERVAL '80 days'),
       (NOW() - INTERVAL '80 days' + INTERVAL '25 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '80 days')::DATE
          AND si.product_type = 'MOBILE_WALLET'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '45 days')::DATE,
       'Atlanta-GA', 'THIRD_PARTY', 'CLOUD_PROVIDER', 'Amazon Web Services (AWS)',
       'POS_ACQUIRING',
       'UNPLANNED', 'AWS EC2 us-east-1 availability zone partial outage',
       'INFRASTRUCTURE_FAILURE', 'SIL2', 'RTC02', 'RSC01', 99.72,
       (NOW() - INTERVAL '45 days'),
       (NOW() - INTERVAL '45 days' + INTERVAL '2 hours 18 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '45 days')::DATE
          AND si.product_type = 'POS_ACQUIRING'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '9 days')::DATE,
       'Dallas-TX', 'OWN', NULL, NULL, 'CROSS_BORDER_REMITTANCE',
       'UNPLANNED', NULL, 'SOFTWARE_BUG',
       'SIL2', 'RTC01', 'RSC01', 99.95,
       (NOW() - INTERVAL '9 days'),
       (NOW() - INTERVAL '9 days' + INTERVAL '42 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '9 days')::DATE
          AND si.product_type = 'CROSS_BORDER_REMITTANCE'
   );

-- --- DEMO_MWANANCHI system interruptions ---

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '70 days')::DATE,
       'Nairobi-CBD', 'OWN', NULL, NULL, 'MOBILE_MONEY',
       'SCHED_MAINTENANCE', NULL, 'PLANNED_MAINTENANCE',
       'SIL1', 'RTC01', 'RSC01', 99.98,
       (NOW() - INTERVAL '70 days'),
       (NOW() - INTERVAL '70 days' + INTERVAL '30 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '70 days')::DATE
          AND si.product_type = 'MOBILE_MONEY'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '38 days')::DATE,
       'Mombasa', 'THIRD_PARTY', 'TELECOMS', 'Safaricom PLC',
       'MOBILE_MONEY',
       'UNPLANNED', 'Safaricom M-Pesa API intermittent 503 errors',
       'THIRD_PARTY_OUTAGE', 'SIL3', 'RTC02', 'RSC01', 98.10,
       (NOW() - INTERVAL '38 days'),
       (NOW() - INTERVAL '38 days' + INTERVAL '3 hours 47 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '38 days')::DATE
          AND si.product_type = 'MOBILE_MONEY'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '6 days')::DATE,
       'Kisumu', 'OWN', NULL, NULL, 'AGENCY_BANKING',
       'UNPLANNED', NULL, 'POWER_OUTAGE',
       'SIL2', 'RTC01', 'RSC01', 99.75,
       (NOW() - INTERVAL '6 days'),
       (NOW() - INTERVAL '6 days' + INTERVAL '55 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '6 days')::DATE
          AND si.product_type = 'AGENCY_BANKING'
   );

-- --- DEMO_APEX system interruptions ---

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '83 days')::DATE,
       'London-EC2', 'OWN', NULL, NULL, 'PAYMENT_GATEWAY',
       'SCHED_MAINTENANCE', NULL, 'PLANNED_MAINTENANCE',
       'SIL1', 'RTC01', 'RSC01', 99.99,
       (NOW() - INTERVAL '83 days'),
       (NOW() - INTERVAL '83 days' + INTERVAL '15 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '83 days')::DATE
          AND si.product_type = 'PAYMENT_GATEWAY'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '52 days')::DATE,
       'London-WC2', 'THIRD_PARTY', 'BANKING_API', 'Barclays Treasury Services',
       'FX_EXCHANGE',
       'UNPLANNED', 'Barclays FX pricing API timeout (>30 s)',
       'THIRD_PARTY_OUTAGE', 'SIL2', 'RTC01', 'RSC01', 99.82,
       (NOW() - INTERVAL '52 days'),
       (NOW() - INTERVAL '52 days' + INTERVAL '48 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '52 days')::DATE
          AND si.product_type = 'FX_EXCHANGE'
   );

INSERT INTO psp_system_interruptions (
    psp_id, reporting_date, sub_county_code, system_owner_flag,
    third_party_owned_category, third_party_name, product_type,
    system_unavailability_type_code, third_party_system_affected,
    service_interruption_cause_code, severity_interruption_code,
    recovery_time_code, remedial_status_code, system_uptime_percentage,
    started_at, resolved_at, created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '14 days')::DATE,
       'Manchester', 'OWN', NULL, NULL, 'REMITTANCE',
       'UNPLANNED', NULL, 'NETWORK_CONGESTION',
       'SIL1', 'RTC01', 'RSC01', 99.95,
       (NOW() - INTERVAL '14 days'),
       (NOW() - INTERVAL '14 days' + INTERVAL '22 minutes'),
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_system_interruptions si
        WHERE si.psp_id = p.psp_id
          AND si.reporting_date = (CURRENT_DATE - INTERVAL '14 days')::DATE
          AND si.product_type = 'REMITTANCE'
   );

-- ============================================================================
-- 11. CUSTOMER COMPLAINTS — 3 per PSP
--     complaint_id is UNIQUE. Dates within past 60 days.
-- ============================================================================

-- --- DEMO_VELOCITY customer complaints ---

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'VEL-COMP-2026-001', 'UNAUTH_TXN', 'MALE',
       1, 'Brandon K. Pierce', 34,
       '+14045550711', 'Atlanta, Georgia, USA',
       'BACHELORS', NULL,
       'VEL-AGT-0042',
       (CURRENT_DATE - INTERVAL '55 days')::DATE,
       (CURRENT_DATE - INTERVAL '53 days')::DATE,
       (CURRENT_DATE - INTERVAL '48 days')::DATE,
       'RESOLVED', 320.00, 320.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'VEL-COMP-2026-001'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'VEL-COMP-2026-002', 'DELAYED_TRANSFER', 'FEMALE',
       1, 'Melissa T. Rhodes', 29,
       '+15105550384', 'Oakland, California, USA',
       'MASTERS', NULL,
       'VEL-AGT-0019',
       (CURRENT_DATE - INTERVAL '33 days')::DATE,
       (CURRENT_DATE - INTERVAL '32 days')::DATE,
       (CURRENT_DATE - INTERVAL '28 days')::DATE,
       'RESOLVED', 0.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'VEL-COMP-2026-002'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'VEL-COMP-2026-003', 'WRONG_AMOUNT_CHARGED', 'MALE',
       2, 'Gregory P. Whitman', 51,
       '+17025550827', 'Las Vegas, Nevada, USA',
       'BACHELORS', 'Second complaint; previously reported overcharge in January 2026.',
       'VEL-AGT-0067',
       (CURRENT_DATE - INTERVAL '11 days')::DATE,
       (CURRENT_DATE - INTERVAL '10 days')::DATE,
       NULL,
       'PENDING', 75.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'VEL-COMP-2026-003'
   );

-- --- DEMO_MWANANCHI customer complaints ---

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'MWA-COMP-2026-001', 'UNAUTH_TXN', 'FEMALE',
       1, 'Aisha Mwende Kamau', 27,
       '+254701234567', 'Westlands, Nairobi',
       'SECONDARY', NULL,
       'MWA-AGT-0013',
       (CURRENT_DATE - INTERVAL '48 days')::DATE,
       (CURRENT_DATE - INTERVAL '47 days')::DATE,
       (CURRENT_DATE - INTERVAL '40 days')::DATE,
       'RESOLVED', 3500.00, 3500.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'MWA-COMP-2026-001'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'MWA-COMP-2026-002', 'ACCOUNT_BLOCKED', 'MALE',
       1, 'Peter Njoroge Kamande', 42,
       '+254712987654', 'Kisumu CBD',
       'BACHELORS', NULL,
       'MWA-AGT-0028',
       (CURRENT_DATE - INTERVAL '25 days')::DATE,
       (CURRENT_DATE - INTERVAL '24 days')::DATE,
       (CURRENT_DATE - INTERVAL '20 days')::DATE,
       'RESOLVED', 0.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'MWA-COMP-2026-002'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'MWA-COMP-2026-003', 'DELAYED_TRANSFER', 'FEMALE',
       1, 'Mercy Atieno Odhiambo', 36,
       '+254723456781', 'Mombasa North',
       'SECONDARY', NULL,
       'MWA-AGT-0041',
       (CURRENT_DATE - INTERVAL '7 days')::DATE,
       (CURRENT_DATE - INTERVAL '6 days')::DATE,
       NULL,
       'UNDER_REVIEW', 0.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'MWA-COMP-2026-003'
   );

-- --- DEMO_APEX customer complaints ---

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'APX-COMP-2026-001', 'DELAYED_TRANSFER', 'FEMALE',
       1, 'Sarah O. Achola', 33,
       '+447800550194', 'Hackney, London',
       'BACHELORS', NULL,
       'APX-AGT-0005',
       (CURRENT_DATE - INTERVAL '57 days')::DATE,
       (CURRENT_DATE - INTERVAL '56 days')::DATE,
       (CURRENT_DATE - INTERVAL '50 days')::DATE,
       'RESOLVED', 0.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'APX-COMP-2026-001'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'APX-COMP-2026-002', 'WRONG_FX_RATE', 'MALE',
       1, 'Kwame Asante Mensah', 45,
       '+447912550736', 'Lewisham, London',
       'MASTERS', 'Customer disputes FX rate applied on 2026-03-12 transfer to Ghana.',
       'APX-AGT-0012',
       (CURRENT_DATE - INTERVAL '29 days')::DATE,
       (CURRENT_DATE - INTERVAL '28 days')::DATE,
       (CURRENT_DATE - INTERVAL '21 days')::DATE,
       'RESOLVED', 24.50, 24.50,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'APX-COMP-2026-002'
   );

INSERT INTO psp_customer_complaints (
    psp_id, complaint_id, complaint_code, complainant_gender,
    complaint_frequency, complainant_name, complainant_age,
    complainant_contact_number, complainant_sub_county_location,
    complainant_education_level, others_complainant_details,
    agent_id, date_of_occurrence, date_reported_to_the_institution,
    date_resolved, remedial_status, amount_lost, amount_recovered,
    created_at, updated_at
)
SELECT p.psp_id,
       'APX-COMP-2026-003', 'UNAUTH_TXN', 'MALE',
       1, 'Emmanuel C. Osei', 38,
       '+447733550941', 'Croydon, London',
       'SECONDARY', NULL,
       'APX-AGT-0021',
       (CURRENT_DATE - INTERVAL '8 days')::DATE,
       (CURRENT_DATE - INTERVAL '7 days')::DATE,
       NULL,
       'UNDER_REVIEW', 180.00, 0.00,
       NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_customer_complaints c WHERE c.complaint_id = 'APX-COMP-2026-003'
   );

-- ============================================================================
-- 12. FRAUD INCIDENTS — 2 per PSP
--     alert_id_link and case_id_link are both NULL (no linked alerts/cases).
--     sub_fraud_code: SFC01=card fraud, SFC02=identity theft, SFC03=account takeover
--     fraud_category_flag: INT=internal, EXT=external
--     victim_category: INDIVIDUAL, MERCHANT, CORPORATE
-- ============================================================================

-- --- DEMO_VELOCITY fraud incidents ---

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '50 days')::DATE,
       'Dallas-TX', 'SFC01', 'EXT', 'INDIVIDUAL',
       'Adult retail customer; card present transaction at POS terminal.',
       (CURRENT_DATE - INTERVAL '51 days')::DATE, 1,
       540.00, 540.00, 540.00,
       'Chargeback filed; card blocked and reissued; customer notified.',
       'Full amount recovered via chargeback from merchant acquirer within 7 days.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '50 days')::DATE
          AND fi.sub_fraud_code = 'SFC01'
   );

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '15 days')::DATE,
       'Atlanta-GA', 'SFC03', 'EXT', 'INDIVIDUAL',
       'Account takeover via credential stuffing; 1 victim account compromised.',
       (CURRENT_DATE - INTERVAL '16 days')::DATE, 1,
       2100.00, 1800.00, 900.00,
       'Account frozen; full investigation launched; police report filed.',
       'Partial recovery via wallet reversal; chargeback on linked card pending.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_VELOCITY'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '15 days')::DATE
          AND fi.sub_fraud_code = 'SFC03'
   );

-- --- DEMO_MWANANCHI fraud incidents ---

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '42 days')::DATE,
       'Nairobi-Westlands', 'SFC02', 'EXT', 'INDIVIDUAL',
       'SIM-swap fraud targeting mobile money account holder.',
       (CURRENT_DATE - INTERVAL '43 days')::DATE, 1,
       45000.00, 45000.00, 45000.00,
       'Notified Safaricom; SIM-swap reversed; full amount recovered from receiving account.',
       'Full amount recouped via intra-network reversal within 4 hours.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '42 days')::DATE
          AND fi.sub_fraud_code = 'SFC02'
   );

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '9 days')::DATE,
       'Mombasa', 'SFC01', 'EXT', 'MERCHANT',
       'Counterfeit card used at a Mombasa agency banking outlet.',
       (CURRENT_DATE - INTERVAL '10 days')::DATE, 3,
       18000.00, 12000.00, 0.00,
       'Outlet notified; card terminal inspected for tampering; police OB filed.',
       'Investigation ongoing; no recovery yet; chargeback process initiated.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_MWANANCHI'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '9 days')::DATE
          AND fi.sub_fraud_code = 'SFC01'
   );

-- --- DEMO_APEX fraud incidents ---

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '38 days')::DATE,
       'London-E1', 'SFC02', 'EXT', 'INDIVIDUAL',
       'Identity document forgery used to open remittance account; '
       || '2 outbound transfers made before detection.',
       (CURRENT_DATE - INTERVAL '40 days')::DATE, 2,
       3800.00, 3800.00, 0.00,
       'Account immediately suspended; funds placed on hold pending investigation; '
       || 'HMRC and NCA notified.',
       'Funds held with receiving bank; legal recovery proceedings initiated.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '38 days')::DATE
          AND fi.sub_fraud_code = 'SFC02'
   );

INSERT INTO psp_fraud_incidents (
    psp_id, reporting_date, sub_county_code, sub_fraud_code,
    fraud_category_flag, victim_category, victim_information,
    date_of_occurrence, number_of_incidences,
    amount_involved, amount_lost, amount_recovered,
    action_taken, recovery_details,
    alert_id_link, case_id_link,
    created_at, updated_at
)
SELECT p.psp_id,
       (CURRENT_DATE - INTERVAL '7 days')::DATE,
       'Manchester', 'SFC03', 'EXT', 'INDIVIDUAL',
       'Account takeover via phishing link; 1 remittance instruction altered.',
       (CURRENT_DATE - INTERVAL '8 days')::DATE, 1,
       920.00, 920.00, 920.00,
       'Transfer recalled immediately; beneficiary account frozen by partner bank.',
       'Full amount recalled within 2 hours via SWIFT gpi recall mechanism.',
       NULL, NULL, NOW(), NOW()
  FROM psps p
 WHERE p.psp_code = 'DEMO_APEX'
   AND NOT EXISTS (
       SELECT 1 FROM psp_fraud_incidents fi
        WHERE fi.psp_id = p.psp_id
          AND fi.reporting_date = (CURRENT_DATE - INTERVAL '7 days')::DATE
          AND fi.sub_fraud_code = 'SFC03'
   );

-- ============================================================================
-- End of V129 CBK demo data seed
-- ============================================================================

COMMIT;
