//
// Copyright (C) 2017 University of Dundee & Open Microscopy Environment.
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


// Adds labels to image panels in OMERO.figure
// using Key Value pairs (map annotations) on the images.
//
// Using the browser devtools to manipulate OMERO.figure is an experimental
// feature for developers.
// N.B.: Never paste untrusted code into your browser console.
// To use, open the OMERO.figure file in your browser.
// Select the image panels you want to add labels to.
// Open devtools in Chrome / Firefox and select Console tab.
// Copy the code below into the console.
// This will load map annotations JSON for each selected image and create labels.
// Can observe under the Network tab in devtools to see JSON loaded.

figureModel.getSelected().forEach(function(p){
    var image_id = p.get('imageId');
    var url = WEBINDEX_URL + "api/annotations/?type=map&image=" + image_id;

    $.getJSON(url, function(data){
        data.annotations.forEach(function(a){
            var labels = a.values.map(function(keyValue){
                return {
                    'text': keyValue.join(": "),
                    'size': 8,
                    'position': "topleft",
                    'color': "ffffff"
                }
            });
            p.add_labels(labels);
        });
    });
});
