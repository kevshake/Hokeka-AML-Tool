-- WP-0A: merchants.cra is 0-100. persistCra used to store cra/100.
-- Rescale only values still on the 0-1 scale.
UPDATE merchants
SET cra = cra * 100
WHERE cra IS NOT NULL
  AND cra <= 1;
