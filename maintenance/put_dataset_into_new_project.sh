#!/bin/bash
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

echo Starting
PATH=path_to_omero_server/OMERO.server/bin/omero
PASSWORD="password"
HOST=outreach.openmicroscopy.org
for i in {3..40}
do  $PATH login -u user-$i -s $HOST -w $PASSWORD
    project=$($PATH obj new Project name='images')
    datasetId=$((i+1108))
    $PATH obj new ProjectDatasetLink parent=$project child=Dataset:$datasetId
done
echo Finishing