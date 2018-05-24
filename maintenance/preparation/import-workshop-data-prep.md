
Import Workshop data 
====================

This document details the steps to prepare data for a workshop demonstrating
import options followed by rendering and metadata import.

Import options data
===================

For the OMERO.insight import, we use the DICOM dataset, downloaded from [https://zenodo.org/record/16956#.Wt-UNtPwbdc].

For the in-place import (single), we use the first image from the SVS dataset, downloaded from [http://downloads.openmicroscopy.org/images/SVS/].

For the bulk import, we use the idr0021 data [https://idr.openmicroscopy.org/webclient/?show=project-51], prepared and imported in the same way as in the metadata workshop [https://github.com/ome/training-scripts/blob/master/maintenance/preparation/idr0021-data-prep.md]. The in-place bulk import of idr0021 into an OMERO.server takes approximately 20 minutes in our setup.


Rendering files
===============

Perform the rendering steps on first two datasets of the data imported in the bulk import (see above), CDK5RAP2-C and CENT2. These two datasets will import within first 3 minutes of the import.

The files used for the rendering settings are renderingdef.yml [add-link-to-file] and renderingdef2.yml [add-link-to-file]. These files contain the rendering defititions of color, min, max and channel names. See the workshop walkthrough [link-to-walkthrough] for how these files are used in the omero-cli-render plugin [https://pypi.org/project/omero-cli-render/].

For the rendering settings changes in batch manner use bash script [link-to-bash-script-of-jm]. The renderingMapping.tsv file [link-to-file] lists all the images in the idr0021 study with the appropriate rendering definition file for such study. renderingMapping.tsv is consumed by the bash script [link-to-bash-script-of-jm]. You also need the renderingdef.yml [add-link-to-file] and renderingdef2.yml [add-link-to-file] for this step, which are referred to in the renderingMapping.tsv.

The rendering and channel names achieved by the manual steps above or the script is not intended to be optimal or accurate for the images. The goal is to achieve an optically striking change on the image thumbnails in the user interface after the single command using the omero-cli-render plugin or the batch script was applied to demonstrate the possibilities of the plugin.


Metadata Import CLI
===================

Images used for this step are from idr0021 (see above). Files used fot the metadata import are idr0021-...-annotation.csv [link-to-file-in-idr] and idr0021-...-config.yml [link-to-file-in-idr].
