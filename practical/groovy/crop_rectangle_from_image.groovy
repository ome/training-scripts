/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2019 University of Dundee. All rights reserved.
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
 * This Groovy script uses ImageJ to crop an image.
 * The cropped image is saved locally as OME-TIFF using Bio-Formats Exporter.
 * The OME-TIFF is then imported to OMERO.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

import java.io.File
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
image_id = 1001
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
    options.append("] ")
    options.append("windowless=true view=Hyperstack ")
    IJ.runPlugIn("loci.plugins.LociImporter", options.toString())
}

def find_dataset(gateway, dataset_id) {
    "Load the Dataset"
    browse = gateway.getFacility(BrowseFacility)
    user = gateway.getLoggedInUser()
    ctx = new SecurityContext(user.getGroupId())
    return browse.findIObject(ctx, "omero.model.Dataset", dataset_id)
}

def upload_image(paths, gateway, id) {
    "Upload an image to OMERO"

    user = gateway.getLoggedInUser()
    sessionKey = gateway.getSessionId(user)

    config = new ImportConfig()
    config.debug.set('false')
    config.hostname.set(HOST)
    config.sessionKey.set(sessionKey)
    dataset = find_dataset(gateway, id)

    store = config.createStore()
    reader = new OMEROWrapper(config)

    library = new ImportLibrary(store, reader)
    error_handler = new ErrorHandler(config)

    library.addObserver(new LoggingImportMonitor())
    candidates = new ImportCandidates(reader, paths, error_handler)
    containers = candidates.getContainers()
    containers.each() { c ->
        c.setTarget(dataset)
    }
    reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL))
    return library.importCandidates(config, candidates)
}


// Connect to OMERO
println "connecting..."
gateway = connect_to_omero()

println "opening Image..."
// Open the Image using Bio-Formats
open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(image_id))

// Crop the image
println "cropping..."
IJ.makeRectangle(0, 0, 200, 200)
IJ.run("Crop")

// Save modified image as OME-TIFF using Bio-Formats Exporter
imp = IJ.getImage()
name = imp.getTitle().replaceAll("\\s","")
file = File.createTempFile("name", ".ome.tiff")
path_to_file = file.getAbsolutePath()
println  path_to_file
options = "save=" + path_to_file + " export compression=Uncompressed"
IJ.run(imp, "Bio-Formats Exporter", options)
imp.changes = false
imp.close()

// Create a Dataset
d = new DatasetData()
d.setName("Cropped Image")
dm = gateway.getFacility(DataManagerFacility)
user = gateway.getLoggedInUser()
ctx = new SecurityContext(user.getGroupId())
d = dm.createDataset(ctx, d, null)

// Import the generated OME-TIFF to OMERO
println "importing..."
str2d = new String[1]
str2d[0] = path_to_file
success = upload_image(str2d, gateway, d.getId())
// delete the local OME-TIFF image
(new File(path_to_file)).delete()
println "imported"

// Close the connection
gateway.disconnect()
