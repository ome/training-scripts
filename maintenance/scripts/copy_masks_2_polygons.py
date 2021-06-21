
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

PAGE_SIZE = 10

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


def image_ids_by_name(dataset):
    ids_by_name = {}
    for image in dataset.listChildren():
        ids_by_name[image.name] = image.id
    return ids_by_name


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


def process_image(conn, conn2, image, to_image_id):
    roi_service = conn.getRoiService()
    update_service = conn2.getUpdateService()

    print("Processing...", image.name, image.id)

    old = conn2.getRoiService().findByImage(to_image_id, None, conn2.SERVICE_OPTS)
    if len(old.rois) > 0:
        print("Image", to_image_id, "already has ROIs. Ignoring...")
        return

    opts = RoiOptions()
    offset = 0
    opts.offset = rint(offset)
    opts.limit = rint(PAGE_SIZE)

    size_x = image.getSizeX()
    size_y = image.getSizeY()

    # NB: we repeat this query below for each 'page' of ROIs
    result = roi_service.findByImage(image.id, opts, conn.SERVICE_OPTS)

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
                t, c, z, y, x, h, w = dims
                plane = np.zeros((size_y, size_x))
                plane[y:y+h, x:x+w] = np_mask
                contours = measure.find_contours(plane, 0.5)
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
        result = roi_service.findByImage(image.id, opts, conn.SERVICE_OPTS)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument('username2', help='Target server Username')
    parser.add_argument('password2', help='Target server Password')
    parser.add_argument('server2', help='Target server')
    parser.add_argument('source', help=(
        'Copy ROIs FROM this: Image:ID or Dataset:ID'))
    parser.add_argument('target', help=(
        'Copy ROIs TO this: Image:ID or Dataset:ID'))
    args = parser.parse_args(argv)


    with cli_login() as cli:
        conn = BlitzGateway(client_obj=cli._client)
        conn.SERVICE_OPTS.setOmeroGroup(-1)

        conn2 = BlitzGateway(args.username2, args.password2,
                             port=4064, host=args.server2)
        conn2.connect()

        source_images = []
        target_image_ids = []

        source = args.source
        source_id = int(source.split(":")[1])
        target = args.target
        target_id = int(target.split(":")[1])

        if source.startswith('Image:'):
            source_images.append(conn.getObject('Image', source_id))
            target_image_ids.append(target_id)
        elif source.startswith('Dataset:'):
            dataset = conn.getObject('Dataset', source_id)
            target_dataset = conn2.getObject('Dataset', target_id)
            ids_by_name = image_ids_by_name(target_dataset)
            for image in dataset.listChildren():
                if image.name in ids_by_name:
                    source_images.append(image)
                    target_image_ids.append(ids_by_name[image.name])
        else:
            print("Source needs to be Image:ID or Dataset:ID")

        print("Processing", source_images)
        print("...to target images:", target_image_ids)
        for image, to_target_id in zip(source_images, target_image_ids):
            process_image(conn, conn2, image, to_target_id)

        conn2.close()


if __name__ == '__main__':
    # First, login to source OMERO $ omero login
    # Copy to target server, with source and target. e.g. Image:123 Image:456
    # $ python copy_masks_2_polygons.py user pass server FROM_TARGET TO_TARGET
    main(sys.argv[1:])
