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
import ugh.exceptions.UGHException;

@PluginImplementation
@Log4j2
public class EpicPidStepPlugin implements IStepPluginVersion2 {

    private static final long serialVersionUID = 6771665909911957400L;
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
    private MetadataType handleMetadataType;

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
        List<? extends Metadata> metadata = docstruct.getAllMetadataByType(handleMetadataType);
        if (!metadata.isEmpty()) {
            return metadata.get(0).getValue();
        }
        //otherwise
        return null;
    }

    /**
     * Add metadata to the element containing the handle.
     * 
     * @throws HandleException
     */
    private void setHandle(DocStruct docstruct, String handle) throws MetadataTypeNotAllowedException, HandleException {

        if (handle == null || handle.isEmpty()) {
            throw new HandleException(0, "Handle is null or empty");
        }

        Metadata md = new Metadata(handleMetadataType);
        md.setValue(handle);

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
    public String addHandle(DocStruct docstruct, String id, HandleClient handler, boolean includeChildren)
            throws HandleException, IOException, MetadataTypeNotAllowedException {

        //        HandleClient handler = new HandleClient(config);
        //already has a handle?
        String handle = getHandle(docstruct);

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

        if (handle == null) {
            handle = handler.makeURLHandleForObject(id, strPostfix, docstruct);
        } else {
            handler.updateURLHandleForObject(handle, strPostfix, docstruct);
        }

        setHandle(docstruct, handle);

        if (includeChildren && docstruct.getAllChildren() != null) {
            // run recursive through all children
            for (DocStruct ds : docstruct.getAllChildren()) {
                addHandle(ds, id, handler, includeChildren);
            }
        }

        return handle;
    }

    /**
     * Get the CatalogIDDigital from the logical struct
     */
    public String getId(DocStruct logical) {
        List<Metadata> lstMetadata = logical.getAllMetadata();
        if (lstMetadata != null) {
            for (Metadata metadata : lstMetadata) {
                if ("CatalogIDDigital".equals(metadata.getType().getName())) {
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
        String tempFolder = null;
        try {
            synchronized (this) {

                //read the metatdata
                Process process = step.getProzess();
                Prefs prefs = process.getRegelsatz().getPreferences();
                String handleMetadata = config.getString("handleMetadata", "_urn");
                handleMetadataType = prefs.getMetadataTypeByName(handleMetadata);
                Fileformat fileformat = process.readMetadataFile();

                DigitalDocument digitalDocument = fileformat.getDigitalDocument();
                DocStruct logical = digitalDocument.getLogicalDocStruct();
                DocStruct physical = digitalDocument.getPhysicalDocStruct();
                // if it is an anchor record use the first child
                if (logical.getType().isAnchor()) {
                    logical = logical.getAllChildren().get(0);
                }
                String identifier = getId(logical);

                //add handles to each physical and logical element
                HandleClient handler = new HandleClient(config);
                tempFolder = handler.tempFolder;

                //remove handles?
                if (config.getString("removeHandles", "").contentEquals(identifier)) {
                    removeHandlesFromProcess(fileformat, handler, process);
                } else {
                    //otherwise add handles:
                    boolean handleForLogicalDocument = config.getBoolean("handleForLogicalDocument", true);
                    boolean handleForPhysicalDocument = config.getBoolean("handleForPhysicalDocument", true);

                    boolean handleForPhysicalChildren = config.getBoolean("handleForPhysicalPages", true);

                    if (handleForLogicalDocument) {
                        try {
                            String myhandle = addHandle(logical, identifier, handler, false);
                            Helper.addMessageToProcessJournal(getStep().getProcessId(), LogType.INFO, "Handle created: " + myhandle);
                        } catch (HandleException e) {
                            log.error(e.getMessage(), e);
                        }
                    }

                    if (handleForPhysicalDocument) {
                        try {
                            String myhandle = addHandle(physical, identifier, handler, handleForPhysicalChildren);
                            Helper.addMessageToProcessJournal(getStep().getProcessId(), LogType.INFO, "Handle created: " + myhandle);
                        } catch (HandleException e) {
                            log.error(e.getMessage(), e);
                            Helper.addMessageToProcessJournal(getStep().getProcessId(), LogType.ERROR,
                                    "Error registering Handles: " + e.getMessage());
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
            Helper.addMessageToProcessJournal(getStep().getProcessId(), LogType.ERROR, "Error writing Handles: " + e.getMessage());
            successfull = false;
        } finally {
            if (tempFolder != null) {
                StorageProvider.getInstance().deleteDataInDir(Paths.get(tempFolder));
            }
        }

        log.info("Epic Pid step plugin executed");
        if (!successfull) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private void removeHandlesFromProcess(Fileformat fileformat, HandleClient handler, Process process) throws UGHException, HandleException,
            IOException, InterruptedException, SwapException, DAOException {
        DigitalDocument digitalDocument = fileformat.getDigitalDocument();
        DocStruct logical = digitalDocument.getLogicalDocStruct();
        DocStruct physical = digitalDocument.getPhysicalDocStruct();

        //find all the handles
        List<String> lstHandles = getHandles(logical);
        lstHandles.addAll(getHandles(physical));
        boolean successful = true;

        //delete all the handles
        for (String strHandle : lstHandles) {
            try {
                handler.remove(strHandle);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                successful = false;
            }
        }

        //delete all the metadata, but only if no errors:
        if (successful) {
            Metadata md = new Metadata(handleMetadataType);
            removeHandlesFromDoc(logical, md);
            removeHandlesFromDoc(physical, md);
        }
    }

    private void removeHandlesFromDoc(DocStruct docstruct, Metadata md) {

        //If there already is a handle, remove it first.
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

    private List<String> getHandles(DocStruct docstruct) {
        List<String> lstHandles = new ArrayList<>();
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
