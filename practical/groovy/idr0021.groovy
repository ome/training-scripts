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
 * This Groovy script uses ImageJ to analyse particles, and saved the results
 * locally in a CSV file. The file is then uploaded and attached to the specified
 * project as a file annotation.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but
 * this should be added
 * if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero/developers/Java.html
 */

import java.util.ArrayList
import java.lang.Math
import java.nio.ByteBuffer
import java.io.BufferedReader
import java.io.FileReader
import java.io.FilenameFilter
import java.io.File
import java.io.PrintWriter

import java.nio.file.Files

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.facility.MetadataFacility
import omero.gateway.facility.ROIFacility
import omero.gateway.facility.TablesFacility
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.MapAnnotationData
import omero.gateway.model.DatasetData
import omero.gateway.model.ImageData
import omero.gateway.model.ProjectData
import omero.gateway.model.TableData
import omero.gateway.model.TableDataColumn


import omero.model.DatasetI
import omero.model.ImageI
import omero.model.ProjectI
import omero.log.SimpleLogger
import omero.model.ChecksumAlgorithmI
import omero.model.FileAnnotationI
import omero.model.OriginalFileI
import omero.model.enums.ChecksumAlgorithmSHA1160

import static omero.rtypes.rlong
import static omero.rtypes.rstring


import org.openmicroscopy.shoola.util.roi.io.ROIReader

import ij.IJ
import ij.plugin.frame.RoiManager
import ij.measure.ResultsTable


// Setup
// =====

// OMERO Server details
HOST = "outreach.openmicroscopy.org"
PORT = 4064
project_id = 1101
USERNAME = "username"
PASSWORD = "password"

NAMESPACE = "openmicroscopy.org/omero/bulk_annotations"
MAP_KEY = "Channels"

save_data = true

if (HOST.startsWith("idr")) {
    save_data = false
}

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

def get_datasets(gateway, ctx, project_id) {
    "List all dataset's ids contained in a Project"

    browse = gateway.getFacility(BrowseFacility)
    ids = new ArrayList(1)
    ids.add(new Long(project_id))
    projects = browse.getProjects(ctx, ids)
    return projects[0].getDatasets()
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

def get_channels_data(gateway, ctx, image_id) {
    "List the channels data associated to the specified image"
    svc = gateway.getFacility(MetadataFacility)
    return svc.getChannelData(ctx, image_id)
}

def get_channel_wavelength(gateway, ctx, image_id, dataset_name) {
    "Load the map annotations and find the channel's wavelength matching the dataset name"
    svc = gateway.getFacility(MetadataFacility)
    types = new ArrayList(1)
    types.add(MapAnnotationData.class)
    data = new ImageData(new ImageI(image_id, false))
    annotations = svc.getAnnotations(ctx, data, types, null)
    if (annotations.size() == 0) {
        return "not found"
    }
    // Iterate through annotation
    j = annotations.iterator()
    while (j.hasNext()) {
        annotation = j.next()
        if (annotation.getNameSpace().equals(NAMESPACE)) {
            named_values = annotation.getContent()
            i = named_values.iterator()
            while (i.hasNext()) {
                nv = i.next()
                if (nv.name.equals(MAP_KEY)) {
                    channels = nv.value.split("; ")
                    channels.each() { ch_name ->
                        values = ch_name.split(":")
                        name = values[1]
                        if (dataset_name.contains(name)) {
                            return values[0]
                        }
                    }
                }
            }
        }
    }

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

def save_as_csv(rt, tmp_dir, image_id, channel_index, dataset_name) {
    "Create a summary table from the original table"
    // Remove the rows not corresponding to the specified channel
    to_delete = new ArrayList()
    
    ref = "c:" + channel_index
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
        rt.setValue("Dataset", i, dataset_name)
        rt.setValue("Bounding_Box", i, max_bounding_box)
    }
    // Create a tmp file and save the result
    path = tmp_dir.resolve("result_for_" + image_id + ".csv")
    file_path = Files.createFile(path)
    rt.updateResults()
    rt.saveAs(file_path.toString())
}

def save_rois_to_omero(ctx, image_id, imp) {
    // Save ROI's back to OMERO
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

def upload_csv_to_omero(ctx, file, type, object_id) {
    "Upload the CSV file and attach it to the specified object"
    svc = gateway.getFacility(DataManagerFacility)

    file_size = file.length()
    original_file = new OriginalFileI()
    original_file.setName(rstring(file.getName()))
    original_file.setPath(rstring(file.getAbsolutePath()))
    original_file.setSize(rlong(file_size))
    checksum_algorithm = new ChecksumAlgorithmI()
    checksum_algorithm.setValue(rstring(ChecksumAlgorithmSHA1160.value))
    original_file.setHasher(checksum_algorithm)
    original_file.setMimetype(rstring("text/csv"))
    original_file = svc.saveAndReturnObject(ctx, original_file)
    store = gateway.getRawFileService(ctx)

    // Open file and read stream
    INC = 262144
    pos = 0
    buf = new byte[INC]
    ByteBuffer bbuf = null
    stream = null
    try {
        store.setFileId(original_file.getId().getValue())
        stream = new FileInputStream(file)
        while ((rlen = stream.read(buf)) > 0) {
            store.write(buf, pos, rlen)
            pos += rlen
            bbuf = ByteBuffer.wrap(buf)
            bbuf.limit(rlen)
        }
        original_file = store.save()
    } finally {
        if (stream != null) {
            stream.close()
        }
        store.close()
    }
    // create the file annotation
    namespace = "training.demo"
    fa = new FileAnnotationI()
    fa.setFile(original_file)
    fa.setNs(rstring(namespace))

    data_object = null
    if (type.equals("Project")) {
        data_object = new ProjectData(new ProjectI(object_id, false))
    } else {
        data_object = new DatasetData(new DatasetI(object_id, false))
    }  
    svc.attachAnnotation(ctx, new FileAnnotationData(fa), data_object)
    println "Saved"
}


def save_summary_as_omero_table(ctx, file, type, object_id, delimiter) {
    "Convert the CSV file into an OMERO table and attach it to the specified object"
    data = null
    stream = null
    try {
        stream = new BufferedReader(new FileReader(file))
        headers = stream.readLine()
        if (headers == null || headers.length == 0) {
            return
        }
        headers.split(delimiter)
        columns = [TableDataColumn] * headers.size()
        index = 0
        string_indexes = new ArrayList()
        headers.each() { c ->
            if (c.equals("Slice") || c.equals("Dataset")) {
                columns[i] = new TableDataColumn(c, i, String)
                string_indexes.append(i)
            } else {
                columns[i] = new TableDataColumn(c, i, Double)
            }
            index++
        }
        // Read the rest of the file
        while ((line = stream.readLine()) != null) {
            values = line.trim().split(delimiter)
            i = 0
            row = new ArrayList()
            values.each() { c ->
                if (string_indexes(i)) {
                    row.add(new String(c))
                } else {
                    row.append(new Double(c))
                }
                i++
            }
            data = new Object[columns.size()][rows.size()]
            for (r = 0; r < rows.size(); r++) {
                row = rows.get(r)
                for (i = 0; i < row.size(); i++) {
                    data[i][e] = row.get(i)
                }
            }
        }
    } finally {
        if (stream != null) {
            stream.close()
        }
    }
    // Create the table
    table_data = new TableData(columns, data)
    table_facility = gateway.getFacility(TablesFacility)
    data_object = null
    if (type.equals("Project")) {
        data_object = new ProjectData(new ProjectI(object_id, false))
    } else {
        data_object = new DatasetData(new DatasetI(object_id, false))
    }
    table_facility.addTable(ctx, data_object, "Summary_from_Fiji", table_data)

}

// Connect
gateway = connect_to_omero()
user = gateway.getLoggedInUser()
group_id = user.getGroupId()
ctx = new SecurityContext(group_id)
exp = gateway.getLoggedInUser()
exp_id = exp.getId()


tmp_dir = Files.createTempDirectory("Fiji_csv")
// get all the dataset_ids in an project
datasets = get_datasets(gateway, ctx, project_id)
j = datasets.iterator()
datasets.each() { d ->
    name = d.getName()
    // for each dataset load the images
    // get all images_ids in the dataset
    image_ids = get_image_ids(gateway, ctx, d.getId())
    image_ids.each() { id ->
        channel_index = 1
        // Find the index of the channel matching the dataset name as a string
        // This section is very specific to the data we are looking at. Each channel has
        // wavelength information but not name directly associated to it.
        channel_wavelength = get_channel_wavelength(gateway, ctx, id, name)
        channels = get_channels_data(gateway, ctx, id)
        channels.each() { channel ->
            em = channel.getEmissionWavelength(null)
            if (em != null) {
                v = em.getValue().intValue().toString()
                if (v.equals(channel_wavelength)) {
                    channel_index = channel.getIndex()+1
                    print "Found index: "+str(channel_index)
                }
            }
        }
        open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, id)
        imp = IJ.getImage()
        // Some analysis which creates ROI's and Results Table
        IJ.run("8-bit")
        IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
        IJ.run(imp, "Analyze Particles...", "size=10-Infinity pixel display clear add stack summarize")
        IJ.run("Set Measurements...", "area mean standard modal min centroid center perimeter bounding feret's summarize stack display redirect=None decimal=3")

        rm = RoiManager.getInstance()
        rm.runCommand(imp, "Measure")
        rt = ResultsTable.getResultsTable()
        // Save the ROIs
        if (save_data) {
            roivec = save_rois_to_omero(ctx, id, imp)	
        }
        println "saving locally results for image with ID " + id
        save_as_csv(rt, tmp_dir, id, channel_index, name)
        // Close the various components
        IJ.selectWindow("Results")
        IJ.run("Close")
        IJ.selectWindow("ROI Manager")
        IJ.run("Close")
        imp.changes = false // Prevent "Save Changes?" dialog
        imp.close()
    }
}

// Close the connection
gateway.disconnect()

// Aggregate the CVS files
delimiter = ","
dir = new File(tmp_dir.toString())

csv_files = dir.listFiles(new FilenameFilter() {
    public boolean accept(File dir, String filename) { return filename.endsWith(".csv") }
})

//Create the result file
path = tmp_dir.resolve("idr0021_merged_results.csv")
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
    }
    //Do not read header
    for (i = 1; i < csv_files.length - 1; i++) {
        f = csv_files[i]
        s = new BufferedReader(new FileReader(f))
        streams.add(s)
        f.nextLine
        while ((line = s.readLine()) != null) {
            sb.append(line)
       }
    }
    stream.write(sb.toString())
} finally {
    streams.each() { stream ->
         stream.close()
    }
}

if (save_data) {
    upload_csv_to_omero(ctx, file, "Project", project_id)
    save_summary_as_omero_table(ctx, file, "Project", project_id, delimiter)
}
println "processing done"
