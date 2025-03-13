package de.intranda.goobi.plugins.step.epic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
//import org.wiztools.xsdgen.ParseException;

import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.Person;

/**
 * Class for reading the metadata necessary for a DOI out of an XML document (eg a MetsMods file).
 * 
 */
public class MakeDOI {

    /**
     * The mapping document: this shows which metadata from the MetsMods file should be recorded in which filed of the DOI
     * 
     */
    private Document mapping;

    //dictionary of mappings
    private HashMap<String, Element> doiMappings;

    /**
     * Static entry point for testing
     * 
     * @param args
     * @throws IOException
     * @throws ParseException
     * @throws ConfigurationException
     * @throws JDOMException
     */
    public static void main(String[] args) throws IOException, ConfigurationException, JDOMException {
        System.out.println("Start DOI");
        MakeDOI makeDoi = new MakeDOI(args[0]);
        makeDoi.saveXMLStructure("/home/joel/XML/orig.xml", "/home/joel/XML/doi_final.xml", "handle/number");
        System.out.println("Finished");
    }

    /**
     * ctor: takes the mapping file as param.
     * 
     * @throws IOException
     * @throws JDOMException
     */
    public MakeDOI(String strMappingFile) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        File xmlFile = new File(strMappingFile);
        this.mapping = builder.build(xmlFile);
        this.doiMappings = new HashMap<>();
        Element rootNode = mapping.getRootElement();
        for (Element elt : rootNode.getChildren()) {
            doiMappings.put(elt.getChildText("field"), elt);
        }
    }

    /**
     * Given the root elt of the xml file which we are examining, find the text of the entry correspoinding to the DOI field specified
     * 
     * @param field
     * @param root
     * @return
     */
    public List<String> getValues(String field, Element root) {
        Element eltMap = doiMappings.get(field);
        if (eltMap == null) {
            return null;
        }

        //set up the default value:
        String strDefault = eltMap.getChildText("default");
        ArrayList<String> lstDefault = new ArrayList<>();
        if (!strDefault.isEmpty()) {
            lstDefault.add(strDefault);
        }

        //try to find the local value:
        String metadata = eltMap.getChildText("metadata");

        //no local value set? then return default:
        if (metadata.isEmpty()) {
            return lstDefault;
        }

        //otherwise
        List<String> lstLocalValues = getValueRecursive(root, metadata);
        if (!lstLocalValues.isEmpty()) {
            return lstLocalValues;
        }

        //could not find first choice? then try alternatives
        for (Element eltAlt : eltMap.getChildren("altMetadata")) {
            lstLocalValues = getValueRecursive(root, eltAlt.getText());
            if (!lstLocalValues.isEmpty()) {
                return lstLocalValues;
            }
        }

        //otherwise just return default
        return lstDefault;
    }

    /**
     * Find all child elements with the specified name, and return a list of all their values. Note this will STOP at the first level at which it
     * finds a hit: if there are "title" elements at level 2 it will return all of them, and will NOT continue if look for "title" elts at lower
     * levels.
     * 
     * @param root
     * @param metadata
     * @return
     */
    private List<String> getValueRecursive(Element root, String metadata) {
        ArrayList<String> lstValues = new ArrayList<>();
        //if we find the correct named element, do NOT include its children in the search:
        if (root.getName() == metadata) {
            lstValues.add(root.getText());
            return lstValues;
        }
        //recursive:
        for (Element eltChild : root.getChildren()) {
            lstValues.addAll(getValueRecursive(eltChild, metadata));
        }
        return lstValues;
    }

    /**
     * Get the xml in strXmlFilePath, create a DOI file, and save it at strSave.
     * 
     * @param strXmlFilePath
     * @param strSave
     * @throws JDOMException
     * @throws IOException
     */
    public void saveXMLStructure(String strXmlFilePath, String strSave, String strDOI) throws JDOMException, IOException {
        Document docEAD = new Document();
        Element rootNew = new Element("resource");
        docEAD.setRootElement(rootNew);
        makeHeader(rootNew, strDOI);

        //mandatory fields:
        addMandatoryFields(rootNew);

        //        //optional

        //now save:
        XMLOutputter outter = new XMLOutputter();
        outter.setFormat(Format.getPrettyFormat().setIndent("    "));
        outter.output(rootNew, new FileWriter(new File(strSave)));
    }

    /**
     * set the resource attribute, and the identifier and creators nodes
     * 
     * @param root
     * @param strDOI
     */
    private void makeHeader(Element root, String strDOI) {
        Namespace sNS = Namespace.getNamespace("xxxxxxxxx", "http://datacite.org/schema/kernel-4");
        root.setAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", sNS);
        root.setAttribute("schemaLocation", "http://datacite.org/schema/kernel-4 http://schema.datacite.org/meta/kernel-4.2/metadata.xsd", sNS);

        //DOI
        Element ident = new Element("identifier");
        ident.setAttribute("identifierType", "DOI");
        ident.addContent(strDOI);
        root.addContent(ident);

        //Creators
        Element creators = new Element("creators");
        Element creator = new Element("creator");
        List<String> lstCreatorNames = getValues("creatorName", root);

        for (String strCreatorName : lstCreatorNames) {
            Element creatorName = new Element("creatorName");
            creatorName.addContent(strCreatorName);
            creator.addContent(creatorName);
        }
        creators.addContent(creator);
        root.addContent(creators);
    }

    /**
     * Get the metadata values for the specified element name, and add a corresponding Element to the parent for each such value.
     */
    private void addElements(Element parent, String strEltName) {
        List<String> lstValues = getValues(strEltName, parent);
        for (String strValue : lstValues) {
            Element elt = new Element(strEltName);
            elt.addContent(strValue);
            parent.addContent(elt);
        }
    }

    /**
     * Add the Title, Publisher, PublicationYear and ResourceType fields
     * 
     * @param rootNew
     * @param rootNode
     */
    private void addMandatoryFields(Element rootNew) {
        //title
        Element titles = new Element("titles");
        addElements(titles, "title");
        rootNew.addContent(titles);

        //publisher
        Element publisher = new Element("publisher").setAttribute("xml:lang", "de");
        publisher.addContent("Stadtarchiv Duderstadt");
        rootNew.addContent(publisher);

        //PublicationYear
        Element pubYear = new Element("publicationYear");
        pubYear.addContent(String.valueOf(Year.now().getValue()));
        rootNew.addContent(pubYear);

        //resourceType
        Element resourceType = new Element("resourceType").setAttribute("resourceTypeGeneral", "Text");
        resourceType.addContent("Archive text");
        rootNew.addContent(resourceType);
    }

    /**
     * Given the root of an xml tree, get the basic DOI info.
     * 
     * @param physical
     * @return
     */
    public BasicDoi getBasicDoi(DocStruct physical) {
        BasicDoi doi = new BasicDoi();
        doi.TITLE = getValues("title", physical);
        doi.AUTHORS = getValues("author", physical);
        doi.PUBLISHER = getValues("publisher", physical);
        doi.PUBDATE = getValues("pubdate", physical);
        doi.INST = getValues("inst", physical);
        return doi;
    }

    /**
     * Get the values of metadata for the specified field, in the specified struct.
     */
    private List<String> getValues(String field, DocStruct struct) {
        ArrayList<String> lstDefault = new ArrayList<>();
        String metadata = field;
        Element eltMap = doiMappings.get(field);
        if (eltMap != null) {
            //set up the default value:
            String strDefault = eltMap.getChildText("default");
            if (!strDefault.isEmpty()) {
                lstDefault.add(strDefault);
            }
            //try to find the local value:
            metadata = eltMap.getChildText("metadata");
        }

        List<String> lstLocalValues = getMetedataFromMets(struct, metadata);

        if (!lstLocalValues.isEmpty()) {
            return lstLocalValues;
        }

        if (eltMap != null) {
            //could not find first choice? then try alternatives
            for (Element eltAlt : eltMap.getChildren("altMetadata")) {
                lstLocalValues = getMetedataFromMets(struct, eltAlt.getText());
                if (!lstLocalValues.isEmpty()) {
                    return lstLocalValues;
                }
            }
        }

        //otherwise just return default
        return lstDefault;
    }

    /**
     * Get all metadata of type "name" in the specified struct.
     */
    private List<String> getMetedataFromMets(DocStruct struct, String name) {

        if (fieldIsPerson(name)) {
            return getPersonFromMets(struct, name);
        }

        ArrayList<String> lstValues = new ArrayList<>();
        for (Metadata mdata : struct.getAllMetadata()) {
            if (mdata.getType().getName().equalsIgnoreCase(name)) {
                lstValues.add(mdata.getValue());
            }
        }
        return lstValues;
    }

    private boolean fieldIsPerson(String name) {

        if (name == null) {
            return false;
        }

        return "Author".equalsIgnoreCase(name) || "Publisher".equalsIgnoreCase(name);
    }

    /**
     * Get all persons of type "name" in the specified struct.
     */
    private List<String> getPersonFromMets(DocStruct struct, String name) {

        ArrayList<String> lstValues = new ArrayList<>();
        for (Person mdata : struct.getAllPersons()) {
            if (mdata.getRole().equalsIgnoreCase(name)) {
                String strName = mdata.getDisplayname();
                if (strName == null || strName.isEmpty()) {
                    strName = mdata.getLastname();
                }
                if (strName == null || strName.isEmpty()) {
                    strName = mdata.getInstitution();
                }
                lstValues.add(strName);
            }
        }
        return lstValues;
    }

}