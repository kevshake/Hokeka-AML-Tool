package com.posgateway.aml.service.merchant;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for mapping MCC codes to descriptions
 */
@Service
public class MccMappingService {

    private final Map<String, String> mccMap;

    public MccMappingService() {
        mccMap = new HashMap<>();
        // Population with common MCC codes
        mccMap.put("5734", "Computer Software Stores");
        mccMap.put("5651", "Family Clothing Stores");
        mccMap.put("5812", "Eating Places, Restaurants");
        mccMap.put("5411", "Grocery Stores, Supermarkets");
        mccMap.put("6211", "Security Brokers/Dealers");
        mccMap.put("7995", "Betting, Track/Casino/Lotto");
        mccMap.put("7273", "Dating and Escort Services");
        mccMap.put("5993", "Cigar Stores and Stands");
        mccMap.put("6051", "Non-FI, Stored Value Card/Magnetic Stripe");
        mccMap.put("4814", "Telecommunication Services");
        mccMap.put("4121", "Taxicabs and Limousines");
        mccMap.put("5311", "Department Stores");
        mccMap.put("7011", "Hotels, Motels, Resorts");
        mccMap.put("4722", "Travel Agencies and Tour Operators");
        mccMap.put("8011", "Doctors and Physicians");
    }

    /**
     * Get description for an MCC code
     */
    public String getDescription(String mcc) {
        return mccMap.getOrDefault(mcc, "Unknown Category");
    }
}
