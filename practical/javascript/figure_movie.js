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
// Creates a Movie layout for selected images
// with rows of panels showing time-points incremented by tIncrement
// and wrapped into multiple rows according to columnCount.


figureModel.panels.getSelected().forEach(p => {
    var j = p.toJSON();
    var left = j.x;
    var top = j.y;
    var columnCount = 10;
    var tIncrement = 2;
    var panelCount = 1;
    for (var t=tIncrement; t<j.sizeT; t+=tIncrement){
        // offset to the right each time we create a new panel
        j.x = left + ((panelCount % columnCount) * j.width * 1.05);
        j.y = top + (parseInt(panelCount / columnCount) * j.height * 1.05);
        panelCount++;
        // Increment T
        j.theT = t;
        // create new panel from json
        figureModel.panels.create(j);
    };
});