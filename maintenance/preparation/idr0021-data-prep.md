
Workshop data preparation (idr0021)
===================================

This document details the steps to prepare data from IDR and elsewhere for a workshop demonstrating
analysis with Fiji, usage of Map Annotations and OMERO.tables and filtering with OMERO.parade.
We use IDR0021, which is a Project containing 10 Datasets with a total of ~400 Images.


Download IDR data
=================

You will need to have Docker installed. This container uses Aspera to download the data from EBI (244 MB):

	$ docker run --rm -v /tmp:/data imagedata/download idr0021 . /data/


Prepare IDR-metadata and import
===============================

Clone https://github.com/IDR/idr0021-lawo-pericentriolarmaterial and edit
```experimentA/idr0021-experimentA-filePaths.tsv```
to point ALL paths at the location of the data downloaded above.
e.g.
```Dataset:name:CDK5RAP2-C	/full/path/to/data/CDK5RAP2-C/Centrin_PCNT_Cep215_20110506/Centrin_PCNT_Cep215_20110506_Fri-1545_0_SIR_PRJ.dv```


If you don't want to use in-place import, comment out this line in
```experimentA/idr0021-experimentA-bulk.yml```:

	transfer: "ln_s"


Do the bulk import:

	$ cd experimentA/
	$ path/to/omero import --bulk idr0021-experimentA-bulk.yml


In the webclient, create a Project 'idr0021' and add the 10 new Datasets created above.


Add Map Annotations from IDR
============================

Run the script [idr_get_map_annotations.py](../scripts/idr_get_map_annotations.py) with the ID of the 'idr0021' Project created
above. See the script for usage details. This will get map annotations from all images in the [idr0021](https://idr.openmicroscopy.org/webclient/?show=project-51) and create identical map annotations on the corresponding images.


Rename Channels from Map Annotations
====================================

We can now use the map annotations to rename channels on all images.
Run the [channel_names_from_maps.py](../scripts/channel_names_from_maps.py)
script on the local data, passing in the `Project ID`.


Analyse in Fiji and save ROIs in OMERO
======================================

First we need to delete an Image that is the only Z-stack and is analyzed differently from the others:
```NEDD1ab_NEDD1141_I_012_SIR.dv``` in Dataset ```NEDD1-C1```.

Run [idr0021.groovy](https://github.com/ome/omero-guide-fiji/blob/master/scripts/groovy/idr0021.groovy) in Fiji with the
appropriate credentials on the `idr0021` Project.

This will Analyse Particles and create ROIs on all channels of each Image, using Channel names to pick the correct
Channel for each Image.

This script also creates a summary OMERO.table on the `idr0021` Project, named `Summary_from_Fiji`.
This can be seen under the `Attachments` tab for the Project, with an `eye` icon that will open the table.
The table has one row per image containing measurements results
e.g. the area of the bounding box of the biggest ROI.
The output is also saved as a CSV file and linked to the Project.


Delete ROIs and Map annotations for 1 Dataset
=============================================

If you wish to remove ROIs from all Images in a Dataset so we can show them being
created in the workshop use the Dataset ID:

	$ omero delete Dataset/Image/Roi:DATASET_ID

If you wish to do this for all users, using the Dataset name for each user, use:

 - [delete_ROIs.py](../scripts/delete_ROIs.py)

The data is now ready to be presented in a workshop and analysed with ```OMERO.parade```.


Plate data
==========

Download Plate ``INMAC384-DAPI-CM-eGFP_59223_1`` from the OME [HCS sample images](https://downloads.openmicroscopy.org/images/HCS/INCELL2000/), using ``wget`` to download all the files (9.8 GB, 1158 items) into a new directory
and import this:

	$ wget -r --no-parent --execute robots=off --no-directories --directory-prefix=INCELL2000/INMAC384-DAPI-CM-eGFP_59223_1 https://downloads.openmicroscopy.org/images/HCS/INCELL2000/INMAC384-DAPI-CM-eGFP_59223_1/

	$ path/to/omero import INMAC384-DAPI-CM-eGFP_59223_1

Alternatively, you can copy a Plate from IDR, copying only the first Z and T index of each Image, using
[idr_copy_plate.py](../scripts/idr_copy_plate.py). This will ask for login details of the server where you 
wish to copy the Plate. IDR [Plate 422](http://idr.openmicroscopy.org/webclient/?show=plate-422) is a
suitable Plate:

	$ python maintenance/scripts/idr_copy_plate.py 422

We need to populate an OMERO.table on the Plate to demonstrate filtering with
OMERO.parade. For that we will use the [channel_minmax_to_table.py](../scripts/channel_minmax_to_table.py).
Run the command line script with the Plate ID:

	$ python maintenance/scripts/channel_minmax_to_table.py PLATE_ID

This should create an Annotation on the Plate called ``Channels_Min_Max_Intensity``.
