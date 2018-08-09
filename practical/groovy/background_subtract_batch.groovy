/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2018 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 */

/*
 * This Groovy script uses ImageJ to Subtract Background.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

import java.io.File
import java.util.ArrayList
import java.lang.reflect.Array

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.model.DatasetData
import omero.log.SimpleLogger

import ome.formats.importer.ImportConfig
import ome.formats.importer.OMEROWrapper
import ome.formats.importer.ImportLibrary
import ome.formats.importer.ImportCandidates
import ome.formats.importer.cli.ErrorHandler
import ome.formats.importer.cli.LoggingImportMonitor

import loci.formats.in.DefaultMetadataOptions
import loci.formats.in.MetadataLevel

import ij.IJ

// Setup
// =====

// OMERO Server details
HOST = "outreach.openmicroscopy.org"
PORT = 4064
group_id = "-1"
// parameters to edit
dataset_id = 1001
USERNAME = "username"
PASSWORD = "password"


def connect_to_omero() {
    "Connect to OMERO"

    credentials = new LoginCredentials()
    credentials.getServer().setHostname(HOST)
    credentials.getServer().setPort(PORT)
    credentials.getUser().setUsername(USERNAME.trim())
    credentials.getUser().setPassword(PASSWORD.trim())
    simpleLogger = new SimpleLogger()
    gateway = new Gateway(simpleLogger)
    gateway.connect(credentials)
    return gateway

}

def open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, image_id) {
    "Open the image using the Bio-Formats Importer"

    StringBuffer options = new StringBuffer()
    options.append("location=[OMERO] open=[omero:server=")
    options.append(HOST)
    options.append("\nuser=")
    options.append(USERNAME.trim())
    options.append("\nport=")
    options.append(PORT)
    options.append("\npass=")
    options.append(PASSWORD.trim())
    options.append("\ngroupID=")
    options.append(group_id)
    options.append("\niid=")
    options.append(image_id)
    options.append("]")
    options.append("windowless=true")
    IJ.runPlugIn("loci.plugins.LociImporter", options.toString())

}

def get_image_ids(gateway, dataset_id) {
    "List all image's ids contained in a Dataset"

    browse = gateway.getFacility(BrowseFacility)
    user = gateway.getLoggedInUser()
    ctx = new SecurityContext(user.getGroupId())
    ids = new ArrayList(1)
    val = new Long(dataset_id)
    ids.add(val)
    images = browse.getImagesForDatasets(ctx, ids)
    j = images.iterator()
    image_ids = new ArrayList()
    while (j.hasNext()) {
        image = j.next()
        image_ids.add(String.valueOf(image.getId()))
    }
    return image_ids
}

def upload_image(path, gateway, id) {
    "Upload an image to omero"

    user = gateway.getLoggedInUser()
    sessionKey = gateway.getSessionId(user)

    config = new ImportConfig()
    config.debug.set('false')
    config.hostname.set(HOST)
    config.sessionKey.set(sessionKey)
    config.savedDataset.set(id)

    store = config.createStore()
    reader = new OMEROWrapper(config)

    library = new ImportLibrary(store, reader)
    error_handler = new ErrorHandler(config)

    library.addObserver(new LoggingImportMonitor())
    candidates = new ImportCandidates(reader, path, error_handler)
    reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL))
    return library.importCandidates(config, candidates)

}

// Connect to OMERO
gateway = connect_to_omero()

// Retrieve the images contained in the specified dataset
image_ids = get_image_ids(gateway, dataset_id)

//Create a dataset to store the newly created images will be added
name = "script_editor_output_from_dataset_" + dataset_id
d = new DatasetData()
d.setName(name)
dm = gateway.getFacility(DataManagerFacility)
user = gateway.getLoggedInUser()
ctx = new SecurityContext(user.getGroupId())
d = dm.createDataset(ctx, d, null)

// Loop through each image
image_ids.each() { image_id ->
    println image_id
    open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, image_id)
    IJ.run("Enhance Contrast...", "saturated=0.3")
    IJ.run("Subtract Background...", "rolling=50 stack")

    // Save modified image as OME-TIFF using Bio-Formats
    imp = IJ.getImage()
    path_to_file = imp.getTitle() + ".ome.tiff"
    println  path_to_file
    options = "save=" + path_to_file + " export compression=Uncompressed"
    IJ.run(imp, "Bio-Formats Exporter", options)
    imp.changes = false
    imp.close()

    // Upload the generated OME-TIFF to OMERO
    println "uploading..."
    str2d = new String[1]
    str2d[0] = path_to_file
    success = upload_image(str2d, gateway, d.getId())
    // delete the local OME-TIFF image
    (new File(path_to_file)).delete()
    println "imported"
}

println "Done"
// Close the connection
gateway.disconnect()