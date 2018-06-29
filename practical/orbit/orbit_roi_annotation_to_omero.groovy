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

import java.awt.Polygon
import java.awt.Point
import omero.model.PolygonI
import omero.model.ImageI
import omero.model.RoiI
import omero.gateway.*
import omero.api.*
import omero.sys.ParametersI
import omero.gateway.Gateway
import omero.gateway.SecurityContext
import static omero.rtypes.rstring
import static omero.rtypes.rint
import static omero.rtypes.rlong
import omero.gateway.facility.BrowseFacility

// Example script to show how to load Orbit ROI annotations from OMERO
// and convert them to Polygons on the Image.

// Edit these parameters
String USERNAME = "username"
String PASSWORD = "password"

// Use the currently opened image...
final OrbitImageAnalysis OIA = OrbitImageAnalysis.getInstance()
ImageFrame iFrame = OIA.getIFrame()
println("selected image: " + iFrame)
RawDataFile rdf = iFrame.rdf

// Get the OMERO Image ID
int omeroImageId = rdf.getRawDataFileId()
println("ID:" + omeroImageId)

// Login to create a new connection with OMERO
ImageProviderOmero imageProvider = new ImageProviderOmero()
imageProvider.authenticateUser(USERNAME, PASSWORD)
Gateway gateway = imageProvider.getGatewayAndCtx().getGateway()
SecurityContext ctx = imageProvider.getGatewayAndCtx().getCtx()

// Load all annotations on the OMERO Image
List<RawAnnotation> annotations = imageProvider.LoadRawAnnotationsByRawDataFile(omeroImageId)
println("Found " + annotations.size() + " files")

List<RoiI> roisToSave = new ArrayList<RoiI>()
for (RawAnnotation ann: annotations) {
	// Cast to ImageAnnotation, scale to 100 and get Points
	ImageAnnotation ia = new ImageAnnotation(ann)
	Polygon poly = ((IScaleableShape)ia.getFirstShape()).getScaledInstance(100d,new Point(0,0))
	String points = poly.listPoints()
	println(points)

	//Create Polygon in OMERO
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
	roisToSave.add(roi)
}
// Save
gateway.getUpdateService(ctx).saveAndReturnArray(roisToSave)

println("Close...")
imageProvider.close()
