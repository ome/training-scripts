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


for i in range(1, 3):

    username = "user-%s" % i
    password = "password"
    host = "outreach.openmicroscopy.org"
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    params = omero.sys.ParametersI()
    params.addString('username', username)
    query = "from Image where name='P-TRE_10_R3D_D3D.dv' \
            AND details.owner.omeName=:username"
    query_service = conn.getQueryService()
    image = query_service.findAllByQuery(query, params, conn.SERVICE_OPTS)

    image_id = image[0].getId().getValue()

    print 'image', image[0].getName().getValue()
    print 'id', image_id

    dataset_obj = omero.model.DatasetI()
    dataset_obj.setName(omero.rtypes.rstring("Fiji-batch"))
    dataset_obj = conn.getUpdateService().saveAndReturnObject(dataset_obj)
    dataset_id = dataset_obj.getId().getValue()
    print username, dataset_id

    link = omero.model.DatasetImageLinkI()
    link.setParent(dataset_obj)
    link.setChild(omero.model.ImageI(image_id, False))
    conn.getUpdateService().saveObject(link)

    conn.close()
