#!/bin/bash
# -----------------------------------------------------------------------------
#   Copyright (C) 2018 University of Dundee. All rights reserved.
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
#
# In this script we:
#   * log in as another user
#   * create a new dataset
#   * import an image
#   * upload an attachment
#
#
dir=$(pwd -P)
OMEROPATH=${OMEROPATH:-/opt/omero/server/OMERO.server/bin/omero}
PASSWORD=${PASSWORD:-ome}
HOST=${HOST:-outreach.openmicroscopy.org}
SUDOER=${SUDOER:-trainer-1}
OMEUSER=${OMEUSER:-user-1}
IMAGEPATH=${IMAGEPATH:-}
ATTACHMENTPATH=${ATTACHMENTPATH:-}

deleteimage=false
deletefile=false

if [ -z "$IMAGEPATH" ]; then
    touch image_to_import.fake
    IMAGEPATH=$dir/image_to_import.fake
    deleteimage=true
fi

if [ -z "$ATTACHMENTPATH" ]; then
    touch file_to_upload.csv
    ATTACHMENTPATH=$dir/file_to_upload.csv
    deletefile=true
fi

# Log in as another user
$OMEROPATH login --sudo ${SUDOER} -u $OMEUSER -s $HOST -w $PASSWORD

# Create a dataset as the specified user
dataset=`$OMEROPATH obj new Dataset name='Basel-workflow'`

# Import the image in the newly created dataset
result=`$OMEROPATH import -T $dataset $IMAGEPATH --output ids`

# Retrieve the Image's id. It is returned as Image:123
imageid=`cut -d':' -f2 <<< $result`
printf 'imageid %s \n' "$id"

# Upload a CSV
result=`$OMEROPATH upload $ATTACHMENTPATH`
originalfileid=`cut -d':' -f2 <<< $result`
printf 'originalfileid %s \n' "$originalfileid"

# Create a file annotation
result=`$OMEROPATH obj new FileAnnotation file=OriginalFile:$originalfileid`
fileid=`cut -d':' -f2 <<< $result`
printf 'fileid %s \n' "$fileid"

# Link the annotation to the Image
$OMEROPATH obj new ImageAnnotationLink parent=Image:$imageid child=FileAnnotation:$fileid

# Delete the Image and the local file if created
if [ $deleteimage = true ]; then
    rm $IMAGEPATH
fi

if [ $deletefile = true ]; then
    rm $ATTACHMENTPATH
fi
