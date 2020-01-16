/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2020 University of Dundee. All rights reserved.
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
 * This Groovy script shows how to analyse an OMERO dataset
 * i.e. a collection of OMERO images using ilastik.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

import java.util.ArrayList


// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.ROIFacility
import omero.log.SimpleLogger

import org.openmicroscopy.shoola.util.roi.io.ROIReader

import loci.formats.FormatTools
import loci.formats.ImageTools
import loci.common.DataTools

import ij.IJ
import ij.ImagePlus
import ij.ImageStack
import ij.process.ByteProcessor
import ij.process.ShortProcessor
import ij.plugin.frame.RoiManager
import ij.measure.ResultsTable


// Setup
// =====

// OMERO Server details
HOST = "workshop.openmicroscopy.org"
PORT = 4064

//  parameters to edit
dataset_id = 2331
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

def get_images(gateway, ctx, dataset_id) {
    "List all images contained in a Dataset"

    browse = gateway.getFacility(BrowseFacility)
    ids = new ArrayList(1)
    ids.add(new Long(dataset_id))
    return browse.getImagesForDatasets(ctx, ids)
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


def file_exists(name, list) {
    for (i = 0; i < list.length; i++) {
        if (name.equals(list[i].toString())) {
            return true
        }
     }
    return false
}

def save_rois_to_omero(ctx, image_id, imp) {
    " Save ROI's back to OMERO"
    reader = new ROIReader()
    roi_list = reader.readImageJROIFromSources(image_id, imp)
    roi_facility = gateway.getFacility(ROIFacility)
    result = roi_facility.saveROIs(ctx, image_id, exp_id, roi_list)

    roivec = new ArrayList()
    j = result.iterator()
    while (j.hasNext()) {
        roidata = j.next()
        roi_id = roidata.getId()

        i = roidata.getIterator()
        while (i.hasNext()) {
            roi = i.next()
            shape = roi[0]
            t = shape.getZ()
            z = shape.getT()
            c = shape.getC()
            shape_id = shape.getId()
            roivec.add([roi_id, shape_id, z, c, t])
        }
    }
    return roivec
}

//Beginning of script
//Choose a directory where to save the .h5 file
data_dir = IJ.getDirectory("Choose a directory where to save h5 files")
if (data_dir == null) {
    println "cancel"
    return
}

pixelClassificationProject = IJ.getFilePath("Choose a ilastik Project File")
if (pixelClassificationProject == null) {
	println "cancel"
	return
}


//Set the path to the executable
IJ.run("Configure ilastik executable location")


// Prototype analysis example
gateway = connect_to_omero()
exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
ctx = new SecurityContext(group_id)
exp_id = exp.getId()

// get all images in an omero dataset
images = get_images(gateway, ctx, dataset_id)

inputDataset = "[/data]"
dir = new File(data_dir.toString())
files = dir.listFiles()


outputType = "Probabilities" //Default
axisOrder = "tzyxc" //axis of the model
inputDataset = "[/data]"

images.each() { image ->
    id = image.getId()
    name = image.getName() + ".h5"
    output_file = data_dir + name
    // Open the image if the .h5 file does not exist in the directory
    if (!file_exists(output_file, files)) {
        println "opening image from OMERO"
        open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(id))
        //compressionLevel to be added if required
        args = "select=" + output_file
        IJ.run("Export HDF5", args);
        imp = IJ.getImage()
        imp.close()
    }
    //Now open the h5 file  
    args = "select=" + output_file + " datasetname=" + inputDataset +  " axisorder=" + axisOrder            
    println "opening h5 file"
    IJ.run("Import HDF5", args)
    // run pixel classification
    input_image = output_file + "/data";
    args = "projectfilename=" + pixelClassificationProject + " saveonly=false inputimage=" + input_image + " chosenoutputtype=" + outputType;
    println "running Pixel Classification Prediction"
    IJ.run("Run Pixel Classification Prediction", args);

    //Get the outputType.h5 file e.g. Probabilities.h5
    imp = IJ.getImage()
    // Analyse the images. This section could be replaced by any other macro
    IJ.run("8-bit")
    //white might be required depending on the version of Fiji
    IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
    IJ.run(imp, "Analyze Particles...", "size=50-Infinity pixel display clear add stack summarize")
    
    // Save the ROIs back to OMERO
    roivec = save_rois_to_omero(ctx, id, imp)

    IJ.run("Close")
    // Close the various components
    IJ.selectWindow("Results")
    IJ.run("Close")
    IJ.selectWindow("ROI Manager")
    IJ.run("Close")
    imp.changes = false     // Prevent "Save Changes?" dialog
    imp.close()
    IJ.run("Close All")
}
// Close the connection
gateway.disconnect()
println "processing done"
