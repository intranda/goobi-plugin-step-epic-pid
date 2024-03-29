package de.intranda.goobi.plugins.step.epic;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.handle.hdllib.HandleException;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class EpicPidStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_epic_pid";
    @Getter
    private Step step;
    private String returnPath;
    @Getter
    @Setter
    private SubnodeConfiguration config;
    @Getter
    @Setter
    private MetadataType urn;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        // read parameters from correct block in configuration file
        config = ConfigPlugins.getProjectAndStepConfig(title, step);
        log.info("EpicPid step plugin initialized");
    }

    /**
     * If the element already has a handle, return it, otherwise return null.
     */
    private String getHandle(DocStruct docstruct) {
        List<? extends Metadata> lstURN = docstruct.getAllMetadataByType(urn);
        if (lstURN.size() == 1) {
            return lstURN.get(0).getValue();
        }
        //otherwise
        return null;
    }

    /**
     * Add metadata to the element containing the handle.
     * 
     * @throws HandleException
     */
    private void setHandle(DocStruct docstruct, String strHandle) throws MetadataTypeNotAllowedException, HandleException {

        if (strHandle == null || strHandle.isEmpty()) {
            throw new HandleException(0, "Handle is null or empty");
        }

        Metadata md = new Metadata(urn);
        md.setValue(strHandle);

        //If there already is a urn, remove it first.
        if (!docstruct.getAllMetadataByType(md.getType()).isEmpty()) {
            docstruct.removeMetadata(docstruct.getAllMetadataByType(md.getType()).get(0));
        }

        docstruct.addMetadata(md);
    }

    /**
     * check if Metadata handle exists if not, create handle and save it under "_urn" in the docstruct.
     * 
     * if it exists, change it
     * 
     * @return Returns the handle.
     */
    public String addHandle(DocStruct docstruct, String strId, Boolean boMakeDOI, HandleClient handler, Boolean boChildren)
            throws HandleException, IOException, MetadataTypeNotAllowedException {

        //        HandleClient handler = new HandleClient(config);
        //already has a handle?
        String strHandle = getHandle(docstruct);

        //if not, make one.
        if (boMakeDOI) {
            handler.setDOIMappingFile(config.getString("doiMapping", null));
        }

        String name = config.getString("name");
        String prefix = config.getString("prefix");
        String separator = config.getString("separator", "-");
        String strPostfix = "";
        if (prefix != null && !prefix.isEmpty()) {
            strPostfix = prefix + separator;
        }
        if (name != null && !name.isEmpty()) {
            strPostfix += name + separator;
        }

        if (strHandle == null) {
            strHandle = handler.makeURLHandleForObject(strId, strPostfix, boMakeDOI, docstruct);
        } else {
            handler.updateURLHandleForObject(strHandle, strPostfix, boMakeDOI, docstruct);
        }

        setHandle(docstruct, strHandle);

        if (boChildren && docstruct.getAllChildren() != null) {
            // run recursive through all children
            for (DocStruct ds : docstruct.getAllChildren()) {
                addHandle(ds, strId, false, handler, boChildren);
            }
        }

        return strHandle;
    }

    /**
     * Get the CatalogIDDigital from the logical struct
     */
    public String getId(DocStruct logical) {
        List<Metadata> lstMetadata = logical.getAllMetadata();
        if (lstMetadata != null) {
            for (Metadata metadata : lstMetadata) {
                if (metadata.getType().getName().equals("CatalogIDDigital")) {
                    return metadata.getValue();
                }
            }
        }
        //otherwise:
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successfull = true;
        String strTempFolder = null;
        try {
            synchronized (this) {

                //read the metatdata
                Process process = step.getProzess();
                Prefs prefs = process.getRegelsatz().getPreferences();
                String strUrn = config.getString("handleMetadata", "_urn");
                urn = prefs.getMetadataTypeByName(strUrn);
                Fileformat fileformat = process.readMetadataFile();

                DigitalDocument digitalDocument = fileformat.getDigitalDocument();
                DocStruct logical = digitalDocument.getLogicalDocStruct();
                DocStruct physical = digitalDocument.getPhysicalDocStruct();
                // if it is an anchor record use the first child
                if (logical.getType().isAnchor()) {
                    logical = logical.getAllChildren().get(0);
                }
                String strId = getId(logical);

                //add handles to each physical and logical element
                Boolean boMakeDOI = config.getBoolean("doiGenerate", false);
                HandleClient handler = new HandleClient(config);
                strTempFolder = handler.tempFolder;

                //remove handles?
                if (config.getString("removeHandles", "").contentEquals(strId)) {
                    removeHandlesFromProcess(fileformat, handler, process);
                } else {
                    //otherwise add handles:
                    boolean handleForLogicalDocument = config.getBoolean("handleForLogicalDocument", true);
                    boolean handleForPhysicalDocument = config.getBoolean("handleForPhysicalDocument", true);

                    boolean boPhysicalChildren = config.getBoolean("handleForPhysicalPages", true);
                    
                    if (handleForLogicalDocument) {
                        try {
                            String myhandle = addHandle(logical, strId, boMakeDOI, handler, false);
                            Helper.addMessageToProcessLog(getStep().getProcessId(), LogType.INFO, "Handle created: " + myhandle);
                        } catch (HandleException e) {
                            log.error(e.getMessage(), e);
                        }
                    }

                    if (handleForPhysicalDocument) {
                        try {
                            String myhandle = addHandle(physical, strId, false, handler, boPhysicalChildren);
                            Helper.addMessageToProcessLog(getStep().getProcessId(), LogType.INFO, "Handle created: " + myhandle);
                        } catch (HandleException e) {
                            log.error(e.getMessage(), e);
                            Helper.addMessageToProcessLog(getStep().getProcessId(), LogType.ERROR, "Error registering Handles: " + e.getMessage());
                            successfull = false;
                        }
                    }
                }

                //and save the metadata again.
                if (successfull) {
                    process.writeMetadataFile(fileformat);
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            Helper.addMessageToProcessLog(getStep().getProcessId(), LogType.ERROR, "Error writing Handles: " + e.getMessage());
            successfull = false;
        } finally {
            if (strTempFolder != null) {
                StorageProvider.getInstance().deleteDataInDir(Paths.get(strTempFolder));
            }
        }

        log.info("Epic Pid step plugin executed");
        if (!successfull) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private void removeHandlesFromProcess(Fileformat fileformat, HandleClient handler, Process process) throws PreferencesException, HandleException,
            MetadataTypeNotAllowedException, WriteException, IOException, InterruptedException, SwapException, DAOException {
        DigitalDocument digitalDocument = fileformat.getDigitalDocument();
        DocStruct logical = digitalDocument.getLogicalDocStruct();
        DocStruct physical = digitalDocument.getPhysicalDocStruct();

        //find all the handles
        ArrayList<String> lstHandles = getHandles(logical);
        lstHandles.addAll(getHandles(physical));
        Boolean boOk = true;

        //delete all the handles
        for (String strHandle : lstHandles) {
            try {
                handler.remove(strHandle);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                boOk = false;
            }
        }

        //delete all the metadata, but only if no errors:
        if (boOk) {
            Metadata md = new Metadata(urn);
            removeHandlesFromDoc(logical, md);
            removeHandlesFromDoc(physical, md);
        }
    }

    private void removeHandlesFromDoc(DocStruct docstruct, Metadata md) {

        //If there already is a urn, remove it first.
        if (!docstruct.getAllMetadataByType(md.getType()).isEmpty()) {
            docstruct.removeMetadata(docstruct.getAllMetadataByType(md.getType()).get(0));
        }

        //then for all children:
        if (docstruct.getAllChildren() != null) {
            // run recursive through all children
            for (DocStruct ds : docstruct.getAllChildren()) {
                removeHandlesFromDoc(ds, md);
            }
        }

    }

    private ArrayList<String> getHandles(DocStruct docstruct) {
        ArrayList<String> lstHandles = new ArrayList<String>();
        String strHandle = getHandle(docstruct);
        if (strHandle != null) {
            lstHandles.add(strHandle);
        }

        if (docstruct.getAllChildren() != null) {
            // run recursive through all children
            for (DocStruct ds : docstruct.getAllChildren()) {
                lstHandles.addAll(getHandles(ds));
            }
        }

        return lstHandles;
    }

}
