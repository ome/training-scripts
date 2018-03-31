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


from omero.gateway import BlitzGateway
from omero.model import DatasetI
from omero.model import DatasetImageLinkI
from omero.model import ImageI
from omero.model import ProjectI
from omero.rtypes import rstring

# Go through all users in the range,
# Create a new project and link it to a specified dat

project_name = "images"
dataset_name = ""

for user_number in range(3, 40):
    username = "user-%s" % user_number
    password = "password"
    host = "outreach.openmicroscopy.org"
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    project = ProjectI()
    project.setName(rstring(project_name))
    update_service = conn.getUpdateService()
    project = update_service.saveAndReturn(project)

    ds = conn.getObject("Dataset", attributes={'name': dataset_name},
                        opts={'owner': conn.getUserId()})
    dataset_id = ds.getId().getValue()
    print username, dataset_id

    link = ProjectDatasetLinkI()
    link.setParent(ImageI(project.getId().getValue(), False))
    link.setChild(DatasetI(dataset_id, False))
    conn.getUpdateService().saveObject(link)

    # Close connection for each user when done
    conn.close()
