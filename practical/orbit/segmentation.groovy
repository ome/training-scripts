import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation
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
import omero.gateway.Gateway
import omero.gateway.SecurityContext
import static omero.rtypes.rstring
import static omero.rtypes.rint
import omero.gateway.facility.BrowseFacility

// Edit these parameters
String USERNAME = "username"
String PASSWORD = "password"

// Use the currently opened image...
final OrbitImageAnalysis OIA = OrbitImageAnalysis.getInstance()
ImageFrame iFrame = OIA.getIFrame()
println("selected image: " + iFrame)
RawDataFile rdf = iFrame.rdf

// Get the OMERO Image ID
long omeroImageId = rdf.getRawDataFileId()
println("ID:" + omeroImageId)

// Login to create a new connection with OMERO
ImageProviderOmero imageProvider = new ImageProviderOmero()
imageProvider.authenticateUser(USERNAME, PASSWORD)
Gateway gateway = imageProvider.getGatewayAndCtx().getGateway()
SecurityContext ctx = imageProvider.getGatewayAndCtx().getCtx()

List<RawAnnotation> annotations = imageProvider.LoadRawAnnotationsByType(RawAnnotation.ANNOTATION_TYPE_MODEL)
println("Found " + annotations.size() + " files")

// Load the model from first FileAnnotation and segment the image
int fileAnnId = annotations[0].getRawAnnotationId()
OrbitModel model = OrbitModel.LoadFromOrbit(fileAnnId)
println("Loaded Model: " + model.getName())
SegmentationResult res = OrbitHelper.Segmentation(rdf.rawDataFileId, model, null, 1)

// handle the segmented objects
println("SegmentationResult: " + res.shapeList.size() + " shapes")
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
    gateway.getUpdateService(ctx).saveAndReturnObject(roi)
}

println("Close...")
imageProvider.close()
