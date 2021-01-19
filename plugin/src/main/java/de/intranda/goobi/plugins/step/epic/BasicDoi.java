package de.intranda.goobi.plugins.step.epic;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Most basic necessary info for a DOI: it must contain at least 
 * 
 * - title
 * - author
 * - publisher
 * - publication date
 * - institution
 * 
 */
public class BasicDoi {
    
    public List<String> lstFields;
    public List<String> TITLE;
    public List<String> AUTHORS;
    public List<String> PUBLISHER;
    public List<String> PUBDATE;
    public List<String> INST;

    /**
     * Constructor
     */
    public BasicDoi() {
        lstFields = new ArrayList<String>();
        lstFields.add("TITLE");
        lstFields.add("AUTHORS");
        lstFields.add("PUBLISHER");
        lstFields.add("PUBDATE");
        lstFields.add("INST");
    }

    /**
     * Returns a list of Field-Value pairs with the basic DOI information.
     * 
     * @return
     */
    public List<Pair<String, List<String>>> getValues() {
        List<Pair<String, List<String>>> lstValues = new ArrayList<Pair<String, List<String>>>();
        lstValues.add(Pair.of("TITLE", TITLE));
        lstValues.add(Pair.of("AUTHORS", AUTHORS));
        lstValues.add(Pair.of("PUBLISHER", PUBLISHER));
        lstValues.add(Pair.of("PUBDATE", PUBDATE));
        lstValues.add(Pair.of("INST", INST));
        return lstValues;
    }
}
