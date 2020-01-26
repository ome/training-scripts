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
 * This Groovy script uses ImageJ to analyse an Image.
 * The number of generated ROIs is then added
 * to a MapAnnotation (Key/Value pairs). The MapAnnotation is then attached
 * to the Image.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

#@ String(label="Username") USERNAME
#@ String(label="Password", style='password') PASSWORD
#@ String(label="Host", value='workshop.openmicroscopy.org') HOST
#@ Integer(label="Port", value=4064) PORT
#@ Integer(label="Image ID", value=2331) image_id

import java.util.ArrayList
import java.util.List
import java.io.File
import java.lang.reflect.Array

// OMERO Dependencies
import omero.model.NamedValue
import omero.model.ImageI
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.model.ImageData
import omero.gateway.model.MapAnnotationData
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
import ij.plugin.frame.RoiManager
import ij.measure.ResultsTable

group_id = "-1"


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

    StringBuilder options = new StringBuilder()
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


// Connect to OMERO
println "connecting..."
gateway = connect_to_omero()

println "opening Image..."
// Open the Image using Bio-Formats
open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(image_id))

imp = IJ.getImage()
// Analyse the images. This section could be replaced by any other macro
IJ.run("8-bit");
//white might be required depending on the version of Fiji
IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
IJ.run(imp, "Analyze Particles...", "size=10-Infinity pixel display clear add stack");
IJ.run("Set Measurements...", "area mean standard modal min centroid center \
        perimeter bounding summarize feret's median\
        stack display redirect=None decimal=3")

rm = RoiManager.getInstance()
rm.runCommand(imp, "Measure")
roi_count = rm.getCount()
println roi_count
// Create the map annotation
println "Creating MapAnnotation..."
List<NamedValue> result = new ArrayList<NamedValue>()
result.add(new NamedValue("Analysis", "Fiji"))
result.add(new NamedValue("ROIs", ""+roi_count))
result.add(new NamedValue("Antibody", "Rabbit"))
MapAnnotationData data = new MapAnnotationData()
data.setContent(result)
data.setDescription("Training Example")
//Use the following namespace if you want the annotation to be editable
//in the webclient and insight
data.setNameSpace(MapAnnotationData.NS_CLIENT_CREATED)

exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
ctx = new SecurityContext(group_id)
DataManagerFacility fac = gateway.getFacility(DataManagerFacility.class)
fac.attachAnnotation(ctx, data, new ImageData(new ImageI(image_id, false)))


// Close the connection
gateway.disconnect()
println "Done"
