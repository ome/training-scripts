/*
 * -----------------------------------------------------------------------------
 *  Copyright (C) 2018 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 */

/*
 * This Groovy script shows how to open an OMERO Image in ImageJ
 * using Bio-Formats importer. In this case the image is not downloaded
 * before opening. Bi0-Formats requests directly the planes from OMERO.
 * Use this script in the Scripting Dialog of Fiji (File > New > Script).
 * Select Groovy as language in the Scripting Dialog.
 * Error handling is omitted to ease the reading of the script but this
 * should be added if used in production to make sure the services are closed
 * Information can be found at
 * https://docs.openmicroscopy.org/latest/omero5/developers/Java.html
 */

 // OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.TransferFacility
import omero.log.SimpleLogger

import ij.IJ


// Setup
// =====

// OMERO Server details
HOST = "outreach.openmicroscopy.org"
PORT = 4064
// parameters to edit
image_id = "1001"
USERNAME = "username"
PASSWORD = "password"


def connect_to_omero() {
    "Connect to OMERO"

    credentials = new LoginCredentials()
    credentials.getServer().setHostname(HOST)
    credentials.getServer().setPort(PORT)
    credentials.getUser().setUsername(USERNAME.trim())
    credentials.getUser().setPassword(PASSWORD.trim())
    simpleLogger = new SimpleLogger()
    gateway = new Gateway(simpleLogger)
    gateway.connect(credentials)
    return gateway

}

def open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, image_id) {
    "Open the image using the Bio-Formats Importer"

    StringBuffer options = new StringBuffer()
    options.append("location=[OMERO] open=[omero:server=")
    options.append(HOST)
    options.append("\nuser=")
    options.append(USERNAME.trim())
    options.append("\nport=")
    options.append(PORT)
    options.append("\npass=")
    options.append(PASSWORD.trim())
    options.append("\ngroupID=")
    options.append(group_id)
    options.append("\niid=")
    options.append(image_id)
    options.append("] ")
    options.append("windowless=true")
    IJ.runPlugIn("loci.plugins.LociImporter", options.toString())

}

//Connect to OMERO
gateway = connect_to_omero()
exp = gateway.getLoggedInUser()
group_id = exp.getGroupId()
gateway.disconnect()

//Open the image
open_image_plus(HOST, USERNAME, PASSWORD, PORT, group_id, String.valueOf(image_id))
