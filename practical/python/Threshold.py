from omero.gateway import BlitzGateway, ColorHolder
from omero.model import MaskI
from omero.rtypes import rint, rlong, rstring, rdouble
import omero
import omero.scripts as scripts
import omero.util.script_utils as script_utils

import numpy as np

def threshold_plane(pixels, threshold=None):
    """
    Thresholds an image consisting of a single plane.

    Args:
    pixels (numpy array): The plane pixels [x,y]
    threshold (int or None): The threshold value; if None a suitable value will
                             be attempted to be found automatically

    Returns:
    numpy array: The binary image [x,y] (int, 0s and 1s only)
    """

    if threshold is None:
        # perform simple automatic thresholding, see
        # https://en.wikipedia.org/wiki/Thresholding_(image_processing)\
        #Automatic_thresholding 
        threshold = np.mean(pixels)
        min_value = np.amin(pixels)
        max_value = np.amax(pixels)
        t = (max_value - min_value) / 100
        it = 1
        while True:
            fg = pixels[pixels > threshold]
            bg = pixels[pixels <= threshold]
            new_threshold = (np.mean(fg) + np.mean(bg)) / 2
            if abs(new_threshold - threshold) < t:
                print("Using auto threshold value {} after {} iterations.".
                      format(threshold, it))
                break
            threshold = new_threshold
            it += 1
    else:
        print("Using manual threshold value {}".format(threshold))

    fg = pixels > threshold
    res = np.zeros(pixels.shape)
    for f,r in np.nditer([fg,res], op_flags=['readwrite']):
        r[...] = int(f)

    return res

def load_images(conn, script_params):
    """
    Load images specified by the script parameters

    Args:
    conn (BlitzGateway): The OMERO connection
    script_params (dict): The script parameters

    Returns:
    list(ImageWrapper): The images
    """
    objects, log_message = script_utils.get_objects(conn, script_params)
    data_type = script_params["Data_Type"]
    images = []
    if data_type == 'Dataset':
        for ds in objects:
            images.extend(list(ds.listChildren()))
    elif data_type == 'Project':
        for p in objects:
            for ds in p.listChildren():
                images.extend(list(ds.listChildren()))
    else:
        images = objects
    return images

def create_mask(
        binim, rgba=None, z=None, c=None, t=None, text=None,
        raise_on_no_mask=False):
    """
    Create a mask shape from a binary image (background=0)

    :param numpy.array binim: Binary 2D array, must contain values [0, 1] only
    :param rgba int-4-tuple: Optional (red, green, blue, alpha) colour
    :param z: Optional Z-index for the mask
    :param c: Optional C-index for the mask
    :param t: Optional T-index for the mask
    :param text: Optional text for the mask
    :param raise_on_no_mask: If True (default) throw an exception if no mask
           found, otherwise return an empty Mask
    :return: An OMERO mask
    :raises NoMaskFound: If no labels were found
    :raises InvalidBinaryImage: If the maximum labels is greater than 1
    """

    # Find bounding box to minimise size of mask
    xmask = binim.sum(0).nonzero()[0]
    ymask = binim.sum(1).nonzero()[0]
    if any(xmask) and any(ymask):
        x0 = min(xmask)
        w = max(xmask) - x0 + 1
        y0 = min(ymask)
        h = max(ymask) - y0 + 1
        submask = binim[y0:(y0 + h), x0:(x0 + w)]
        if (not np.array_equal(np.unique(submask), [0, 1]) and not
        np.array_equal(np.unique(submask), [1])):
            raise Exception("Invalid binary image")
    else:
        if raise_on_no_mask:
            raise Exception("No mask found")
        x0 = 0
        w = 0
        y0 = 0
        h = 0
        submask = []

    mask = MaskI()
    mask.setBytes(np.packbits(np.asarray(submask, dtype=int)))
    mask.setWidth(rdouble(w))
    mask.setHeight(rdouble(h))
    mask.setX(rdouble(x0))
    mask.setY(rdouble(y0))

    if rgba is not None:
        ch = ColorHolder.fromRGBA(*rgba)
        mask.setFillColor(rint(ch.getInt()))
    if z is not None:
        mask.setTheZ(rint(z))
    if c is not None:
        mask.setTheC(rint(c))
    if t is not None:
        mask.setTheT(rint(t))
    if text is not None:
        mask.setTextValue(rstring(text))

    return mask

def threshold_image(conn, image, threshold=None, c=0, z=0, t=0,
                  rgba=(255,255,0,100), save_images=False):
    """
    Tresholds the image and adds the result as mask to the image.
    Optionally saves the thresholed image.

    Args:
    conn (BlitzGateway): The OMERO connection
    image (ImageWrapper): The image
    c (int): The channel index (default: 0)
    z (int): The Z plane index (default: 0)
    t (int): The time point (default: 0)
    threshold (int or None): The threshold for the foreground channel
    rgba (int tuple): The RGBA value of the mask color
    save_images (bool): Save the thresholded images
    """
    print("Using plane c={}, z={}, t={}".format(c, z, t))
    pixels = image.getPrimaryPixels().getPlane(z, c, t)

    binary_image = threshold_plane(pixels, threshold=threshold)

    if save_images:
        try:
            name = image.getName()+"_thresholded"
            conn.createImageFromNumpySeq(
                iter([binary_image]), name, sizeZ=1, sizeC=1, dataset=image.getParent())
            print("Save thresholded image as {}".format(name))
        except Exception as e:
            print("Can't save thresholded image\n{}".format(e))

    try:
        mask = create_mask(binary_image, rgba=rgba)
        roi = omero.model.RoiI()
        roi.setImage(image._obj)
        roi.addShape(mask)
        conn.getUpdateService().saveAndReturnObject(roi)
        print("Add mask to image.")
    except Exception as e:
        print("Can't create mask.\n{}".format(e))

def delete_masks(conn, image):
    """
    Delets all mask ROIs of the given image.

    Args:
    conn (BlitzGateway): The OMERO connection
    image (ImageWrapper): The image
    """
    roi_service = conn.getRoiService()
    result = roi_service.findByImage(image.getId(), None)
    ids = []
    for roi in result.rois:
        for s in roi.copyShapes():
            if type(s) == omero.model.MaskI:
                ids.append(roi.getId().getValue())
                break
    if ids:
        conn.deleteObjects("Roi", ids, deleteAnns=True, deleteChildren=True, wait=True)

def run_script(conn, script_params):
    threshold = script_params["Threshold"]
    if threshold < 0:
        threshold = None
    del_masks = script_params["Delete_previous_Masks"]
    c = script_params["Channel"]
    z = script_params["Z_Plane"]
    t = script_params["Timepoint"]

    r = int(script_params["Mask_color_Red"])
    g = int(script_params["Mask_color_Green"])
    b = int(script_params["Mask_color_Blue"])
    a = int(script_params["Mask_color_Alpha"])
    rgba = (r,g,b,a)

    save_images = script_params["Save_Images"]

    images = load_images(conn, script_params)
    for img in images:
        print("Processing image {}".format(img.getName()))
        if del_masks:
            try:
                delete_masks(conn, img)
            except Exception as e:
                print("Can't delete previous masks.\n{}".format(e))
        try:
            threshold_image(conn, img, threshold=threshold, c=c, z=z, t=t,
                          rgba=rgba, save_images=save_images)
        except Exception as e:
            print("Could not threshold image\n{}".format(e))


if __name__ == "__main__":

    dataTypes = [rstring('Image'),rstring('Dataset'),rstring('Project')]

    client = scripts.client(
        'Threshold.py', """
Performs simple Image thresholding on one or multiple images.
Saves the result as Mask ROI on the image and optionally saves the
thresholded image in the same dataset as the original image.
Specifiy the plane to threshold (channel, z plane, timepoint) and 
optionally a threshold value (hint: check the histogram of the channel
to get an idea for a good value). If you specify a negative value,
a simple iterative approach is used to automatically find a good value.
        """,

        scripts.String(
            "Data_Type", optional=False, grouping = "a",
            description="Choose source of images",
            values=dataTypes, default="Dataset"),
        scripts.List(
            "IDs", optional=False, grouping = "a.2",
            description="List of IDs to process.").ofType(rlong(0)),

        scripts.Int(
            "Channel", optional=False, grouping = "b",
            description="Which channel to use for indexing (first channel = 0)",
            min=0, default=0),
        scripts.Int(
            "Z_Plane", optional=False, grouping = "b.2",
            description="Which Z plane to use for indexing (first channel = 0)",
            min=0, default=0),
        scripts.Int(
            "Timepoint", optional=False, grouping = "b.3",
            description="Which timepoint to use for indexing (first channel = 0)",
            min=0, default=0),
        scripts.Float("Threshold", optional=False, grouping = "b.4",
                    description="Threshold value to separate foreground from background "
                                "(enter negative value in order to use auto thresholding)",
                    default=-1.0),

        scripts.Bool("Save_Images", grouping = "d",
                     description="Save the thresholded images as new images",
                     default=False),

        scripts.Bool("Delete_previous_Masks", grouping = "e",
                     description="Delete all previous Masks of the images",
                     default=True),
        scripts.Int("Mask_color_Red", optional=False, grouping = "e.2",
                    description="Display color of the mask",
                    min=0, max=255, default=255),
        scripts.Int("Mask_color_Green", optional=False, grouping = "e.3",
                    description="Display color of the mask",
                    min=0, max=255, default=255),
        scripts.Int("Mask_color_Blue", optional=False, grouping = "e.4",
                    description="Display color of the mask",
                    min=0, max=255, default=0),
        scripts.Int("Mask_color_Alpha", optional=False, grouping = "e.5",
                    description="Display color of the mask",
                    min=0, max=255, default=100),

        version="1.0",
        authors=["Dominik Lindner"],
        contact="d.lindner@dundee.ac.uk",
    )

    try:
        conn = BlitzGateway(client_obj=client)
        script_params = client.getInputs(unwrap=True)
        run_script(conn, script_params)
        client.setOutput("Message", rstring("Finished."))

    finally:
        client.closeSession()

