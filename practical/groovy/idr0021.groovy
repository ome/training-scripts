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
 * This Groovy script uses ImageJ to analyse particles.
* The generated ROIs are then saved back to OMERO.
 * We create a summary CSV and a summary table of the measurement and attach
 * them to the dataset.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but
 * this should be added
 * if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

#@ String(label="Username") USERNAME
#@ String(label="Password", style='password') PASSWORD
#@ String(label="Host", value='workshop.openmicroscopy.org') HOST
#@ Integer(label="Port", value=4064) PORT
#@ Integer(label="Project ID", value=2331) project_id

import java.util.ArrayList
import java.lang.Math
import java.lang.StringBuffer
import java.nio.ByteBuffer
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
    "List all datasets contained in a Project"

    browse = gateway.getFacility(BrowseFacility)
    ids = new ArrayList(1)
    ids.add(new Long(project_id))
    projects = browse.getProjects(ctx, ids)
    return projects[0].getDatasets()
}


def get_images(gateway, ctx, dataset_id) {
    "List all images contained in a Dataset"

    browse = gateway.getFacility(BrowseFacility)

    ids = new ArrayList(1)
    ids.add(new Long(dataset_id))
    return browse.getImagesForDatasets(ctx, ids)
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
    wavelength = 0
    while (j.hasNext()) {
        annotation = j.next()
        if (annotation.getNameSpace().equals(FileAnnotationData.BULK_ANNOTATIONS_NS)) {
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
                            wavelength = values[0]
                            return
                        }
                    }
                }
            }
        }
    }
    return wavelength
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


def save_row(rt, table_rows, channel_index, dataset_name, image) {
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
        rt.setValue("Channel Index", i, channel_index)
    }
    headings = rt.getHeadings()
    
    for (j = 0; j < rt.size(); j++) {
        row = new ArrayList()
        for (i = 0; i < headings.length; i++) {
            heading = rt.getColumnHeading(i)
            if (heading.equals("Slice") || heading.equals("Dataset") || heading.equals("Label")) {
                row.add(rt.getStringValue(i, j))
            } else {
                row.add(new Double(rt.getValue(i, j)))
            }
        }
        row.add(image)
        table_rows.add(row)
    }
    
    return headings
}


def create_table_columns(headings) {
    "Create the table headings from the ImageJ results table"
    size = headings.size()
    table_columns = new TableDataColumn[size+1]
    //populate the headings
    for (h = 0; h < size; h++) {
        heading = headings[h]
        // OMERO.tables queries don't handle whitespace well
        heading = heading.replace(" ", "_")
        if (heading.equals("Slice") || heading.equals("Dataset") || heading.equals("Label")) {
            table_columns[h] = new TableDataColumn(heading, h, String)
        } else {
            table_columns[h] = new TableDataColumn(heading, h, Double)
        }
    }
    table_columns[size] = new TableDataColumn("Image", size, ImageData)
    return table_columns
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

def upload_csv_to_omero(ctx, file, project_id) {
    "Upload the CSV file and attach it to the specified project"
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

    data_object = new ProjectData(new ProjectI(project_id, false)) 
    svc.attachAnnotation(ctx, new FileAnnotationData(fa), data_object)
}

def save_summary_as_omero_table(ctx, rows, columns, project_id) {
    "Create an OMERO table with the summary result and attach it to the specified project"
    data = new Object[columns.length][rows.size()]
    for (r = 0; r < rows.size(); r++) {
        row = rows.get(r)
        for (i = 0; i < row.size(); i++) {
            //Due to a limitation of OMERO.parade multiply value by 100
            v = row.get(i)
            if (v instanceof Double) {
                v = v * 100
            }
            data[i][r] = v
        }
    }
    // Create the table
    table_data = new TableData(columns, data)
    table_facility = gateway.getFacility(TablesFacility)
    data_object = new ProjectData(new ProjectI(project_id, false))
    result = table_facility.addTable(ctx, data_object, "Summary_from_Fiji", table_data)
    oid = result.getOriginalFileId()
    // Retrieve the annotation and set the namespace (due to a limitation of JavaGateway)
    annotations = table_facility.getAvailableTables(ctx, data_object)
    it = annotations.iterator()
    while (it.hasNext()) {
        ann = it.next()
        if (ann.getFileID() == oid) {
            ann.setNameSpace(FileAnnotationData.BULK_ANNOTATIONS_NS)
            gateway.getUpdateService(ctx).saveAndReturnObject(ann.asIObject())
            break
        }
    }
}


def save_summary_as_csv(file, rows, columns) {
    "Save the summary locally as a CSV"
    stream = null
    sb = new StringBuilder()
    try {
        stream = new PrintWriter(file)
        l = columns.length
        for (i = 0; i < l; i++) {
            sb.append(columns[i].getName())
            if (i != (l-1)) {
                sb.append(", ")
            }
        }
        sb.append("\n")
        rows.each() { row ->
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

// Connect
gateway = connect_to_omero()
user = gateway.getLoggedInUser()
group_id = user.getGroupId()
ctx = new SecurityContext(group_id)
exp = gateway.getLoggedInUser()
exp_id = exp.getId()


table_rows = new ArrayList()
table_columns = null

// get all the dataset_ids in an project
datasets = get_datasets(gateway, ctx, project_id)

//Close windows before starting
IJ.run("Close All")
datasets.each() { d ->
    dataset_name = d.getName()
    // for each dataset load the images
    // get all images_ids in the dataset
    images = get_images(gateway, ctx, d.getId())
    images.each() { image ->
        if (image.getName().endsWith(".tif")) {
            return
        }
        IJ.run("Close All")
        id = image.getId()
        channel_index = 1
        // Find the index of the channel matching the dataset name as a string
        // This section is very specific to the data we are looking at. Each channel has
        // wavelength information but not name directly associated to it.
        channel_wavelength = get_channel_wavelength(gateway, ctx, id, dataset_name)
        channels = get_channels_data(gateway, ctx, id)
        channels.each() { channel ->
            em = channel.getEmissionWavelength(null)
            if (em != null) {
                v = em.getValue().intValue().toString()
                if (v.equals(channel_wavelength)) {
                    channel_index = channel.getIndex()+1
                    println "Found index: "+ channel_index
                }
            }
        }
        open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, id)
        imp = IJ.getImage()
        // Some analysis which creates ROI's and Results Table
        IJ.run("8-bit")
        // white might be required depending on the version of Fiji
        IJ.run(imp, "Auto Threshold", "method=MaxEntropy stack")
        IJ.run(imp, "Analyze Particles...", "size=10-Infinity pixel display clear add stack summarize")
        IJ.run("Set Measurements...", "area mean standard modal min centroid center perimeter bounding feret's summarize stack display redirect=None decimal=3")

        rt = ResultsTable.getResultsTable()
        // Save the ROIs
        if (save_data) {
            roivec = save_rois_to_omero(ctx, id, imp)   
        }
        println "creating summary results for image ID " + id
        headings = save_row(rt, table_rows, channel_index, dataset_name, image)
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
}


//Create the result file
tmp_dir = Files.createTempDirectory("Fiji_csv")
path = tmp_dir.resolve("idr0021_merged_results.csv")
file_path = Files.createFile(path)
file = new File(file_path.toString())
// create CSV file
save_summary_as_csv(file, table_rows, table_columns)


if (save_data) {
    upload_csv_to_omero(ctx, file, project_id)
    save_summary_as_omero_table(ctx, table_rows, table_columns, project_id)
    // delete the local copy of the temporary file and directory
    dir = new File(tmp_dir.toString())
    entries = dir.listFiles()
    for (i = 0; i < entries.length; i++) {
        entries[i].delete()
    }
    dir.delete()
}

// Close the connection
gateway.disconnect()

println "processing done"
