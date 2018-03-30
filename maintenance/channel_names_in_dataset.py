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

# Go through all users in the range,
# Set channel names for all images in named Dataset

dataset_name = "Condensation"

for user_number in range(38, 40):
    username = "user-%s" % user_number
    password = "password"
    host = "outreach.openmicroscopy.org"
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    ds = conn.getObject("Dataset", attributes={'name': dataset_name},
                        opts={'owner': conn.getUserId()})
    print "Dataset", ds

    conn.setChannelNames("Dataset", [ds.getId()],
                         {1: "H2B", 2: "nuclear lamina"})

    # Close connection for each user when done
    conn.close()
