#!/bin/bash
#
# This script deletes rnd settings on images in specified Datasets.
# Datasets can be specified either by name, such as
# DATASETNAME=svs bash delete_rendering.sh
# or by IDs (one or more, separated by comma), such as
# DATASETID=1868,2073 bash delete_rendering.sh

echo Starting
OMEROPATH=${OMEROPATH:-/opt/omero/server/OMERO.server/bin/omero}
PASSWORD=${PASSWORD:-ome}
HOST=${HOST:-outreach.openmicroscopy.org}
DATASETNAME=${DATASETNAME:-siRNAi-HeLa}
DATASETIDS=${DATASETID:-none}
NUMBER=${NUMBER:-50}
OMEUSER=${OMEUSER:-trainer-1}

$OMEROPATH login -u $OMEUSER -s $HOST -w $PASSWORD
if [ "$DATASETIDS" = "none" ]
then
    result=`$OMEROPATH hql --ids-only --limit 1000 --style plain -q --all "SELECT id from Dataset WHERE name = '$DATASETNAME'" | cut -f 2 -d , | tr '\n' ,`
    dataset_number=`echo $result | tr -cd , | wc -c | sed -e 's/[[:space:]]*//'`
    datasetids=`echo $result | sed -e 's/,$//'`
else
    datasetids=$DATASETIDS
    dataset_number="specified datasets"
fi
echo 'Deleting rnd settings on '"$dataset_number"' Datasets:' $datasetids
$OMEROPATH delete --report Dataset/RenderingDef:$datasetids
$OMEROPATH logout
echo Stopping
