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
 * This Groovy script shows how to load a given plane from OMERO,
 * load OMERO rois and add them to the manager and run an ImageJ macro.
 * All planes can be retrieved or only the planes corresponding to a specific timepoint.
 * In this script, the analysis can be done on behalf of another user by a person
 * with more privileges e.g. analyst.
 * More details about restricted privileges can be found at
 * https://docs.openmicroscopy.org/latest/omero/sysadmins/restricted-admins.html
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but
 * this should be added
 * if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero/developers/Java.html
 */

#@ String(label="Username") USERNAME
#@ String(label="Password", style='password') PASSWORD
#@ String(label="Host", value='workshop.openmicroscopy.org') HOST
#@ Integer(label="Port", value=4064) PORT
#@ Integer(label="Dataset ID", value=2331) dataset_id

import java.util.ArrayList


// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.AdminFacility
import omero.gateway.facility.ROIFacility
import omero.gateway.model.EllipseData
import omero.gateway.model.LineData
import omero.gateway.model.PointData
import omero.gateway.model.PolylineData
import omero.gateway.model.PolygonData
import omero.gateway.model.RectangleData
import omero.log.SimpleLogger


import loci.formats.FormatTools
import loci.formats.ImageTools
import loci.common.DataTools

import ij.gui.Line
import ij.gui.OvalRoi
import ij.gui.PointRoi
import ij.gui.PolygonRoi
import ij.gui.Roi
import ij.IJ
import ij.ImagePlus
import ij.ImageStack
import ij.process.ByteProcessor
import ij.process.ShortProcessor
import ij.plugin.frame.RoiManager
import ij.process.FloatPolygon

group_id = -1

// If you want to do analysis for someone else,
// specify their username
target_user = ""
// The index of the select timepoint. Range from 0 to sizeT-1
selected_t = -1

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
    "List all image's ids contained in a Dataset"

    browse = gateway.getFacility(BrowseFacility)
    ctx = switch_security_context(ctx, target_user)

    ids = new ArrayList(1)
    ids.add(new Long(dataset_id))
    return browse.getImagesForDatasets(ctx, ids)

}

def switch_security_context(ctx, target_user) {
    "Switch security context"
    if (!target_user?.trim()) {
        target_user = USERNAME
    }
    service = gateway.getFacility(AdminFacility)
    user = service.lookupExperimenter(ctx, target_user)
    ctx = new SecurityContext(user.getGroupId())
    ctx.setExperimenter(user)
    return ctx
}

// Convert omero Image object as ImageJ ImagePlus object
// (An alternative to OmeroReader)
def open_omero_image(ctx, image_id, value) {
    browse = gateway.getFacility(BrowseFacility)
    println image_id
    image = browse.getImage(ctx, image_id)
    pixels = image.getDefaultPixels()
    size_z = pixels.getSizeZ()
    size_t = pixels.getSizeT()
    size_c = pixels.getSizeC()
    size_x = pixels.getSizeX()
    size_y = pixels.getSizeY()
    pixtype = pixels.getPixelType()
    pixels_type = FormatTools.pixelTypeFromString(pixtype)
    bpp = FormatTools.getBytesPerPixel(pixels_type)
    is_signed = FormatTools.isSigned(pixels_type)
    is_float = FormatTools.isFloatingPoint(pixels_type)
    is_little = false
    interleave = false
    store = gateway.getPixelsStore(ctx)
    pixels_id = pixels.getId()
    store.setPixelsId(pixels_id, false)
    stack = new ImageStack(size_x, size_y)
    start = 0
    end = size_t
    dimension = size_t
    if (value >= 0 && value < size_t) {
        start = value
        end = value + 1
        dimension = 1
    } else if (value >= size_t) {
        throw new Exception("The selected timepoint cannot be greater than or equal to " + size_t)
    }
    t = start
    while (t < end) {
        z = 0
        while (z < size_z) {
            c = 0
            while (c < size_c) {
                plane = store.getPlane(z, c, t)

                ImageTools.splitChannels(plane, 0, 1, bpp, false, interleave)
                pixels = DataTools.makeDataArray(plane, bpp, is_float, is_little)

                q = pixels
                if (plane.size() != size_x*size_y) {
                    tmp = q
                    q = zeros(size_x*size_y, 'h')
                    System.arraycopy(tmp, 0, q, 0, Math.min(q.size(), tmp.size()))
                    if (is_signed) {
                        q = DataTools.makeSigned(q)
                    }
                }
                if (q[0] instanceof Byte) {
                    ip = new ByteProcessor(size_x, size_y, q, null)
                } else {
                    ip = new ShortProcessor(size_x, size_y, q, null)
                }
                stack.addSlice('', ip)
                c += 1
            }
            z += 1
        }
        t += 1
    }
    store.close()
    // Do something
    image_name = image.getName() + '--OMERO ID:' + image.getId()
    imp = new ImagePlus(image_name, stack)
    imp.setDimensions(size_c, size_z, dimension)
    imp.setOpenAsHyperStack(true)
    imp.show()
    return imp

}

def get_rois(gateway, image_id) {
    "List all rois associated to an Image"

    browse = gateway.getFacility(ROIFacility)
    user = gateway.getLoggedInUser()
    ctx = new SecurityContext(user.getGroupId())
    return browse.loadROIs(ctx, image_id)
}

def format_shape(data, ij_shape) {
    "Convert settings e.g. color"
    settings = data.getShapeSettings()
    ij_shape.setStrokeColor(settings.getStroke())
    stroke = settings.getStrokeWidth(null)
    if (stroke != null) {
        value = settings.getStrokeWidth(null).getValue()
        ij_shape.setStrokeWidth(new Float(value))
    } else {
        ij_shape.setStrokeWidth(new Float(1))
    }
    // attach the shape to the plane
    img = IJ.getImage()
    z = data.getZ()
    c = data.getC()
    t = data.getT()
    if (z >= 0) {
        z += 1
    }
    if (t >= 0) {
        t += 1
    }
    if (c >= 0) {
        c += 1
    }
    if (img.getNChannels() == 1 && img.getNSlices() == 1 && t > 0) {
        ij_shape.setPosition(t)
    } else if (img.getNChannels() == 1 && img.getNFrames() && 1 && z > 0) {
        ij_shape.setPosition(z)
    } else if (img.getNSlices() == 1 && img.getNFrames() && 1 && c > 0) {
        ij_shape.setPosition(c)
    } else if (img.isHyperStack()) {
        ij_shape.setPosition(c, z, t)
    }
}

def convert_rectangle(data) {
    "Convert a rectangle into an imageJ rectangle"

    shape = new Roi(data.getX(), data.getY(), data.getWidth(), data.getHeight())
    format_shape(data, shape)
    return shape
}

def convert_ellipse(data) {
    "Convert an ellipse into an imageJ ellipse"
    width = data.getRadiusX()
    height = data.getRadiusY()
    shape = new OvalRoi(data.getX()-width, data.getY()-height, 2*width, 2*height)
    format_shape(data, shape)
    return shape
}

def convert_point(data) {
    "Convert a point into an imageJ point"
    shape = new PointRoi(data.getX(), data.getY())
    format_shape(data, shape)
    return shape
}

def convert_line(data) {
    "Convert a line into an imageJ line"
    shape = new Line(data.getX1(), data.getY1(), data.getX2(), data.getY2())
    format_shape(data, shape)
    return shape
}

def convert_polygon_polyline(data, type) {
    "Convert a polygon or polyline into an imageJ polygon or polyline"
    points = data.getPoints()
    polygon = new FloatPolygon()
    points.each() { p ->
        polygon.addPoint(p.getX(), p.getY())
    }

    shape = new PolygonRoi(polygon, type)
    format_shape(data, shape)
    return shape

}

def convert_omero_rois_to_ij_rois(rois_results, t) {
    "Convert the omero ROI into imageJ ROI"

    output = new ArrayList()
    rois_results.each() { roi_result ->
        rois = roi_result.getROIs()
        rois.each() { roi ->
            iterator = roi.getIterator()
            iterator.each() { s ->
                s.each() { shape ->
                    if (shape.getT() < 0 || t < 0 || shape.getT() == t) {
                        if (shape instanceof RectangleData) {
                            output.add(convert_rectangle(shape))
                        } else if (shape instanceof EllipseData) {
                            output.add(convert_ellipse(shape))
                        } else if (shape instanceof PointData) {
                            output.add(convert_point(shape))
                        } else if (shape instanceof LineData) {
                            output.add(convert_line(shape))
                        } else if (shape instanceof PolylineData) {
                            shape = convert_polygon_polyline(shape, Roi.POLYLINE)
                            output.add(shape)
                        } else if (shape instanceof PolygonData) {
                            shape = convert_polygon_polyline(shape, Roi.POLYGON)
                            output.add(shape)
                        }
                    }
                }
            }
        }
    }
    return output

}

// Prototype analysis example
gateway = connect_to_omero()
exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
ctx = new SecurityContext(group_id)
exp_id = exp.getId()

// get all images in an omero dataset
images = get_images(gateway, ctx, dataset_id)

//if target_user ~= None:
// Switch context to target user and open omeroImage as ImagePlus object
ctx = switch_security_context(ctx, target_user)

images.each { img ->
    // if target_user ~= None:
    // Switch context to target user and open omeroImage as ImagePlus object
    img_id = img.getId()
    imp = open_omero_image(ctx, img_id, selected_t)
    imp = IJ.getImage()
    // Initialize the ROI manager
    rm = new RoiManager()
    // load the OMERO ROIs linked to a given image and add them to the manager
    rois = get_rois(gateway, img_id)
    output = convert_omero_rois_to_ij_rois(rois, selected_t)
    count = 0
    output.each() { i ->
        rm.add(imp, i, count)
        count = count+1
    }
    // Run Macro:
    // IJ.run("Enhance Contrast...", "saturated=0.3")
    // or
    // IJ.runMacroFile("/path/to/Macrofile")
    // Close the various components
    IJ.selectWindow("ROI Manager")
    IJ.run("Close")
    imp.changes = false     /// Prevent "Save Changes?" dialog
    imp.close()
}
// Close the connection
gateway.disconnect()
println "processing done"
