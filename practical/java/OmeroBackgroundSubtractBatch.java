/*
 *------------------------------------------------------------------------------
 *  Copyright (C) 2017 University of Dundee & Open Microscopy Environment.
 *  All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import ij.IJ;
import ij.ImagePlus;
import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import ome.formats.importer.cli.ErrorHandler;
import ome.formats.importer.cli.LoggingImportMonitor;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.log.SimpleLogger;


/**
 * This script uses ImageJ to Subtract Background
 * The purpose of the script is to be used in the Scripting Dialog
 * of Fiji.
 */
public class OmeroBgSubtractBatch {

    // Edit value
    private static String USERNAME = "changeMe";

    // Edit value
    private static long datasetId = 0;

    private static String HOST = "outreach.openmicroscopy.org";

    private static int PORT = 4064;

    private static String PASSWORD = "changeMe";

    private static String  groupId = "-1";

    /**
     * Open an image using the Bio-Formats importer.
     *
     * @param imageId The id of the image to open
     * @throws Exception
     */
    private void openImagePlus(long imageId)
        throws Exception
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append("location=[OMERO] open=[omero:server=");
        buffer.append(HOST);
        buffer.append("\nuser=");
        buffer.append(USERNAME);
        buffer.append("\nport=");
        buffer.append(PORT);
        buffer.append("\npass=");
        buffer.append(PASSWORD);
        buffer.append("\ngroupID=");
        buffer.append(groupId);
        buffer.append("\niid=");
        buffer.append(imageId);
        buffer.append("]");
        buffer.append(" windowless=true ");
        IJ.runPlugIn("loci.plugins.LociImporter", buffer.toString());
    }

    /**
     * Connects to OMERO and returns a agteway instance allowing to interact
     * with the server
     *
     * @return See above
     * @throws Exception
     */
    private Gateway connectToOMERO() throws Exception {
        LoginCredentials credentials = new LoginCredentials();
        credentials.getServer().setHostname(HOST);
        credentials.getServer().setPort(PORT);
        credentials.getUser().setUsername(USERNAME.trim());
        credentials.getUser().setPassword(PASSWORD.trim());
        SimpleLogger simpleLogger = new SimpleLogger();
        Gateway gateway = new Gateway(simpleLogger);
        gateway.connect(credentials);
        return gateway;
    }

    /**
     * Returns the images contained in the specified dataset.
     *
     * @param gateway The gateway
     * @return See above
     * @throws Exception
     */
    private Collection<ImageData> getImages(Gateway gateway)
            throws Exception
    {
        BrowseFacility browser = gateway.getFacility(BrowseFacility.class);
        ExperimenterData user = gateway.getLoggedInUser();
        SecurityContext ctx = new SecurityContext(user.getGroupId());
        List<Long> ids = Arrays.asList(datasetId);
        return browser.getImagesForDatasets(ctx, ids);
    }

    /**
     * Uploads the image to OMERO.
     * 
     * @param gateway The gateway
     * @param path The path to the image to upload
     * @return
     * @throws Exception
     */
    private boolean uploadImage(Gateway gateway, String path)
            throws Exception
    {
        ExperimenterData user = gateway.getLoggedInUser();

        String sessionKey = gateway.getSessionId(user);

        ImportConfig config = new ImportConfig();
        config.debug.set(Boolean.FALSE);;
        config.hostname.set(HOST);
        config.sessionKey.set(sessionKey);
        String target_value = "omero.model.Dataset:"+datasetId;
        config.target.set(target_value);

        loci.common.DebugTools.enableLogging("DEBUG");

        OMEROMetadataStoreClient store = config.createStore();
        OMEROWrapper reader = new OMEROWrapper(config);

        ImportLibrary library = new ImportLibrary(store, reader);
        ErrorHandler errorHandler = new ErrorHandler(config);

        library.addObserver(new LoggingImportMonitor());
        String[] paths = new String[1];
        paths[0] = path;
        ImportCandidates candidates = new ImportCandidates(reader, paths, errorHandler);
        reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL));
        return library.importCandidates(config, candidates);
    }

    public OmeroBatchAnalysis() {
        Gateway gateway = null;
        try {
            gateway = connectToOMERO();
            Collection<ImageData> images = getImages(gateway);
            Iterator<ImageData> image = images.iterator();
            while (image.hasNext()) {
                ImageData data = image.next();
                openImagePlus(data.getId());
                IJ.run("Enhance Contrast...", "saturated=0.3");
                IJ.run("Subtract Background...", "rolling=50 stack");
                ImagePlus imp = IJ.getImage();
                String path = data.getName()+ ".ome.tiff";
                String options = "save=" + path + " export compression=Uncompressed";
                IJ.run(imp, "Bio-Formats Exporter", options);
                imp.changes = Boolean.FALSE;
                imp.close();
                uploadImage(gateway, path);
            }
        } catch (Exception e) {
            IJ.showMessage("An error occurred while loading the image.");
        } finally {
            if (gateway != null) gateway.disconnect();
        }
    }
    public static void main(String[] args) {
        new OmeroBatchAnalysis();

    }
