package com.posgateway.aml.model;

/**
 * SAR Type
 * Defines the type of Suspicious Activity Report
 */
public enum SarType {
    INITIAL,            // First report of suspicious activity
    CONTINUING,         // Ongoing suspicious activity (filed every 90 days)
    CORRECTED,          // Correction to a previously filed report
    SUPPLEMENTAL,       // Additional information for a previous report
    JOINT               // Joint filing with another financial institution
}

