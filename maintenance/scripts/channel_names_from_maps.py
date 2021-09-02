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

# Script uses map annotations on each Image to rename channels

import argparse
from omero.gateway import BlitzGateway, MapAnnotationWrapper


NAMESPACE = "openmicroscopy.org/omero/bulk_annotations"
MAP_KEY = "Channels"


def create_map_ann(conn, obj, key_value_data):
    map_ann = MapAnnotationWrapper(conn)
    map_ann.setValue(key_value_data)
    map_ann.setNs('from.channels.keyvaluepair')
    map_ann.save()
    obj.linkAnnotation(map_ann)


def run(args):
    username = args.username
    password = args.password
    project_id = args.project_id
    host = args.server
    port = args.port
    use_stain = args.use_stain
    add_map_anns = args.add_map_anns

    token_index = 0 if use_stain else 1

    conn = BlitzGateway(username, password, host=host, port=port)
    try:
        conn.connect()
        project = conn.getObject("Project", project_id)

        for dataset in project.listChildren():
            print("\n\nDataset", dataset.id, dataset.name)
            for image in dataset.listChildren():

                print("Image", image.id, image.name)
                ann = image.getAnnotation(NAMESPACE)
                if ann is None:
                    print(" No annotation found")
                    continue
                keys = ann.getValue()
                values = [kv[1] for kv in keys if kv[0] == MAP_KEY]
                if len(values) == 0:
                    print(" No Key-Value found for key:", MAP_KEY)
                channels = values[0].split("; ")
                print("Channels", channels)
                name_dict = {}
                key_value_pairs = []
                for c, ch_name in enumerate(channels):
                    tokens = ch_name.split(":")
                    if add_map_anns and len(tokens) > 1:
                        key_value_pairs.extend(
                            [["Ch%s_Stain" % c, tokens[0]],
                             ["Ch%s_Label" % c, tokens[1]]]
                        )
                    if len(tokens) > token_index:
                        label = tokens[token_index]
                    else:
                        label = ch_name
                    name_dict[c + 1] = label
                conn.setChannelNames("Image", [image.id], name_dict,
                                     channelCount=None)
                if len(key_value_pairs) > 0:
                    create_map_ann(conn, image, key_value_pairs)
    except Exception as exc:
        print("Error while changing names: %s" % str(exc))
    finally:
        conn.close()


def main(args):
    parser = argparse.ArgumentParser()
    parser.add_argument('username')
    parser.add_argument('password')
    parser.add_argument('project_id')
    parser.add_argument(
        '--use_stain', action='store_true',
        help="""Map Ann Channels are in the form stain:label, e.g. DAPI:DNA.
If use_stain, channels will be named with the stain instead of the label""")
    parser.add_argument(
        '--add_map_anns', action='store_true',
        help="""Create new Map Anns of the form
Ch1_Stain:DAPI, Ch1_Label:DNA etc using the Channels Key-Value Pair""")
    parser.add_argument('--server', default="workshop.openmicroscopy.org",
                        help="OMERO server hostname")
    parser.add_argument('--port', default=4064, help="OMERO server port")
    args = parser.parse_args(args)
    run(args)


if __name__ == '__main__':
    import sys
    main(sys.argv[1:])
