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
// Creates a "Split View" layout from selected panels

figureModel.getSelected().forEach(p => {
    var j = p.toJSON();
    for (var c=0; c<j.channels.length; c++){
        // offset to the right each time we create a new panel
        j.x = j.x + (j.width * 1.05);
        // turn all channels off except for the current index
        j.channels = j.channels.map((ch, i) => {
            var newc = Object.assign({}, ch);
            newc.active = i === c;
            return newc;
        });
        // create new panel from json
        figureModel.panels.create(j);
    };
});
