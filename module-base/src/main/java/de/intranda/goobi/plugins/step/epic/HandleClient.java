package de.intranda.goobi.plugins.step.epic;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;

import de.sub.goobi.config.ConfigurationHelper;
import lombok.extern.log4j.Log4j;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AdminRecord;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.CreateHandleResponse;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.FilesystemConfiguration;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ModifyValueRequest;
import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.Util;
import ugh.dl.DocStruct;

/**
 * Creates requests for the Handle Service, querying handles and creating new handles.
 */
@Log4j
public class HandleClient {

    //static fields
    private String certificate;
    private String user;
    private String base;
    private String prefix;
    private String separator;
    private static int ADMIN_INDEX = 300; //NOT 28!
    private static int ADMIN_RECORD_INDEX = 100;
    private static int URL_RECORD_INDEX = 1;

    // Non-Static fields
    private PrivateKey privKey;
    PublicKeyAuthenticationInfo authInfo;
    HandleResolver resolver;
    private ArrayList<String> lstCheckedHandles;
    private int iLastSuffix;
    String tempFolder;

    /**
     * Constructor. Note that this resets the last suffix index: if this client is used for multiple IDs, call resetSuffix() between them.
     * 
     * @param config
     * @throws HandleException
     * @throws IOException
     */
    public HandleClient(SubnodeConfiguration config) throws HandleException, IOException {
        this.user = config.getString("user");
        this.base = config.getString("base");
        this.prefix = config.getString("url");
        this.separator = config.getString("separator");
        this.certificate = config.getString("certificate");
        this.privKey = getPemPrivateKey();
        this.authInfo = new PublicKeyAuthenticationInfo(Util.encodeString(user), ADMIN_INDEX, privKey);

        this.lstCheckedHandles = new ArrayList<>();
        //specify the temp folder:
        tempFolder = ConfigurationHelper.getInstance().getTemporaryFolder() + ".handles";
        net.handle.hdllib.FilesystemConfiguration handleConfig = new FilesystemConfiguration(new File(tempFolder));
        handleConfig.setAutoUpdateRootInfo(false);
        resolver = new HandleResolver();
        resolver.setConfiguration(handleConfig);

        resetSuffix();
    }

    /**
     * Given an object with specified ID and postfix, make a handle "base/postfix_id" with URL given in getURLForHandle. Returns the new Handle.
     * 
     */
    public String makeURLHandleForObject(String strObjectId, String strPostfix, DocStruct docstruct) throws HandleException {

        String strNewHandle = newURLHandle(base + "/" + strPostfix + strObjectId, prefix, separator, true);
        String strNewURL = getURLForHandle(strNewHandle);
        if (changeHandleURL(strNewHandle, strNewURL)) {
            return strNewHandle;
        } else {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Failed to create new Handle for " + strObjectId);
        }

    }

    /**
     * Make a new handle with specified URL. If boMintNewSuffix, add a suffix guaranteeing uniquness. Retuns the new handle.
     * 
     * @param newHandle
     * @param url
     * @param separator
     * @param mintNewSuffix
     * @return
     * @throws HandleException
     */
    public String newURLHandle(String newHandle, String url, String separator, boolean mintNewSuffix)
            throws HandleException {

        if (!mintNewSuffix && isHandleRegistered(newHandle)) {
            return newHandle;
        }

        String oldHandle = newHandle;

        //create a unique suffix?
        if (mintNewSuffix) {
            String strTestHandle = newHandle;

            while (isHandleRegistered(strTestHandle)) {
                iLastSuffix++;
                strTestHandle = newHandle + "-" + iLastSuffix;

                if (iLastSuffix > 5000) {
                    throw new HandleException(HandleException.INTERNAL_ERROR, "Registry query always returning true: " + newHandle);
                }
            }

            //test handle ok:
            log.debug("Handle OK " + strTestHandle);
            newHandle = strTestHandle;
        }

        // Define the admin record for the handle we want to create
        AdminRecord admin = createAdminRecord(user, ADMIN_INDEX);

        // Make a create-handle request.
        HandleValue values[] = { new HandleValue(ADMIN_RECORD_INDEX, // unique index
                Util.encodeString("HS_ADMIN"), // handle value type
                Encoder.encodeAdminRecord(admin)), //data

                new HandleValue(URL_RECORD_INDEX, // unique index
                        "URL", // handle value type
                        url) }; //data

        // Create the request to send and the resolver to send it
        log.debug("Create " + newHandle);
        CreateHandleRequest request = new CreateHandleRequest(Util.encodeString(newHandle), values, authInfo);

        //        HandleResolver resolver = new HandleResolver();
        AbstractResponse response;

        // Let the resolver process the request
        response = resolver.processRequest(request);

        // Check the response to see if operation was successful
        if (response.responseCode == AbstractMessage.RC_SUCCESS) {
            //            log.debug(response);
            byte[] btHandle = ((CreateHandleResponse) response).handle;
            String strFinalHandle = Util.decodeString(btHandle);
            log.debug("Handle created: " + Util.decodeString(btHandle));

            if (!lstCheckedHandles.contains(newHandle)) {
                lstCheckedHandles.add(newHandle);
            }

            return strFinalHandle;
        } else if (response.responseCode == AbstractMessage.RC_HANDLE_ALREADY_EXISTS) {

            while (response.responseCode == AbstractMessage.RC_HANDLE_ALREADY_EXISTS) {
                iLastSuffix++;
                String strNext = oldHandle + "-" + iLastSuffix;
                log.debug("Create 2 " + strNext);
                CreateHandleRequest request2 = new CreateHandleRequest(Util.encodeString(strNext), values, authInfo);
                // Let the resolver process the request
                response = resolver.processRequest(request2);
                if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                    log.debug(response);
                    byte[] btHandle = ((CreateHandleResponse) response).handle;
                    String strFinalHandle = Util.decodeString(btHandle);
                    log.debug("Handle created: " + Util.decodeString(btHandle));
                    if (!lstCheckedHandles.contains(strFinalHandle)) {
                        lstCheckedHandles.add(strFinalHandle);
                    }
                    return strFinalHandle;
                }

                if (iLastSuffix > 5000) {
                    throw new HandleException(HandleException.INTERNAL_ERROR,
                            "Failed trying to create handle at the server, response was" + response + " " + newHandle);
                }
            }
        }

        //otherwise:

        throw new HandleException(HandleException.INTERNAL_ERROR,
                "Failed trying to create a new handle at the server, response was" + response + " " + newHandle);

    }

    private String getURLForHandle(String strHandle) {
        return prefix + strHandle;
    }

    /**
     * Given an object with specified handle, update the URL
     * 
     */
    public void updateURLHandleForObject(String handle, String strPostfix, DocStruct docstruct) throws HandleException {

        String strNewURL = getURLForHandle(handle);
        changeHandleURL(handle, strNewURL);

    }

    /**
     * Make a new handle with specified URL. If boMintNewSuffix, add a suffix guaranteeing uniquness. Retuns the new handle.
     * 
     * @param handle
     * @param url
     * @return
     * @throws HandleException
     */
    public boolean updateHandle(String handle, String url) throws HandleException {

        // Define the admin record for the handle we want to create
        AdminRecord admin = createAdminRecord(user, ADMIN_INDEX);

        // Make a create-handle request.
        HandleValue values[] = { new HandleValue(ADMIN_RECORD_INDEX, // unique index
                Util.encodeString("HS_ADMIN"), // handle value type
                Encoder.encodeAdminRecord(admin)), //data

                new HandleValue(URL_RECORD_INDEX, // unique index
                        "URL", // handle value type
                        url) }; //data

        // Create the request to send and the resolver to send it
        ModifyValueRequest request = new ModifyValueRequest(Util.encodeString(handle), values, authInfo);

        AbstractResponse response;

        // Let the resolver process the request
        response = resolver.processRequest(request);

        // Check the response to see if operation was successful
        if (response.responseCode == AbstractMessage.RC_SUCCESS) {
            log.debug(response);
            return true;
        } else {
            log.debug(response);
            return false;
        }
    }

    /**
     * Change the URL for the handle. Returns true if successful, false otherwise
     */
    public boolean changeHandleURL(String handle, String newUrl) throws HandleException {
        if (StringUtils.isEmpty(handle) || StringUtils.isEmpty(newUrl)) {
            throw new IllegalArgumentException("handle and URL cannot be empty");
        }
        log.debug("Update Handle: " + handle + " new URL: " + newUrl);
        try {
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            HandleValue handleNew = new HandleValue(URL_RECORD_INDEX, "URL", newUrl);
            handleNew.setTimestamp(timestamp);
            // Make a create-handle request.
            HandleValue values[] = { handleNew };
            ModifyValueRequest req = new ModifyValueRequest(Util.encodeString(handle), values, authInfo);
            //            HandleResolver resolver = new HandleResolver();
            AbstractResponse response = resolver.processRequest(req);
            String msg = AbstractMessage.getResponseCodeMessage(response.responseCode);
            log.debug("Response code from Handle request: " + msg);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                return false;
            }
        } catch (HandleException e) {
            log.error("Tried to update handle " + handle + " but failed.", e);
            throw e;
        }
        return true;
    }

    /**
     * Create the NA admin record for a new handle. The NA admin is provided all permissions bar ADD_NA and DELETE_NA
     * 
     * @return AdminRecord an AdminRecord object representing the NA handle admin
     * @param handle the NA admin handle in byte form
     * @param idx the handle index the of the NA handle's HS_VLIST entry
     */
    public AdminRecord createAdminRecord(String handle, int idx) {
        return new AdminRecord(Util.encodeString(handle), idx, AdminRecord.PRM_ADD_HANDLE, AdminRecord.PRM_DELETE_HANDLE, AdminRecord.PRM_NO_ADD_NA,
                AdminRecord.PRM_NO_DELETE_NA, AdminRecord.PRM_READ_VALUE, AdminRecord.PRM_MODIFY_VALUE, AdminRecord.PRM_REMOVE_VALUE,
                AdminRecord.PRM_ADD_VALUE, AdminRecord.PRM_MODIFY_ADMIN, AdminRecord.PRM_REMOVE_ADMIN, AdminRecord.PRM_ADD_ADMIN,
                AdminRecord.PRM_LIST_HANDLES);
    }

    /**
     * Extract the private key from the .PEM certificate.
     * 
     */
    public PrivateKey getPemPrivateKey() throws HandleException, IOException {
        File f = new File(certificate);
        FileInputStream fis = new FileInputStream(f);
        DataInputStream dis = new DataInputStream(fis);
        byte[] keyBytes = new byte[(int) f.length()];
        dis.readFully(keyBytes);
        dis.close();

        String temp = new String(keyBytes);
        String privKeyPEM = temp.replace("-----BEGIN PRIVATE KEY-----", "");
        privKeyPEM = privKeyPEM.replace("-----END PRIVATE KEY-----", "");
        privKeyPEM = privKeyPEM.replace("\r\n", "");
        byte[] decoded = Base64.getDecoder().decode(privKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);

        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            log.error("Error reading the private key", e);
            throw new IOException("Failed to generate private key", e);
        }
    }

    /**
     * Returns true if the handle has already been registered, false otherwise.
     * 
     */
    public boolean isHandleRegistered(String handle) throws HandleException {

        //already checked?
        if (lstCheckedHandles.contains(handle)) {
            return true;
        }

        //otherwise check:
        boolean handleRegistered = false;
        ResolutionRequest req = buildResolutionRequest(handle);
        AbstractResponse response = null;
        //        HandleResolver resolver = new HandleResolver();
        try {
            response = resolver.processRequest(req);
        } catch (HandleException ex) {
            log.error("Caught exception trying to process lookup request", ex);
            throw ex;
        }
        if ((response != null && response.responseCode == AbstractMessage.RC_SUCCESS)) {
            log.debug("Handle " + handle + " registered.");
            handleRegistered = true;
        }
        if ((response != null && response.responseCode != AbstractMessage.RC_HANDLE_NOT_FOUND)) {
            log.debug("Handle " + handle + " has error: " + response.responseCode);
            handleRegistered = true;
        }

        //save, so do not need to call again:
        if (handleRegistered) {
            lstCheckedHandles.add(handle);
        }

        return handleRegistered;
    }

    private ResolutionRequest buildResolutionRequest(final String handle) throws HandleException {
        //find auth info for the whole domain:
        String handlePrefix = handle.substring(0, handle.indexOf("/"));
        PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(Util.encodeString(handlePrefix), 300, privKey);

        byte[][] types = null;
        int[] indexes = null;
        ResolutionRequest req = new ResolutionRequest(Util.encodeString(handle), types, indexes, auth);
        req.certify = false;
        req.cacheCertify = true;
        req.authoritative = false;
        req.ignoreRestrictedValues = true;
        return req;
    }

    /**
     * Restart the counter for suffixes
     */
    public void resetSuffix() {
        this.iLastSuffix = -1;
    }

    /**
     * Remove a handle.
     * 
     * @param handle
     * @return true if a handle was removed, false otherwise
     * @throws HandleException
     */
    public boolean remove(String handle) throws HandleException {
        // Create the request to send and the resolver to send it
        DeleteHandleRequest request = new DeleteHandleRequest(Util.encodeString(handle), authInfo);

        HandleResolver resolver = new HandleResolver();
        AbstractResponse response;

        // Let the resolver process the request
        response = resolver.processRequest(request);

        // Check the response to see if operation was successful
        if (response.responseCode == AbstractMessage.RC_SUCCESS) {

            log.info("Handle deleted: " + handle);
            return true;
        } else if (response.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {

            log.info("Handle not found: " + handle);
            return false;
        } else {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Failed trying to delete a new handle at the server, response was" + response);
        }
    }
}
