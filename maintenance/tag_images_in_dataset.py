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


from omero.gateway import BlitzGateway
import omero

# Go through all users in the range,
# Adding a single existing tag to all images in the named Dataset
# filtering images by name

dataset_name = "Condensation"
images_to_tag = ["A1.pattern1.tif",
                 "B12.pattern1.tif",
                 "B12.pattern2.tif",
                 "B12.pattern3.tif",
                 "C4.pattern9.tif",
                 "C4.pattern.tif"]
tag = 4236

for i in range(1, 40):

    username = "user-%s" % i
    password = "password"
    host = "outreach.openmicroscopy.org"
    conn = BlitzGateway(username, password, host=host, port=4064)

    updateService = conn.getUpdateService()

    ds = conn.getObject("Dataset", attributes={'name': dataset_name},
                        opts={'owner': conn.getUserId()})
    print "Dataset", ds

    def link_tag(iid, tagid):

        link = omero.model.ImageAnnotationLinkI()
        link.parent = omero.model.ImageI(iid, False)
        link.child = omero.model.TagAnnotationI(tagid, False)
        print "Tagging image %s to tag %s" % (iid, tagid)
        try:
            updateService.saveObject(link)
        except:
            print "Already tagged!"

    for i in ds.listChildren():
        if i.getName() in images_to_tag:
            link_tag(i.id, tag)

    # Close connection for each user when done
    conn.close()
