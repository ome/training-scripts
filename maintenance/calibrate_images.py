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
from omero.model.enums import UnitsLength


for i in range(2, 41):

    username = "user-%s" % i
    password = "password"
    host = "outreach.openmicroscopy.org"
    print username
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    params = omero.sys.ParametersI()
    params.addString('username', username)
    query = "from Dataset where name='Condensation' \
             AND details.owner.omeName=:username"
    service = conn.getQueryService()
    dataset = service.findAllByQuery(query, params, conn.SERVICE_OPTS)
    dataset_obj = dataset[0]
    datasetId = dataset[0].getId().getValue()

    print 'dataset', datasetId
    params2 = omero.sys.ParametersI()
    params2.addId(dataset_obj.getId())
    query = "select l.child.id from DatasetImageLink \
             l where l.parent.id = :id"
    images = service.projection(query, params2, conn.SERVICE_OPTS)

    images_obj = images[0]

    values = []
    for k in range(0, len(images)):

        image_id = images[k][0].getValue()
        delta_t = 300

        image = conn.getObject("Image", image_id)

        u = omero.model.LengthI(0.33, UnitsLength.MICROMETER)
        p = image.getPrimaryPixels()._obj
        p.setPhysicalSizeX(u)
        p.setPhysicalSizeY(u)
        values.append(p)

    if len(images) > 0:
        conn.getUpdateService().saveArray(values)

    conn.close()