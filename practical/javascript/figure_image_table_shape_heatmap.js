
//
// Copyright (C) 2021 University of Dundee & Open Microscopy Environment.
// All rights reserved.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

// Using the browser devtools to manipulate OMERO.figure is an experimental
// feature for developers.
// N.B.: Never paste untrusted code into your browser console.
//
// This script requires an OMERO.table linked to the Image
// with a 'Shape' column and another numerical named column (edit below).
// Select panel with Shapes added from OMERO (shape.id should be in the table).
// This colors each Shape according to a heatmap for the named column.

async function shapeData(panel) {
    const shapeIds = panel.get("shapes").map(s => s.id).filter(id => id > 0);
    console.log('shapeIds', shapeIds);
    let vals_by_shape = {};
    for (let i = 0; i < shapeIds.length; i++) {
        // Load one at a time - more reliable
        let url = `/webgateway/table/Image/${panel.get('imageId')}/query/?query=Shape-${shapeIds[i]}`;
        let r = await fetch(url).then(rsp => rsp.json());
        let colIndex = r.data?.columns?.indexOf("Centroids_RAW_X");
        let shapeIndex = r.data?.columns?.indexOf("Shape");
        if (colIndex && shapeIndex && r.data?.rows.length > 0) {
            console.log("Value", r.data.rows[0][colIndex]);
            vals_by_shape[r.data.rows[0][shapeIndex]] = r.data.rows[0][colIndex];
        }
    };
    // Once all loaded, we can calculate range and assign colours to shapes
    const values = Object.values(vals_by_shape);
    let minVal = Math.min(...values);
    let valRange = Math.max(...values) - minVal;
    console.log('min, range', minVal, valRange);
    const new_shapes = panel.get("shapes").map(shape => {
        // hide any shapes we don't have data for
        if (!vals_by_shape[shape.id]) return { ...shape, strokeWidth: 0.01 };
        let value = (vals_by_shape[shape.id] - minVal) / valRange;
        let red = parseInt(value * 255).toString(16);
        let blue = (255 - parseInt(value * 255)).toString(16);
        red = red.length == 1 ? `0` + red : red;
        blue = blue.length == 1 ? `0` + blue : blue;
        return { ...shape, strokeColor: `#${red}00${blue}`, strokeWidth: 5 }
    });
    panel.set('shapes', new_shapes);
}
figureModel.getSelected().forEach(shapeData);
