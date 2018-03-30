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


# This Python script
# does delete all the ratings and rois on all the images of user-1
# through user-40 and of trainer-1 in dataset "Condensation"
# The script also counts the number of the images in each
# "Condensation" dataset and
# warns if there is any other number than 32.
# Further the script unlinks all tags from the images
# in each "Condensation" dataset other than the tag
# with id 4236


import omero
from omero.gateway import BlitzGateway
from omero.rtypes import wrap


def list_objs(username, password, host):

    global mitosis_tags_nr
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()

    params = omero.sys.ParametersI()
    params.addString('username', username)
    query = "from Dataset where name='Condensation' \
             AND details.owner.omeName=:username"
    query_service = conn.getQueryService()
    dataset = query_service.findAllByQuery(query, params, conn.SERVICE_OPTS)
    dataset_id = dataset[0].getId().getValue()

    dataset = conn.getObject("Dataset", dataset_id)
    k = 0
    roi_service = conn.getRoiService()
    for image in dataset.listChildren():
        k += 1
        result = roi_service.findByImage(image.getId(), None)

        for roi in result.rois:
            roi_id = roi.getId().getValue()
            obj_ids_rois.append(roi_id)

        if not len(result.rois) == 0:
            value = 'Will delete %s ROIs on image \
                    %s of %s' % (len(result.rois), image.getId(), username)
            print value

        for ann in image.listAnnotations():
            if ann.OMERO_TYPE == omero.model.LongAnnotationI:
                obj_ids_rating.append(ann.getId())
                value = 'Will delete rating %s on image \
                        %s of %s' % (ann.getId(), image.getId(), username)
                print value

            if ann.OMERO_TYPE == omero.model.TagAnnotationI:
                if ann.getId() == 4236:
                    mitosis_tags_nr += 1
                else:
                    params2 = omero.sys.ParametersI()
                    params2.add('imageId', wrap(image.getId()))
                    params2.addId(ann.getId())
                    query = "select l.id from ImageAnnotationLink as \
                            l where l.parent.id=:imageId AND l.child.id=:id"
                    linkIds = query_service.projection(query, params2,
                                                       conn.SERVICE_OPTS)
                    for linkId in linkIds:
                        obj_ids_taglinks.append(linkId[0].getValue())
                        value = 'Will delete link %s on image \
                                %s of tag %s of ' % (linkId[0].getValue(),
                                                     image.getId(),
                                                     ann.getId(), username)
                        print value

    if not mitosis_tags_nr == 6:
        print 'WARNING mitosis_tags_nr is %s %s' % (mitosis_tags_nr, username)
    mitosis_tags_nr = 0
    if not k == 32:
        value = 'WARNING dataset %s of %s \
                has %s images' % (dataset_id, username, k)
        print value

    conn.close()


def delete_objs(username, password, host):
    conn = BlitzGateway(username, password, host=host, port=4064)
    conn.connect()
    print 'deleting', len(obj_ids_taglinks), ' tag links'
    if len(obj_ids_taglinks) > 0:
        conn.deleteObjects("ImageAnnotationLink", obj_ids_taglinks,
                           deleteAnns=True, deleteChildren=False, wait=True)
    print 'deleting %s rois' % len(obj_ids_rois)
    if len(obj_ids_rois) > 0:
        conn.deleteObjects("Roi", obj_ids_rois, deleteAnns=True,
                           deleteChildren=True, wait=True)
    print 'deleting %s ratings' % len(obj_ids_rating)
    if len(obj_ids_rating) > 0:
        conn.deleteObjects("LongAnnotation", obj_ids_rating, deleteAnns=True,
                           deleteChildren=False, wait=True)
    conn.close()

password = "password"
host = "outreach.openmicroscopy.org"
obj_ids_taglinks = []
obj_ids_rating = []
obj_ids_rois = []
mitosis_tags_nr = 0

for i in range(1, 41):
    username = "user-%s" % i
    list_objs(username, password, host)

trainer_name = "trainer-1"
list_objs(trainer_name, password, host)
delete_objs(trainer_name, password, host)
