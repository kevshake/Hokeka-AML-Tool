package com.hokeka.edge.sanctions;

import java.util.List;
import java.util.Objects;

/**
 * One public list entry ({@code docs/contracts/sanctions-list-entry.schema.json}).
 *
 * @param id       public watchlist entry id, {@code <listId>:<entryId>}
 * @param name     primary listed name (may be empty)
 * @param aliases  other listed names, source order, no blanks
 * @param type     PERSON / ORGANIZATION / VESSEL / UNKNOWN
 * @param pepLevel {@code PEP} or {@code RCA} for politically exposed persons, else null
 */
public record SanctionsEntry(String id, String name, List<String> aliases, String type, String pepLevel) {

    /** Normalises nulls. */
    public SanctionsEntry {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        type = type == null ? "UNKNOWN" : type;
    }

    /** True for a PEP / RCA entry (never a sanctions hit, contract 6.5). */
    public boolean pep() {
        return pepLevel != null;
    }
}
