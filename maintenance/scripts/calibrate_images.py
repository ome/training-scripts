#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
# -----------------------------------------------------------------------------
#   Copyright (C) 2017 University of Dundee. All rights reserved.
#
#   This program is free software; you can redistribute it and/or modify
#   it under the terms of the GNU General Public License as published by
#   the Free Software Foundation; either version 2 of the License, or
#   (at your option) any later version.
#   This program is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#   GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public License along
#   with this program; if not, write to the Free Software Foundation, Inc.,
#   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# ------------------------------------------------------------------------------

"""
This script changes the calibration on all images contained in a Dataset
with a specified name belonging to users user-1 through
user-50.
Each calibration change is made by the owner of the Dataset and the images
themselves.
"""

import argparse
import omero
from omero.gateway import BlitzGateway
from omero.model.enums import UnitsLength


def setPixelSize(conn, image, value, unit):
    print('Image', image.id, value)
    value_z = None
    if 'x' in value:
        value_xy = float(value.split('x')[0])
        value_z = float(value.split('x')[1])
    else:
        value_xy = float(value)
    xy = omero.model.LengthI(value_xy, getattr(UnitsLength, unit))
    p = image.getPrimaryPixels()._obj
    p.setPhysicalSizeX(xy)
    p.setPhysicalSizeY(xy)
    if value_z is not None:
        z = omero.model.LengthI(value_z, getattr(UnitsLength, unit))
        p.setPhysicalSizeZ(z)
    conn.getUpdateService().saveObject(p)


def set_by_target_id(password, username, target, host, port, value, unit):

    conn = BlitzGateway(username, password, host=host, port=port)
    conn.connect()
    target_type = target.split(':')[0]
    target_id = int(target.split(':')[1])
    if target_type not in ['Project', 'Dataset', 'Image']:
        print("Target must be Project:ID, Dataset:ID or Image:ID")
        return
    images = []
    if target_type == "Project":
        for dataset in conn.getObject('Project', target_id).listChildren():
            images.extend((list(dataset.listChildren())))
    elif target_type == "Dataset":
        images = list(conn.getObject('Dataset', target_id).listChildren())
    elif target_type == "Image":
        images = [conn.getObject("Image", target_id)]
    print('images', images)

    for image in images:
        setPixelSize(conn, image, value, unit)


def set_for_users_by_dataset_name(password, target, host, port, value, unit):

    for i in range(1, 2):

        username = "user-%s" % i
        print(username)
        conn = BlitzGateway(username, password, host=host, port=port)
        try:
            conn.connect()

            params = omero.sys.ParametersI()
            params.addString('username', username)
            query = "from Dataset where name='%s' \
                     AND details.owner.omeName=:username" % target
            service = conn.getQueryService()
            dataset = service.findAllByQuery(query, params, conn.SERVICE_OPTS)

            if len(dataset) == 0:
                print("No dataset with name %s found" % target)
                continue

            dataset_obj = dataset[0]
            datasetId = dataset[0].getId().getValue()
            print('dataset', datasetId)
            for image in conn.getObject("Dataset", datasetId).listChildren():
                setPixelSize(conn, image, value, unit)
        except Exception as exc:
            print("Error during calibration: %s" % str(exc))
        finally:
            conn.close()

def run(args):
    password = args.password
    target = args.target
    value = args.value
    unit = args.unit
    host = args.server
    port = args.port

    # Handle target is e.g. "Project:1"
    if ':' in target:
        try:
            target_id = int(target.split(':')[1])
            set_by_target_id(password, args.user, target, host, port, value, unit)
            return
        except ValueError:
            print("Not valid Project or Dataset ID")

    # Assume that target was a Dataset Name
    set_for_users_by_dataset_name(password, target, host, port, value, unit)

def main(args):
    """
    The script sets Pixels Sizes in 2 use-cases. 
    Each needs a pixel size 'value' eg. 0.85 for X and Y or '0.85x0.2' for XY and Z
    Units are optional. Default is "MICROMETER".

    1) For many users 'user-1...user-50' etc with a NAMED Dataset
    $ calibrate_images.py [password] [dataset_name] [value] --server [server]

    2) For a single user, where the target is Project:ID or Dataset:ID
    $ calibrate_images.py [password] [target] [value] --user [username] --server [server]
    """
    parser = argparse.ArgumentParser()
    parser.add_argument('password')
    parser.add_argument(
        'target',
        help="Dataset name (for many users) or target Project/Dataset/Image:ID")
    parser.add_argument('value', help="Pixel size value")
    parser.add_argument('--unit', default="MICROMETER",
        help="Unit from omero.")
    parser.add_argument('--user', help="Username ONLY if single user")
    parser.add_argument('--server', default="localhost",
                        help="OMERO server hostname")
    parser.add_argument('--port', default=4064, help="OMERO server port")
    args = parser.parse_args(args)

    run(args)


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
