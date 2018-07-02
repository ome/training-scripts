
Workshop prep:

General prep
============

 - Open gdoc or PDF walkthrough (with link to script)
 - Turn off e-mail & slack notifications!


OMERO intro - webclient
=======================

 - Add Prometaphase & Anaphase tags to siRNAi images
 - Create Metaphase tag to be added in workshop
 - Create rendering settings for some users on trainer-1's images

Cleanup
-------

 - Remove metaphase tag
 - Remove any ROIs added


Cell Profiler
=============

 - Check that pre-calculated OMERO.table is attached to plate
 - Run the analysis against a different plate so incomplete tables don't confuse Parade

Cleanup
-------

 - Delete any tables from previous run-throughs (but not the complete table)


Orbit segmentation
==================

 - Delete any Orbit model annotation(s) (not linked to Images)
 - Delete Orbit ROI annotation on Image
 - Delete ROIs on Image


Raw data analysis
=================

 - Check: idr0021 imported with 10 Datasets
 - Channels named and IDR map annotations present
 - ROIs added to all Datasets except first
 - Map Annotations from ROIs added to all Datasets except first
 - ROIS on ALL Datasets previously analysed -> Table on Project
 - Ratings added to at least one image in every Dataset
 - batch_roi_export_to_table script is installed on server
 - Check Jupyter notebook is available: https://idr-analysis.openmicroscopy.org/training/hub/tmplogin

Cleanup
-------

 - Delete ROIs from 1st Dataset
 - Delete summpary ROI Map annotations from 1st Dataset
 - Delete Plots attachments on Project


Metadata Handling
=================

 - idr0021 Project has Bulk annotations Table
 - OMERO.mapr config set for Gene and Key-Value
 - Figure 2 Aurora-B figure created
 - Only 1 Project idr0021


Cleanup
-------

 - Delete any Map Annotations added during the workshop
 - Delete any Tags added during workshop
 - Delete any figures created during workshop