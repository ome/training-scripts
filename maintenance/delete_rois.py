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


import omero
from omero.gateway import BlitzGateway


for i in range(1, 41):

    username = "user-%s" % i
    password = "password"
    host = "outreach.openmicroscopy.org"
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    params = omero.sys.ParametersI()
    params.addString('username', username)
    query = "from Dataset where name='Condensation' \
             AND details.owner.omeName=:username"
    query_service = conn.getQueryService()
    dataset = query_service.findAllByQuery(query, params, conn.SERVICE_OPTS)

    dataset_id = dataset[0].getId().getValue()

    print 'dataset', dataset_id
    dataset = conn.getObject("Dataset", dataset_id)
    obj_ids = []
    roi_service = conn.getRoiService()
    for image in dataset.listChildren():

        result = roi_service.findByImage(image.getId(), None)
        obj_ids = []
        for roi in result.rois:
            roi_id = roi.getId().getValue()
            obj_ids.append(roi_id)

        if len(result.rois) > 0:
            delete_children = True
            conn.deleteObjects("Roi", obj_ids, deleteAnns=True,
                               deleteChildren=delete_children, wait=True)
            value = 'deleted %s ROIs on \
                    image %s' % (len(result.rois), image.getId())
            print value
    conn.close()
