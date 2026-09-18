CREATE TABLE IF NOT EXISTS high_risk_countries (
    id SERIAL PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL UNIQUE,
    country_name VARCHAR(100),
    risk_level VARCHAR(20) DEFAULT 'HIGH',
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(100)
);

INSERT INTO high_risk_countries (country_code, country_name, risk_level, added_by, added_at) VALUES
('AF', 'Afghanistan', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP),
('KP', 'North Korea', 'CRITICAL', 'SYSTEM', CURRENT_TIMESTAMP),
('IR', 'Iran', 'CRITICAL', 'SYSTEM', CURRENT_TIMESTAMP),
('SY', 'Syria', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP),
('YE', 'Yemen', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP),
('MM', 'Myanmar', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP),
('VE', 'Venezuela', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP),
('ZW', 'Zimbabwe', 'HIGH', 'SYSTEM', CURRENT_TIMESTAMP)
ON CONFLICT (country_code) DO NOTHING;

