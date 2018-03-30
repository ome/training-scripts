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

// Using the browser devtools to manipulate OMERO.figure is an experimental
// feature for developers.
// N.B.: Never paste untrusted code into your browser console.
//
// To be used as part of the FRAP workflow
// Expects map annotation on each image with key-value pairs
// [theT, intensity]
// for each time-point in the movie.
// This script loads map-annotations for namespace 'ns'
// and creates a label from the Value of the nth key-value pair
// where n = theT for that panel

// NB: This is not the most efficient solution since we load
// the same data many times for panels of the same image, but
// the code is a lot simpler like this.

figureModel.getSelected().forEach(function(p){
    var image_id = p.get('imageId');
    var ns = 'demo.simple_frap_data';
    var url = WEBINDEX_URL + "api/annotations/?type=map&image=" + image_id;
    url += '&ns=' + ns;
    var theT = p.get('theT');

    $.getJSON(url, function(data){
        // Use only the values from the first annotation
        var values = data.annotations[0].values;
        var labels = [{
                'text': "" + parseInt(values[theT][1]),
                'size': 12,
                'position': "topleft",
                'color': "ffffff"
            }]
        p.add_labels(labels);
    });
});
