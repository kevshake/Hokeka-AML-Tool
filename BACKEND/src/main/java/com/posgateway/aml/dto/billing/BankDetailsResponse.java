package com.posgateway.aml.dto.billing;

/**
 * Bank transfer details displayed to the PSP when they choose bank transfer.
 */
public class BankDetailsResponse {

    private String bankName;
    private String accountName;
    private String accountNumber;
    private String branch;
    private String swiftCode;

    public BankDetailsResponse() {
    }

    public BankDetailsResponse(String bankName, String accountName, String accountNumber,
                               String branch, String swiftCode) {
        this.bankName = bankName;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.branch = branch;
        this.swiftCode = swiftCode;
    }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getSwiftCode() { return swiftCode; }
    public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }
}
