package de.intranda.goobi.plugins.step.epic;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.junit.Test;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.ResolutionResponse;
import net.handle.hdllib.Util;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

public class EpicPidPluginTest {

    @Test
    public void testVersion() throws IOException {
        String s = "xyz";
        assertNotNull(s);
    }

    //for testing:
    private static String rulesetExample = "/opt/digiverso/goobi/test/klassik.xml";
    private static String xmlExample = "/opt/digiverso/goobi/test/meta.xml";
    private static String xmlOut = "/opt/digiverso/goobi/test/doiTEST.xml";

    private static String strConfig = "/opt/digiverso/goobi/config/plugin_intranda_step_handle_mets.xml";

    //Testing:
    public static void main(String[] args) throws Exception {

        EpicPidStepPlugin plug = new EpicPidStepPlugin();

        XMLConfiguration xmlConfig = new XMLConfiguration(new File(strConfig));
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;
        myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");

        plug.setConfig(myconfig);

        Prefs prefs = new Prefs();
        prefs.loadPrefs(rulesetExample);
        plug.setUrn(prefs.getMetadataTypeByName("_urn"));

        //read the metatdata
        Fileformat fileformat = new MetsMods(prefs);
        fileformat.read(xmlExample);

        DigitalDocument digitalDocument = fileformat.getDigitalDocument();
        DocStruct logical = digitalDocument.getLogicalDocStruct();
        DocStruct physical = digitalDocument.getPhysicalDocStruct();

        String strId = plug.getId(logical);

        //add handles to each physical and logical element
        String strLogicalHandle = plug.addHandle(logical, strId, true);

        //already carried out: "21.T11998/goobi-go-1296243265-1"; 
        // http://hdl.handle.net/21.T11998/goobi-go-1296243265-1

        plug.addHandle(physical, strId, true);

        //            //Add DOI?
        //            if (plug.config.getBoolean("doiGenerate")) {
        //
        //                plug.addDOI(logical, strLogicalHandle);
        //            }

        fileformat.write(xmlOut);
    }

    //-----------------Testing:---------------------
    public static void testClient(String[] args) throws HandleException, IOException, ConfigurationException {
        String strConfig = "/opt/digiverso/goobi/config/plugin_intranda_step_handle_mets.xml";
        XMLConfiguration xmlConfig = new XMLConfiguration(new File(strConfig));
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
        HandleClient test = new HandleClient(myconfig);

        //        test.resolveRequest("300:200/23");
        //        test.resolveRequest("45678/1");
        //        test.resolveRequest("200/23");
        //      test.resolveRequest(21.T11998/TEST015c2702c8-d2ca-40b7-9220-400225b19cc3);
        //        test.resolveRequest("10.1594/GFZ.ISDC.CHAMP/CH-ME-2-PLP");
        //
        //        test.resolveRequest("20.1000/100");
        //        test.resolveRequest("21.T11998/TEMPLATEHANDLE");
        //        test.resolveRequest(strUserHandle);
        //
        //        String strUniqueHandle = test.newURLHandle("21.T11998/TEST02", "https://stackoverflow.com/", false);
        //        //        test.changleHandleURL(strUniqueHandle, "https://www.theguardian.com/international");
        //        test.resolveRequest(strUniqueHandle);
        //
        EpicPidPluginTest.resolveRequest("21.T11998/TEST02", test.authInfo);
        //        test.changleHandleURL("21.T11998/TEST02", "https://www.theguardian.com/international");
        //        test.resolveRequest("21.T11998/TEST02");

        //        AddValueRequest 
    }
    
    /**
     * Check that a handle request can be carried out without an exception being thrown.
     * @param authInfo 
     */
    public static void resolveRequest(String strHandle, PublicKeyAuthenticationInfo authInfo) throws HandleException {
        // Get the UTF8 encoding of the desired handle.
        byte bytesHandle[] = Util.encodeString(strHandle);
        // Create a resolution request.
        // (without specifying any types, indexes)
        ResolutionRequest request = new ResolutionRequest(bytesHandle, null, null, authInfo);
        HandleResolver resolver = new HandleResolver();
        AbstractResponse response = resolver.processRequest(request);

        // Check the response to see if the operation was successful.
        if (response.responseCode == AbstractMessage.RC_SUCCESS) {
            // The resolution was successful, so we'll cast the response
            // and get the handle values.
            HandleValue values[] = ((ResolutionResponse) response).getHandleValues();
           
        } else {
            //not successful
        }
    }
}
