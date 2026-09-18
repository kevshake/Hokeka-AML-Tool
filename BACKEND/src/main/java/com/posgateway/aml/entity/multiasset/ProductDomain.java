package com.posgateway.aml.entity.multiasset;

public enum ProductDomain {
    BANKING(AssetClass.BANKING),
    SECURITIES_AML(AssetClass.SECURITIES),
    SECURITIES_MARKET_SURVEILLANCE(AssetClass.SECURITIES),
    E_MONEY_MOBILE_MONEY(AssetClass.E_MONEY),
    PREPAID_CLOSED_LOOP(AssetClass.E_MONEY),
    VIRTUAL_ASSET(AssetClass.CRYPTO),
    TOKENIZED_FIAT_CBDC(AssetClass.TOKENIZED_FIAT);

    private final AssetClass assetClass;

    ProductDomain(AssetClass assetClass) {
        this.assetClass = assetClass;
    }

    public AssetClass assetClass() {
        return assetClass;
    }

    public boolean supports(AssetClass candidate) {
        return assetClass == candidate;
    }

    public static ProductDomain defaultFor(AssetClass assetClass) {
        return switch (assetClass) {
            case BANKING -> BANKING;
            case SECURITIES -> SECURITIES_AML;
            case E_MONEY -> E_MONEY_MOBILE_MONEY;
            case TOKENIZED_FIAT -> TOKENIZED_FIAT_CBDC;
            case CRYPTO -> VIRTUAL_ASSET;
        };
    }

    public static ProductDomain forAccountType(AssetAccountType accountType, AssetClass assetClass) {
        if (accountType == null) return defaultFor(assetClass);
        return switch (accountType) {
            case BANK_ACCOUNT -> BANKING;
            case BROKERAGE -> SECURITIES_AML;
            case MOBILE_MONEY, DIGITAL_WALLET -> E_MONEY_MOBILE_MONEY;
            case PREPAID_VALUE -> PREPAID_CLOSED_LOOP;
            case TOKENIZED_FIAT_ACCOUNT -> TOKENIZED_FIAT_CBDC;
            case CRYPTO_WALLET, EXCHANGE_ACCOUNT -> VIRTUAL_ASSET;
        };
    }
}
