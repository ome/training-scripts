#!/usr/bin/env python

import pandas
import csv
import mimetypes
import os

import omero.clients
import omero.cli
import omero
from omero_metadata.populate import ParsingContext
from omero.util.metadata_utils import NSBULKANNOTATIONSRAW


project_name = "idr0079-hartmann-lateralline/experimentA"

# NB: Need to checkout https://github.com/IDR/idr0079-hartmann-lateralline
# and run this from in idr0079-hartmann-lateralline directory OR update
# tables_path to point to that repo.

tables_path = "./experimentA/idr0079_experimentA_extracted_measurements/%s/"
# Lots of tsv files to choose from...
# e.g. Tissue Frame Of Reference Primary Component Analysis measured:
# tables_path += "%s_shape_TFOR_pca_measured.tsv"
# e.g. Other Measurements:
tables_path += "%s_other_measurements.tsv"


def get_omero_col_type(col_name):
    """Returns s for string, d for double, l for long/int"""
    if "Name" in col_name:
        return "s"
    return "d"


def populate_metadata(project, dict_data):

    csv_columns = list(dict_data[0].keys())
    # Dataset Name, Image Name, PC...
    csv_columns.sort()
    # header s,s,d,d,d,...
    col_types = [get_omero_col_type(name) for name in csv_columns]
    header = f"# header {','.join(col_types)}\n"
    print('header', header)
    csv_file = "other_measurements_summaries.csv"
    print("writing to", csv_file)
    with open(csv_file, 'w') as csvfile:
        # header s,s,d,l,s
        csvfile.write(header)
        writer = csv.DictWriter(csvfile, fieldnames=csv_columns)
        writer.writeheader()
        for data in dict_data:
            writer.writerow(data)

    # Links the csv file to the project and parses it to create OMERO.table
    mt = mimetypes.guess_type(csv_file, strict=False)[0]
    fileann = conn.createFileAnnfromLocalFile(
        csv_file, mimetype=mt, ns=NSBULKANNOTATIONSRAW
    )
    fileid = fileann.getFile().getId()
    project.linkAnnotation(fileann)
    client = project._conn.c
    ctx = ParsingContext(
        client, project._obj, fileid=fileid, file=csv_file, allow_nan=True
    )
    ctx.parse()


def process_image(image):

    # Read csv for each image
    image_name = image.name
    table_pth = tables_path % (image_name, image_name)
    print('table_pth', table_pth)
    df = pandas.read_csv(table_pth, delimiter="\t")

    cols = ["Source Name",
        "Cell ID",
        "Centroids RAW X",
        "Centroids RAW Y",
        "Centroids RAW Z",
        "Centroids TFOR X",
        "Centroids TFOR Y",
        "Centroids TFOR Z",
        "Longest Extension",
        "Major Axis Length",
        "Major/Medium Axis Eccentricity",
        "Major/Minor Axis Eccentricity",
        "Medium Axis Length",
        "Medium/Minor Axis Eccentricity",
        "Minor Axis Length",
        "Orientation along X",
        "Orientation along Y",
        "Orientation along Z",
        "Roundness (Smoothness)",
        "Sphericity",
        "Surface Area",
        "Volume",
        "X Axis Length",
        "Y Axis Length",
        "Y/X Aspect Ratio",
        "Z Axis Length",
        "Z/X Aspect Ratio",
        "Z/Y Aspect Ratio"]

    summary = df.describe()
    data = {'count': summary['Cell ID']['count']}
    # get: RAW_Y_Range, RAW_X_Range, RAW_Z_Range
    for dim in ['X', 'Y', 'Z']:
        min_val = summary[f'Centroids RAW {dim}']['min']
        max_val = summary[f'Centroids RAW {dim}']['max']
        data[f'RAW_{dim}_Range'] = max_val - min_val
        min_tfor = summary[f'Centroids TFOR {dim}']['min']
        max_tfor = summary[f'Centroids TFOR {dim}']['max']
        data[f'TFOR_{dim}_Range'] = max_tfor - min_tfor
    
    # Mean_Sphericity, Mean_Volume, Mean_Z_Axis_Length, Mean_X_Axis_Length
    for col_name in ['Sphericity', 'Volume', 'X Axis Length', 'Y Axis Length', 'Z Axis Length']:
        value = summary[col_name]['mean']
        data[f'Mean_{col_name}'.replace(' ', '_')] = value

    # For PC .tsf
    # columns are named "PC 1", "PC 2" etc...
    # for pc_id in range(1,4):
    #     for stat in ['count', 'mean', 'min', 'max', 'std']:
    #         # No spaces in OMERO.table col names!
    #         omero_table_colname = f"PC{pc_id}_{stat}"
    #         value = summary[f'PC {pc_id}'][stat]
    #         data[omero_table_colname] = value

    return data


def main(conn):

    project = conn.getObject("Project", attributes={"name": project_name})
    print("Project", project.id)
    conn.SERVICE_OPTS.setOmeroGroup(project.getDetails().group.id.val)
    data_rows = []
    # For each Image in Project, open the local CSV and summarise to one row
    for dataset in project.listChildren():
        for image in dataset.listChildren():
            # ignore _seg images etc.
            if '_' in image.name:
                continue
            dict_data = process_image(image)
            dict_data['Dataset Name'] = dataset.name
            dict_data['Image Name'] = image.name
            data_rows.append(dict_data)

    populate_metadata(project, data_rows)

# Usage:
# cd idr0079-hartmann-lateralline
# python scripts/csv_to_table_on_project.py

if __name__ == "__main__":
    with omero.cli.cli_login() as c:
        conn = omero.gateway.BlitzGateway(client_obj=c.get_client())
        main(conn)
        conn.close()
