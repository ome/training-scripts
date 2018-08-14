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
import java.io.FilenameFilter
import java.io.File
import java.io.PrintWriter

import java.nio.file.Files

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.ROIFacility
import omero.log.SimpleLogger
import omero.gateway.facility.TablesFacility
import omero.gateway.model.DatasetData
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
HOST = "outreach.openmicroscopy.org"
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

def get_image_ids(gateway, ctx, dataset_id) {
    "List all image's ids contained in a Dataset"

    browse = gateway.getFacility(BrowseFacility)

    ids = new ArrayList(1)
    ids.add(new Long(dataset_id))
    images = browse.getImagesForDatasets(ctx, ids)

    j = images.iterator()
    image_ids = new ArrayList()
    while (j.hasNext()) {
        image_ids.add(j.next().getId())
    }
    return image_ids
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


def save_as_csv(rt, tmp_dir, image_id) {
    "Create a summary table of the measurements and save as CSV"
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
    // Create a tmp file and save the result
    path = tmp_dir.resolve("result_for_" + image_id + ".csv")
    file_path = Files.createFile(path)
    rt.updateResults()
    rt.save(file_path.toString())
}


def save_summary_as_omero_table(ctx, file, dataset_id, delimiter) {
    "Convert the CSV file into an OMERO table and attach it to the specified dataset"
    data = null
    stream = null
    rows = new ArrayList()
    columns_list = new ArrayList()
    try {
        stream = new BufferedReader(new FileReader(file))
        line = stream.readLine()
        if (line == null) {
            return
        }
        headers = line.split(delimiter)
        index = 0
        string_indexes = new ArrayList()
        headers.each() { c ->
            if (c.equals("Slice") || c.equals("Label")) {
                columns_list.add(new TableDataColumn(c, index, String))
                string_indexes.add(index)
            } else {
                columns_list.add(new TableDataColumn(c, index, Double))
            }
            index++
        }
        // Read the rest of the file
        while ((line = stream.readLine()) != null) {
            values = line.trim().split(delimiter)
            i = 0
            row = new ArrayList()
            values.each() { c ->
                if (string_indexes.contains(i)) {
                    row.add(new String(c))
                } else {
                    row.add(new Double(c))
                }
                i++
            }
            rows.add(row)
        }
    } finally {
        if (stream != null) {
            stream.close()
        }
    }
    // create columns
    columns = new TableDataColumn[columns_list.size()]
    i = 0
    columns_list.each() { c ->
        columns[i] = c
        i++
    }
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

// Prototype analysis example
gateway = connect_to_omero()
exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
ctx = new SecurityContext(group_id)
exp_id = exp.getId()

// Save temporarily the summary for each image
tmp_dir = Files.createTempDirectory("Fiji_csv")

// get all images_ids in an omero dataset
ids = get_image_ids(gateway, ctx, dataset_id)

ids.each() { id ->
    // Open the image
    open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(id))
    imp = IJ.getImage()
    // Analyse the images. This section could be replaced by any other macro
    IJ.run("8-bit")
    IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
    IJ.run(imp, "Analyze Particles...", "size=10-Infinity pixel display clear add stack summarize")
    IJ.run("Set Measurements...", "area mean standard modal min centroid center perimeter bounding feret's summarize stack display redirect=None decimal=3")

    rm = RoiManager.getInstance()
    rm.runCommand(imp, "Measure")
    rt = ResultsTable.getResultsTable()
    // Save the ROIs back to OMERO
    roivec = save_rois_to_omero(ctx, id, imp)
    println "saving locally results for image with ID " + id
    save_as_csv(rt, tmp_dir, id)
    // Close the various components
    IJ.selectWindow("Results")
    IJ.run("Close")
    IJ.selectWindow("ROI Manager")
    IJ.run("Close")
    imp.changes = false     // Prevent "Save Changes?" dialog
    imp.close()
}

// Aggregate the CVS files
delimiter = ","
dir = new File(tmp_dir.toString())

csv_files = dir.listFiles(new FilenameFilter() {
    public boolean accept(File dir, String filename) { return filename.endsWith(".csv") }
})

//Create the result file
path = tmp_dir.resolve("idr0021_merged_results.csv")
file_path = Files.createFile(path)
file = new File(file_path.toString())
data = null
streams = new ArrayList()
sb = new StringBuilder()
try {
    stream = new PrintWriter(file)
    streams.add(stream)
    //read the first file with the headers
    s = new BufferedReader(new FileReader(csv_files[0]))
    streams.add(s)
    while ((line = s.readLine()) != null) {
        sb.append(line)
        sb.append("\n")
    }
    //Do not read header
    for (i = 1; i < csv_files.length - 1; i++) {
        f = csv_files[i]
        s = new BufferedReader(new FileReader(f))
        streams.add(s)
        f.nextLine
        while ((line = s.readLine()) != null) {
            sb.append(line)
            sb.append("\n")
       }
    }
    stream.write(sb.toString())
} finally {
    streams.each() { stream ->
         stream.close()
    }
}

save_summary_as_omero_table(ctx, file, dataset_id, delimiter)

// delete the directory. First need to remove the files
entries = dir.listFiles()
for (i =0; i < entries.length; i++) {
    entries[i].delete()
}

dir.delete()
// Close the connection
gateway.disconnect()
println "processing done"
