
# -----------------------------------------------------------------------------
#  Copyright (C) 2018 University of Dundee. All rights reserved.
#
#
#  This program is free software; you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation; either version 2 of the License, or
#  (at your option) any later version.
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
#  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# ------------------------------------------------------------------------------

# Delete all file attachments and ROIs for a particular dataset (specified by
# name) for all users user-1 to user-50

import argparse
import omero
from omero.gateway import BlitzGateway


def getDataset(conn, username, datasetName):
  params = omero.sys.ParametersI()
  params.addString('username', username)
  params.addString('datasetname', datasetName)
  query = "from Dataset where name=:datasetname \
           AND details.owner.omeName=:username"
  query_service = conn.getQueryService()
  dataset = query_service.findByQuery(query, params,
                                      conn.SERVICE_OPTS)
  datasetId = dataset.getId().getValue()
  dataset = conn.getObject("Dataset", datasetId)
  return dataset


def deleteFileAnnotations(conn, dataset):
  ann_ids = []
  for a in dataset.listAnnotations():
    if a.OMERO_TYPE == omero.model.FileAnnotationI:
      ann_ids.append(a.id)

  if len(ann_ids) > 0:
    print "Deleting %s file attachments..." % len(ann_ids)
    conn.deleteObjects('Annotation', ann_ids, wait=True)


def deleteROIs(conn, dataset):
  roi_service = conn.getRoiService()
  for image in dataset.listChildren():
    result = roi_service.findByImage(image.getId(), None,
                                     conn.SERVICE_OPTS)
    if result is not None:
      roi_ids = [roi.id.val for roi in result.rois]
      if len(roi_ids) > 0:
        print "Deleting %s ROIs..." % len(roi_ids)
        conn.deleteObjects("Roi", roi_ids, wait=True)


def run(password, datasetName, host, port):
  for i in range(1, 51):
    username = "user-%s" % i
    print username
    conn = BlitzGateway(username, password, host=host, port=port)
    try:
      conn.connect()
      dataset = getDataset(conn, username, datasetName)
      deleteFileAnnotations(conn, dataset)
      deleteROIs(conn, dataset)
    except Exception as exc:
      print "Error: %s" % str(exc)
    finally:
      conn.close()


def main(args):
    parser = argparse.ArgumentParser()
    parser.add_argument('password')
    parser.add_argument('datasetName')
    parser.add_argument('--server', default="outreach.openmicroscopy.org",
                        help="OMERO server hostname")
    parser.add_argument('--port', default=4064, help="OMERO server port")
    args = parser.parse_args(args)
    run(args.password, args.datasetName, args.server, args.port)


if __name__ == '__main__':
  import sys
main(sys.argv[1:])
