package id.enterpriseapps.integration.pubchem;

import lombok.Data;

import java.util.List;

/**
 * Subset of PubChem compound data relevant to a lab context: identity, physical
 * properties, and GHS hazard classification. Returned as-is by the enrich endpoint.
 */
@Data
public class PubChemData {
    private Integer cid;
    private String iupacName;
    private String molecularFormula;
    private Double molecularWeight;
    private String inchiKey;
    private String smiles;

    // GHS hazard pictogram codes e.g. ["GHS01","GHS06"]
    private List<String> ghsPictograms;
    // GHS signal word e.g. "Danger"
    private String ghsSignalWord;
    // GHS H-statements e.g. ["H225 - Highly flammable", ...]
    private List<String> hazardStatements;
}
