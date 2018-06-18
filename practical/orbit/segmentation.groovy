import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.models.SegmentationResult
import com.actelion.research.orbit.imageAnalysis.utils.OrbitHelper

import java.awt.Shape

import com.actelion.research.orbit.imageAnalysis.components.*
import com.actelion.research.orbit.imageAnalysis.models.*
import com.actelion.research.orbit.imageprovider.ImageProviderOmero

import omero.gateway.model.*
import omero.model.*
import static omero.rtypes.rstring;
import static omero.rtypes.rint;
import omero.gateway.facility.BrowseFacility

// Use the currently opened image...
final OrbitImageAnalysis OIA = OrbitImageAnalysis.getInstance();
ImageFrame iFrame = OIA.getIFrame();
println("selected image: "+iFrame);
RawDataFile rdf = iFrame.rdf;

// Get the OMERO Image ID
long omeroImageId = rdf.getRawDataFileId()
println("ID:" + omeroImageId)

// Login to create a new connection with OMERO
ImageProviderOmero imageProvider = new ImageProviderOmero()
imageProvider.authenticateUser("username", "password");


// Load the model from file and classify the image
OrbitModel model = OrbitModel.LoadFromFile("/Full/path/to/orbit-model.omo")
println("Loaded Model")
println(model)
SegmentationResult res = OrbitHelper.Segmentation(rdf.rawDataFileId, model, null, 1)

// handle the segmented objects
println("SegmentationResult")
for (Shape shape: res.shapeList) {
    // can cast shape to Polygon or simply listPoints
    String points = shape.listPoints()

    // Create Polygon in OMERO
    p = new PolygonI()
    // Convert "x, y; x, y" format to "x, y, x, y" for OMERO
    points = points.replace(";", ",")
    p.setPoints(rstring(points))
    p.setTheT(rint(0))
    p.setTheZ(rint(0))
    p.setStrokeColor(rint(-65281))   // yellow

    // Add each shape to an ROI on the Image
    ImageI image = new ImageI(omeroImageId, false)
    RoiI roi = new RoiI()
    roi.setImage(image)
    roi.addShape(p)

    // Save
    imageProvider.getGatewayAndCtx().getGateway().getUpdateService(imageProvider.getGatewayAndCtx().getCtx()).saveAndReturnObject(roi)
}

// 
imageProvider.close()
