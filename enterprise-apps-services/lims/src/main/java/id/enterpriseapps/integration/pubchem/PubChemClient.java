package id.enterpriseapps.integration.pubchem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Read-only client for the PubChem PUG REST API (https://pubchem.ncbi.nlm.nih.gov/rest/pug/).
 * Free, no API key required. Looks up compounds by CAS number (preferred) or IUPAC/common name,
 * then fetches GHS classification from the PUG View endpoint.
 */
@Slf4j
@Component
public class PubChemClient {

    private static final String PUG_BASE   = "https://pubchem.ncbi.nlm.nih.gov/rest/pug";
    private static final String VIEW_BASE  = "https://pubchem.ncbi.nlm.nih.gov/rest/pug_view";

    private OkHttpClient http;

    @PostConstruct
    void init() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Enriches a compound using its CAS number (primary) or display name (fallback).
     *
     * @param casNumber   CAS registry number, e.g. "67-64-1"
     * @param displayName Common / IUPAC name used when CAS is blank
     * @return enriched data, or empty if PubChem has no match or is unreachable
     */
    public Optional<PubChemData> enrich(String casNumber, String displayName) {
        try {
            Integer cid = resolveCid(casNumber, displayName);
            if (cid == null) return Optional.empty();

            PubChemData data = new PubChemData();
            data.setCid(cid);
            fetchProperties(cid, data);
            fetchGhs(cid, data);
            return Optional.of(data);
        } catch (Exception e) {
            log.warn("[PubChem] Lookup failed for '{}' / '{}': {}", casNumber, displayName, e.getMessage());
            return Optional.empty();
        }
    }

    /** Resolves a CID from CAS number first, falls back to compound name search. */
    private Integer resolveCid(String casNumber, String displayName) throws Exception {
        if (casNumber != null && !casNumber.isBlank()) {
            Integer cid = cidByName(casNumber);
            if (cid != null) return cid;
        }
        if (displayName != null && !displayName.isBlank()) {
            return cidByName(displayName);
        }
        return null;
    }

    private Integer cidByName(String term) throws Exception {
        String url = PUG_BASE + "/compound/name/"
                + URLEncoder.encode(term, StandardCharsets.UTF_8)
                + "/cids/JSON";
        String body = get(url);
        if (body == null) return null;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("IdentifierList")) return null;
        JsonArray cids = root.getAsJsonObject("IdentifierList").getAsJsonArray("CID");
        if (cids == null || cids.isEmpty()) return null;
        return cids.get(0).getAsInt();
    }

    private void fetchProperties(int cid, PubChemData data) throws Exception {
        String url = PUG_BASE + "/compound/cid/" + cid
                + "/property/IUPACName,MolecularFormula,MolecularWeight,InChIKey,CanonicalSMILES/JSON";
        String body = get(url);
        if (body == null) return;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject props = root.getAsJsonObject("PropertyTable")
                .getAsJsonArray("Properties").get(0).getAsJsonObject();

        if (props.has("IUPACName"))        data.setIupacName(props.get("IUPACName").getAsString());
        if (props.has("MolecularFormula")) data.setMolecularFormula(props.get("MolecularFormula").getAsString());
        if (props.has("MolecularWeight"))  data.setMolecularWeight(props.get("MolecularWeight").getAsDouble());
        if (props.has("InChIKey"))         data.setInchiKey(props.get("InChIKey").getAsString());
        if (props.has("CanonicalSMILES"))  data.setSmiles(props.get("CanonicalSMILES").getAsString());
    }

    /** Fetches GHS pictograms, signal word, and H-statements from PUG View. */
    private void fetchGhs(int cid, PubChemData data) {
        try {
            String url = VIEW_BASE + "/data/compound/" + cid + "/JSON?heading=GHS+Classification";
            String body = get(url);
            if (body == null) return;

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray sections = root.getAsJsonObject("Record")
                    .getAsJsonArray("Section");

            List<String> pictograms = new ArrayList<>();
            List<String> hazardStmts = new ArrayList<>();
            List<String> signalWords = new ArrayList<>();
            traverseGhs(sections, pictograms, hazardStmts, signalWords);

            if (!pictograms.isEmpty())  data.setGhsPictograms(pictograms);
            if (!signalWords.isEmpty()) data.setGhsSignalWord(signalWords.get(0));
            if (!hazardStmts.isEmpty()) data.setHazardStatements(hazardStmts);
        } catch (Exception e) {
            log.debug("[PubChem] GHS fetch failed for CID {}: {}", cid, e.getMessage());
        }
    }

    private void traverseGhs(JsonArray sections, List<String> pictograms,
                              List<String> hazardStmts, List<String> signalWords) {
        if (sections == null) return;
        for (JsonElement se : sections) {
            JsonObject section = se.getAsJsonObject();
            String heading = section.has("TOCHeading") ? section.get("TOCHeading").getAsString() : "";

            if (section.has("Section")) {
                traverseGhs(section.getAsJsonArray("Section"), pictograms, hazardStmts, signalWords);
            }

            if (!section.has("Information")) continue;
            for (JsonElement ie : section.getAsJsonArray("Information")) {
                JsonObject info = ie.getAsJsonObject();
                if (!info.has("Value")) continue;
                JsonObject value = info.getAsJsonObject("Value");

                if (heading.contains("Pictogram") && value.has("StringWithMarkup")) {
                    for (JsonElement sv : value.getAsJsonArray("StringWithMarkup")) {
                        String txt = sv.getAsJsonObject().get("String").getAsString().trim();
                        if (!txt.isEmpty() && !pictograms.contains(txt)) pictograms.add(txt);
                    }
                } else if (heading.contains("Signal") && value.has("StringWithMarkup")) {
                    for (JsonElement sv : value.getAsJsonArray("StringWithMarkup")) {
                        signalWords.add(sv.getAsJsonObject().get("String").getAsString().trim());
                    }
                } else if (heading.contains("Hazard Statement") && value.has("StringWithMarkup")) {
                    for (JsonElement sv : value.getAsJsonArray("StringWithMarkup")) {
                        String txt = sv.getAsJsonObject().get("String").getAsString().trim();
                        if (!txt.isEmpty() && !hazardStmts.contains(txt)) hazardStmts.add(txt);
                    }
                }
            }
        }
    }

    /** Makes a GET request; returns null on 404/429 or network error (non-fatal). */
    private String get(String url) throws Exception {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (resp.code() == 404) return null;
            if (!resp.isSuccessful()) {
                log.warn("[PubChem] HTTP {} for {}", resp.code(), url);
                return null;
            }
            return resp.body() != null ? resp.body().string() : null;
        }
    }
}
