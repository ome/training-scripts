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
 * This Groovy script shows how to analyse an OMERO dataset
 * i.e. a collection of OMERO images.
 * For that example, we use the analyse particles plugin.
 * The generated ROIs are then saved back to OMERO.
 * We create a summary CSV and a summary table of the measurement and attach
 * them to the dataset.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

import java.util.ArrayList
import java.lang.StringBuffer
import java.nio.ByteBuffer
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter

import java.nio.file.Files

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.facility.ROIFacility
import omero.gateway.facility.TablesFacility
import omero.log.SimpleLogger
import omero.model.ChecksumAlgorithmI
import omero.model.FileAnnotationI
import omero.model.OriginalFileI
import omero.model.enums.ChecksumAlgorithmSHA1160

import static omero.rtypes.rlong
import static omero.rtypes.rstring

import omero.gateway.model.DatasetData
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.ImageData
import omero.gateway.model.TableData
import omero.gateway.model.TableDataColumn
import omero.model.DatasetI

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


def save_row(rt, table_rows, image) {
    "Create a summary table of the measurements"
    // Remove the rows not corresponding to the specified channel
    to_delete = new ArrayList()
    
    // We only keep the first channel. Index starts at 1 in ImageJ
    ref = "c:" + 1
    max_bounding_box = 0.0f
    for (i = 0; i < rt.size(); i++) {
        label = rt.getStringValue("Label", i)
        if (label.contains(ref)) {
            w = rt.getStringValue("Width", i)
            h = rt.getStringValue("Height", i)
            area = Float.parseFloat(w) * Float.parseFloat(h)
            max_bounding_box = Math.max(area, max_bounding_box)
        }
    }
    // Rename the table so we can read the summary table
    IJ.renameResults("Results")
    rt = ResultsTable.getResultsTable()
    for (i = 0; i < rt.size(); i++) {
        value = rt.getStringValue("Slice", i)
        if (!value.startsWith(ref)) {
            to_delete.add(i)
        }
    }
    // Delete the rows we do not need
    for (i = 0; i < rt.size(); i++) {
        value = to_delete.get(i)
        v = value-i
        rt.deleteRow(v)
    }
    rt.updateResults()
    // Insert values in summary table
    for (i = 0; i < rt.size(); i++) {
        rt.setValue("Bounding_Box", i, max_bounding_box)
    }
    headings = rt.getHeadings()
    for (i = 0; i < headings.length; i++) {
        row = new ArrayList()
        for (j = 0; j < rt.size(); j++) {
            for (i = 0; i < headings.length; i++) {
                heading = rt.getColumnHeading(i)
                if (heading.equals("Slice") || heading.equals("Dataset")) {
                    row.add(rt.getStringValue(i, j))
                } else {
                    row.add(new Double(rt.getValue(i, j)))
                }
            }
        }
        row.add(image)
        table_rows.add(row)
    }
    return headings
}


def save_summary_as_omero_table(ctx, rows, columns, dataset_id) {
    "Create an OMERO table with the summary result and attach it to the specified dataset"
    data = new Object[columns.length][rows.size()]
    for (r = 0; r < rows.size(); r++) {
        row = rows.get(r)
        for (i = 0; i < row.size(); i++) {
            data[i][r] = row.get(i)
        }
    }
    // Create the table
    table_data = new TableData(columns, data)
    table_facility = gateway.getFacility(TablesFacility)
    data_object = new DatasetData(new DatasetI(dataset_id, false))
    table_facility.addTable(ctx, data_object, "Summary_from_Fiji", table_data)
}


def create_table_columns(headings) {
    "Create the table headings from the ImageJ results table"
    size = headings.size()
    table_columns = new TableDataColumn[size+1]
    //populate the headings
    for (h = 0; h < headings.size(); h++) {
        heading = headings[h]
        if (heading.equals("Slice") || heading.equals("Label")) {
            table_columns[h] = new TableDataColumn(heading, h, String)
        } else {
            table_columns[h] = new TableDataColumn(heading, h, Double)
        }
    }
    table_columns[size] = new TableDataColumn("Image", size, ImageData)
    return table_columns
}


def save_summary_as_csv(file, rows, columns) {
    "Save the summary locally as a CSV"
    stream = null
    sb = new StringBuilder()
    try {
        stream = new PrintWriter(file)
        l = table_columns.length
        for (i = 0; i < l; i++) {
            sb.append(table_columns[i].getName())
            if (i != (l-1)) {
                sb.append(", ")
            }
        }
        sb.append("\n")
        table_rows.each() { row ->
            size = row.size()
            for (i = 0; i < size; i++) {
                value = row.get(i)
                if (value instanceof ImageData) {
                    sb.append(value.getId())
                } else {
                    sb.append(value)
                }
                if (i != (size-1)) {
                    sb.append(", ")
                }
            }
            sb.append("\n")
        }
        stream.write(sb.toString())
    } finally {
        stream.close()
    }
}

def upload_csv_to_omero(ctx, file, dataset_id) {
    "Upload the CSV file and attach it to the specified dataset"
    svc = gateway.getFacility(DataManagerFacility)
    data = new DatasetData(new DatasetI(dataset_id, false))
    namespace = "training.demo"
    mimetype = "text/csv"
    svc.attachFile(ctx, file, mimetype, "", file.getName(), namespace, data)
}

// Prototype analysis example
gateway = connect_to_omero()
exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
ctx = new SecurityContext(group_id)
exp_id = exp.getId()


// get all images in an omero dataset
images = get_images(gateway, ctx, dataset_id)

table_rows = new ArrayList()
table_columns = null

count = 0
//Close all windows before starting
IJ.run("Close All")
images.each() { image ->
    // Open the image
    id = image.getId()
    open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(id))
    imp = IJ.getImage()
    // Analyse the images. This section could be replaced by any other macro
    IJ.run("8-bit")
    //white might be required depending on the version of Fiji
    IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
    IJ.run(imp, "Analyze Particles...", "size=10-Infinity pixel display clear add stack summarize")
    IJ.run("Set Measurements...", "area mean standard modal min centroid center perimeter bounding feret's summarize stack display redirect=None decimal=3")

    rm = RoiManager.getInstance()
    rm.runCommand(imp, "Measure")
    rt = ResultsTable.getResultsTable()
    // Save the ROIs back to OMERO
    roivec = save_rois_to_omero(ctx, id, imp)
    println "creating summary results for image ID " + id
    headings = save_row(rt, table_rows, image)
    if (table_columns == null) {
        table_columns = create_table_columns(headings)
    }
    
    // Close the various components
    IJ.selectWindow("Results")
    IJ.run("Close")
    IJ.selectWindow("ROI Manager")
    IJ.run("Close")
    imp.changes = false     // Prevent "Save Changes?" dialog
    imp.close()
    
}

//Create the result file
tmp_dir = Files.createTempDirectory("Fiji_csv")
path = tmp_dir.resolve("idr0021_merged_results.csv")
file_path = Files.createFile(path)
file = new File(file_path.toString())

// create a CSV file and upload it
save_summary_as_csv(file, table_rows, table_columns)
upload_csv_to_omero(ctx, file, dataset_id)

//delete the local copy of the temporary file and directory
dir = new File(tmp_dir.toString())
entries = dir.listFiles()
for (i = 0; i < entries.length; i++) {
    entries[i].delete()
}
dir.delete()

save_summary_as_omero_table(ctx, table_rows, table_columns, dataset_id)

// Close the connection
gateway.disconnect()
println "processing done"

