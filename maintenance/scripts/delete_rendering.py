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
This script finds all rendering on images from a dataset specified
by dataset id or dataset name and deletes these rendering settings.
"""

import argparse
import omero
from omero.gateway import BlitzGateway


def run(name, password, dataset_name, dataset_id, host, port):

    conn = BlitzGateway(name, password, host=host, port=port)
    try:
        conn.connect()
        datasets = []
        if dataset_id >= 0:
            datasets.append(conn.getObject("Dataset", dataset_id))
        else:
            datasets = conn.getObjects("Dataset",
                                       attributes={"name": dataset_name})

        for dataset in datasets:
            print(dataset.getId())
            for image in dataset.listChildren():
                pixels = image.getPrimaryPixels()
                pixId = pixels.getId()
                print(pixId)
                params = omero.sys.ParametersI()
                query = "from RenderingDef where pixels.id = '%s'" % pixId
                query_service = conn.getQueryService()
                result = query_service.findAllByQuery(query, params,
                                                      conn.SERVICE_OPTS)
                if result is not None:
                    rnd_ids = [rnd.id.val for rnd in result]
                    if len(rnd_ids) > 0:
                        print("Deleting %s rnds..." % len(rnd_ids))
                        print(rnd_ids)
                        conn.deleteObjects("RenderingDef", rnd_ids, wait=True)

    except Exception as exc:
        print("Error while deleting rendering: %s" % str(exc))
    finally:
        conn.close()


def main(args):
    parser = argparse.ArgumentParser()
    parser.add_argument('password')
    parser.add_argument('--datasetid', default=-1,
                        help="The ID of the dataset")
    parser.add_argument('--datasetname', default="",
                        help="The name of the dataset")
    parser.add_argument('--name', default="trainer-1",
                        help="The user deleting the rois")
    parser.add_argument('--server', default="outreach.openmicroscopy.org",
                        help="OMERO server hostname")
    parser.add_argument('--port', default=4064, help="OMERO server port")
    args = parser.parse_args(args)
    run(args.name, args.password, args.datasetname, args.datasetid,
        args.server, args.port)


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
