
Download and import idr0021 data
================================

To download IDR data and re-import elsewhere....
(See gdoc https://docs.google.com/document/d/1TmBZ43_yhiO3AOua8oMk4mPWKWJtpeYNc2KLP17h-1I/edit# )

	$ ssh idr0-slot3.openmicroscopy.org

	# another terminal
	$ rsync -rvP --progress wmoore@idr0-slot3.openmicroscopy.org:/uod/idr/filesets/idr0021-lawo-pericentriolarmaterial/Raw-files/ .


Edit ```idr-metadata/idr0021-lawo-pericentriolarmaterial/experimentA/idr0021-experimentA-filePaths.tsv```
to point ALL paths at download location.
e.g.
```Dataset:name:CDK5RAP2-C	/Users/wmoore/Desktop/IDR/data/idr0021/CDK5RAP2-C/Centrin_PCNT_Cep215_20110506/Centrin_PCNT_Cep215_20110506_Fri-1545_0_SIR_PRJ.dv```


If you don't want to in-place import, comment out this line in idr-metadata/bulk.yml:

	transfer: "ln_s"


Do the bulk import:

	$ cd idr-metadata/idr0021-lawo-pericentriolarmaterial/experimentA/
	$ omero import --bulk idr0021-experimentA-bulk.yml


In the webclient, create a Project 'idr0021' and add the 10 new Datasets created above.


Add Map Annotations from IDR
============================

Edit the ```maintenance/idr_get_map_annotations.py``` with the ID of the 'idr0021' Project created
above. This will get map annotations from all images in the [idr0021](http://idr.openmicroscopy.org/webclient/?show=project-51) and create identical map annotations on the corresponding images.

We can then use these map annotations to rename channels on all images.
Edit the ```project_id``` and run the ```maintenance/channel_names_from_maps.py``` script on the local data.


Analyse in Fiji/ImageJ and save ROIs in OMERO
=============================================

Run the ```jython/analyse_particles_for_another_user.jy``` in Fiji with the
appropriate credentials on a Dataset at a time, updating the dataset_id each time.

This will Analyse Particles and create ROIs on all channels of each Image.

NB: we may also have this script create Tables on Images or Datasets as an example
of how to create Tables from ImageJ, but these won't be used in the workshop.


Create Map Annotations and Table from ROIs
==========================================

First we need to delete an outlier Image that causes
[problems in OMERO.parade](https://github.com/ome/omero-parade/issues/26).

Delete NEDD1ab_NEDD1141_I_012_SIR. This image is the only Z-stack and no blobs are found
so the Polygon created covers the whole plane (possibly because Fiji analyses the first Z-section only?).

The ```python/server/batch_roi_export_to_table.py``` script needs to be installed on the
server. Run this from the webclient, selecting the ```idr0021``` Project to create a
single Table on this Project, that has rows for all Images in the Project.

This script uses the Channel Names to pick a Channel that matches the Dataset name
for each Image. This is the Channel that needs to be analysed and is used to filter Shapes created
by Fiji.

This script also creates Map annotations and can create a CSV (could be shown in workshop).
Options for these are handled by checkboxes at the bottom of the script dialog.

The data is now ready to be presented in a workshop and analysed with ```OMERO.parade```.
