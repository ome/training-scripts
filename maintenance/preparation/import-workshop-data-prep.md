
Import Workshop data 
====================

This document details the steps to prepare data for a workshop demonstrating some
import options followed by rendering of the imported data and metadata import.

Import options data
===================

For the OMERO.insight import, we use a [DICOM dataset](https://zenodo.org/record/16956#.Wt-UNtPwbdc).

For the in-place import, we use the first image from the [SVS dataset](https://downloads.openmicroscopy.org/images/SVS/).

For the bulk import, we use the [idr0021](https://idr.openmicroscopy.org/webclient/?show=project-51) data, prepared and imported in the same way as in the metadata workshop [idr0021-data-prep.md](idr0021-data-prep.md). The in-place bulk import of idr0021 into an OMERO.server takes approximately 20 minutes in our setup.


Rendering files
===============

Perform the rendering steps on the first two datasets of the data imported in the bulk import (see above), CDK5RAP2-C and CENT2. These two datasets will import within the first 3 minutes of the import.

The files used for the rendering settings are [renderingdef.yml](renderingdef.yml) and [renderingdef2.yml](renderingdef2.yml). These files contain the rendering definitions of color, min, max and channel names. See the workshop walkthrough [link-to-walkthrough] for how these files are used in the [omero-cli-render](https://pypi.org/project/omero-cli-render/) plugin.

To change the rendering settings in a batch manner, use the bash script [apply_rnd_settings_as.sh](../scripts/apply_rnd_settings_as.sh). The [renderingMapping.tsv](renderingMapping.tsv) file lists all the datasets in the idr0021 study with the appropriate rendering definition file for that dataset. The file renderingMapping.tsv is consumed by the bash script mentioned above. You also need the files [renderingdef.yml](renderingdef.yml) and [renderingdef2.yml](renderingdef2.yml) for this step. They are referred to in the renderingMapping.tsv. Either put the renderingdef.yml and renderingdef2.yml in the same folder as renderingMapping.tsv or edit the renderingMapping.tsv and add the full path to the files.

The rendering settings and channel names achieved by the manual steps above or the script are not intended to be optimal or accurate for the images. The goal is to achieve an optically striking change on the thumbnails in the User Interface after the single command using the [omero-cli-render](https://pypi.org/project/omero-cli-render/) plugin or the batch script was applied to demonstrate the possibilities of the plugin.


Metadata Import CLI
===================

Images used for this step are from idr0021 (see above). Files used fot the metadata import are [idr0021-experimentA-annotation.csv](https://github.com/IDR/idr0021-lawo-pericentriolarmaterial/blob/master/experimentA/idr0021-experimentA-annotation.csv) and [idr0021-bulkmap-config.yml](https://github.com/IDR/idr0021-lawo-pericentriolarmaterial/blob/master/experimentA/idr0021-experimentA-bulkmap-config.yml).
