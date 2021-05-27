
from skimage import measure

import argparse
import sys
import numpy as np

import omero
import omero.clients
from omero.gateway import BlitzGateway
from omero.rtypes import unwrap, rint, rstring
from omero.cli import cli_login
from omero.api import RoiOptions


def mask_to_binim_yx(mask):
    """
    # code from omero-cli-zarr
    :param mask MaskI: An OMERO mask

    :return: tuple of
            - Binary mask
            - (T, C, Z, Y, X, w, h) tuple of mask settings (T, C, Z may be
            None)
    """

    t = unwrap(mask.theT)
    c = unwrap(mask.theC)
    z = unwrap(mask.theZ)

    x = int(mask.x.val)
    y = int(mask.y.val)
    w = int(mask.width.val)
    h = int(mask.height.val)

    mask_packed = mask.getBytes()
    # convert bytearray into something we can use
    intarray = np.fromstring(mask_packed, dtype=np.uint8)
    binarray = np.unpackbits(intarray)
    # truncate and reshape
    binarray = np.reshape(binarray[: (w * h)], (h, w))

    return binarray, (t, c, z, y, x, h, w)


def rgba_to_int(red, green, blue, alpha=255):
    """ Return the color as an Integer in RGBA encoding """
    r = red << 24
    g = green << 16
    b = blue << 8
    a = alpha
    rgba_int = r+g+b+a
    if (rgba_int > (2**31-1)):       # convert to signed 32-bit int
        rgba_int = rgba_int - 2**32
    return rgba_int


def get_longest_contour(contours):
    contour = contours[0]
    for c in contours:
        if len(c) > len(contour):
            contour = c
    return c


def add_polygon(roi, contour, x_offset=0, y_offset=0, z=None, t=None):
    """ points is 2D list of [[x, y], [x, y]...]"""

    stride = 4
    coords = []
    # points in contour are adjacent pixels, which is too verbose
    # take every nth point
    for count, xy in enumerate(contour):
        if count % stride == 0:
            coords.append(xy)
    if len(coords) < 2:
        return
    points = ["%s,%s" % (xy[1] + x_offset, xy[0] + y_offset) for xy in coords]
    points = ", ".join(points)

    polygon = omero.model.PolygonI()
    if z is not None:
        polygon.theZ = rint(z)
    if t is not None:
        polygon.theT = rint(t)
    polygon.strokeColor = rint(rgba_to_int(255, 255, 255))
    # points = "10,20, 50,150, 200,200, 250,75"
    polygon.points = rstring(points)
    roi.addShape(polygon)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument('username2', help='Target server Username')
    parser.add_argument('password2', help='Target server Password')
    parser.add_argument('server2', help='Target server')
    parser.add_argument('imageid', type=int, help=(
        'Copy ROIs FROM this image'))
    parser.add_argument('imageid2', type=int, help=(
        'Copy ROIs TO this image'))
    args = parser.parse_args(argv)

    to_image_id = args.imageid2

    PAGE_SIZE = 50

    with cli_login() as cli:
        conn = BlitzGateway(client_obj=cli._client)
        conn2 = BlitzGateway(args.username2, args.password2,
                             port=4064, host=args.server2)
        conn2.connect()

        roi_service = conn.getRoiService()
        update_service = conn2.getUpdateService()

        opts = RoiOptions()
        offset = 0
        opts.offset = rint(offset)
        opts.limit = rint(PAGE_SIZE)

        conn.SERVICE_OPTS.setOmeroGroup(-1)
        image = conn.getObject('Image', args.imageid)
        size_x = image.getSizeX()
        size_y = image.getSizeY()
        print(image.name)

        # NB: we repeat this query below for each 'page' of ROIs
        result = roi_service.findByImage(args.imageid, opts, conn.SERVICE_OPTS)

        while len(result.rois) > 0:
            print("offset", offset)
            print("Found ROIs:", len(result.rois))
            for roi in result.rois:

                new_roi = omero.model.RoiI()
                new_roi.setImage(omero.model.ImageI(to_image_id, False))
                shapes_added = False

                for shape in roi.copyShapes():

                    if not isinstance(shape, omero.model.MaskI):
                        continue
                    # assume shape is a Mask
                    np_mask, dims = mask_to_binim_yx(shape)
                    print('shape', np_mask.shape)
                    t, c, z, y, x, h, w = dims
                    print('dims', dims)
                    image = np.zeros((size_y, size_x))
                    image[y:y+h, x:x+w] = np_mask
                    contours = measure.find_contours(image, 0.5)
                    print('Found contours:', len(contours))
                    if len(contours) > 0:
                        contour = get_longest_contour(contours)
                        # Only add 1 Polygon per Mask Shape.
                        # First is usually the longest
                        add_polygon(new_roi, contour, 0, 0, z, t)
                    shapes_added = True

                if shapes_added:
                    update_service.saveObject(new_roi)

            offset += PAGE_SIZE
            opts.offset = rint(offset)
            result = roi_service.findByImage(args.imageid, opts,
                                             conn.SERVICE_OPTS)

        conn2.close()


if __name__ == '__main__':
    # First, login to source OMERO $ omero login
    # Copy to target server, with Image IDs
    # $ python copy_masks_2_polygons.py user pass server FROM_IID TO_IID
    main(sys.argv[1:])
