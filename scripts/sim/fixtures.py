"""Static fixtures for the Hokeka simulator: watchlist entities, PSP profiles, typology parameters.

Everything here is synthetic. Names on the watchlist are invented and deliberately unlike real
people; they exist only so exact / fuzzy / PEP screening can be asserted end to end.
"""

# Synthetic watchlist pushed to aml-microservice (POST /internal/v1/sanctions/ingest) before the
# Control Plane boots — production fails closed on an empty watchlist (SanctionsDataStartupCheck).
WATCHLIST = [
    {"entityId": "SIM-SDN-0001", "name": "Volkan Draxmere", "aliases": ["V. Draxmere", "Volkan Draxmeer"],
     "type": "PERSON", "listName": "SIM-OFAC-SDN", "country": "IR", "birthDate": "1971-03-04"},
    {"entityId": "SIM-SDN-0002", "name": "Oskarine Holdings Ltd", "aliases": ["Oskarine Holdings"],
     "type": "ORGANIZATION", "listName": "SIM-UN-1267", "country": "KP"},
    {"entityId": "SIM-SDN-0003", "name": "Teodric Palmenhaus", "aliases": [],
     "type": "PERSON", "listName": "SIM-EU-CFSP", "country": "SY", "birthDate": "1966-11-20"},
    {"entityId": "SIM-SDN-0004", "name": "Kestrel Bay Trading FZE", "aliases": ["Kestrel Bay Trading"],
     "type": "ORGANIZATION", "listName": "SIM-OFAC-SDN", "country": "AE"},
    {"entityId": "SIM-PEP-0001", "name": "Amarisa Quellenbrook", "aliases": ["Hon. Amarisa Quellenbrook"],
     "type": "PERSON", "listName": "SIM-PEP", "country": "KE", "birthDate": "1969-07-15", "pepLevel": "PEP"},
    {"entityId": "SIM-PEP-0002", "name": "Dorrance Vilkaitis", "aliases": [],
     "type": "PERSON", "listName": "SIM-PEP", "country": "NG", "pepLevel": "RCA"},
]

SANCTIONED_EXACT = "Volkan Draxmere"
SANCTIONED_FUZZY = "Volkan Draksmere"          # one-letter substitution
SANCTIONED_ORG = "Oskarine Holdings Ltd"
PEP_NAME = "Amarisa Quellenbrook"
CLEAN_NAMES = ["Wanjiru Kamau", "Peter Otieno", "Grace Achieng", "Samuel Mwangi", "Lucy Njeri",
               "John Smith", "Maria Garcia", "Chen Wei", "Aisha Bello", "Tom Baker"]

# Three PSP profiles with different business models.
PSPS = [
    {
        "key": "card",
        "code_prefix": "SIMCARD",
        "legalName": "SimCard Acquiring Ltd",
        "tradingName": "SimCard Acquiring",
        "country": "KE",
        "profile": "CARD_ACQUIRER",
        "currency": "USD",
        "merchants": [
            {"name": "Sim Grocer", "mcc": "5411", "country": "KE"},
            {"name": "Sim Electronics", "mcc": "5732", "country": "KE"},
            {"name": "Sim Casino Online", "mcc": "7995", "country": "MT"},    # high-risk MCC
        ],
    },
    {
        "key": "momo",
        "code_prefix": "SIMMOMO",
        "legalName": "SimMobile Money Ltd",
        "tradingName": "SimMoMo",
        "country": "KE",
        "profile": "MOBILE_MONEY",
        "currency": "KES",
        "merchants": [
            {"name": "Sim Agent Westlands", "mcc": "6012", "country": "KE"},
            {"name": "Sim Utility Bills", "mcc": "4900", "country": "KE"},
        ],
    },
    {
        "key": "vasp",
        "code_prefix": "SIMVASP",
        "legalName": "SimChain Exchange Ltd",
        "tradingName": "SimChain",
        "country": "KE",
        "profile": "VASP",
        "currency": "USD",
        "merchants": [
            {"name": "Sim OTC Desk", "mcc": "6051", "country": "KE"},           # high-risk MCC
            {"name": "Sim Wallet Payouts", "mcc": "6012", "country": "KE"},
        ],
    },
]
