UPDATE asset_accounts
SET asset_class = 'TOKENIZED_FIAT'
WHERE product_domain = 'TOKENIZED_FIAT_CBDC';

UPDATE multi_asset_transactions
SET asset_class = 'TOKENIZED_FIAT'
WHERE product_domain = 'TOKENIZED_FIAT_CBDC';
