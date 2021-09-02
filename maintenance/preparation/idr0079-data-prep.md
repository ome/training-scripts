
Workshop data preparation (idr0079)
===================================

This document details the steps to prepare data from idr0079 for use in an OMERO.parade
and OMERO.figure workshop. 


IDR data import
===============

For OME team, steps for in-place import of IDR data can be found at
https://docs.google.com/document/d/18cLSvUKVn8jEp7KSW7e48NvuxHT_mp3tyh98loTTOYY/edit

For other users, you will need to have Docker installed.
This container uses Aspera to download the data from EBI:

	$ docker run --rm -v /tmp:/data imagedata/download idr0079 . /data/

Clone https://github.com/IDR/idr0079-hartmann-lateralline and edit
```experimentA/idr0079-experimentA-filePaths.tsv```
to point ALL paths at the location of the data downloaded above.

If you don't want to use in-place import, comment out this line in
```experimentA/idr0079-experimentA-bulk.yml```:

	transfer: "ln_s"


Do the bulk import:

	$ cd experimentA/
	$ omero import --bulk idr0079-experimentA-bulk.yml


Add Map Annotations from IDR
============================

Use the script [idr_get_map_annotations.py](../scripts/idr_get_map_annotations.py) with the ID of
the 'idr0079-hartmann-lateralline/experimentA' Project created above and the corresponding
Project on IDR (1102):

	$ python idr_get_map_annotations.py username password Project:1102 Project:1301 --server localhost

This will get map annotations from all Images in `idr0079-hartmann-lateralline/experimentA` and
create identical map annotations on the corresponding Images.


Rename Channels from Map Annotations
====================================

We can now use the map annotations to rename channels on all images.
Run the [channel_names_from_maps.py](../scripts/channel_names_from_maps.py)
script on the local data, using the local Project ID.
We are using the `stain` to name channels, e.g. `NLStdTomato`, and also adding
map annotations for individual channels to use for label creation in OMERO.figure:

    $ python channel_names_from_maps.py username password 1301 --server localhost --use_stain --add_map_anns


Manual Annotations and Rendering Settings
=========================================

Add 5* rating to 1 image each from membranes_actin, membranes_cisgolgi, membranes_nuclei, membranes_recendo.

Set rendering settings: Channel-1: Green, Channel-2: Red. Maybe adjust levels too.

Set Pixel Sizes
==========================================

See the commands used for IDR at [idr0079_voxel_sizes.sh]
(https://github.com/IDR/idr0079-hartmann-lateralline/blob/master/scripts/idr0079_voxel_sizes.sh)
and run these commands on the local server, using the appropriate Dataset IDs, at least
for the Datasets you wish to use.

Copy Masks to Polygons
======================

The idr0079 images in IDR have Masks, but we want to use Polygons in OMERO.figure.
Use the [copy_masks_2_polygons.py](../scripts/copy_masks_2_polygons.py) script to
convert. NB: requires `skimage`. We can process an Image or a Dataset at a time:

    # login to IDR
    $ omero login

    # copy from IDR e.g Dataset:1 to local server TARGET e.g. Dataset:2
    $ python copy_masks_2_polygons.py username password server Dataset:1 Dataset:2

Create OMERO.tables
===================

We use the tsv files in https://github.com/IDR/idr0079-hartmann-lateralline to create
an OMERO.table on the Project (for use with OMERO.parade), with one row per Image, summarising the stats for all the
ROIs in that Image. This uses [idr0079_csv_to_table_on_project.py](../scripts/idr0079_csv_to_table_on_project.py)

Clone the `idr0079-hartmann-lateralline` github repo, then:

    $ cd idr0079-hartmann-lateralline
    $ python /path/to/training-scripts/maintenance/scripts/csv_to_table_on_project.py

We then create an OMERO.table on each Image that has ROIs added above, using
the `_other_measurements.tsv` table for each Image.
Use the optional `--name NAME` to run on a single named Image:

    $ cd idr0079-hartmann-lateralline
    # process ALL raw images (use --name NAME to process 1 image)
    $ python scripts/csv_to_roi_table.py _other_measurements.tsv
