% Copyright (C) 2018 University of Dundee & Open Microscopy Environment.
% All rights reserved.
%
% This program is free software; you can redistribute it and/or modify
% it under the terms of the GNU General Public License as published by
% the Free Software Foundation; either version 2 of the License, or
% (at your option) any later version.
%
% This program is distributed in the hope that it will be useful,
% but WITHOUT ANY WARRANTY; without even the implied warranty of
% MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
% GNU General Public License for more details.
%
% You should have received a copy of the GNU General Public License along
% with this program; if not, write to the Free Software Foundation, Inc.,
% 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

% Detect cells using image segmentation
% see https://www.mathworks.com/examples/image/mw/images-ex64621327-detecting-a-cell-using-image-segmentation
% The shapes are saved as polylines.

host='outreach.openmicroscopy.org';
% To be modified
user='USERNAME';
password='PASSWORD';
datasetId = 23953;

client = loadOmero(host);
client.enableKeepAlive(60);
% Create an OMERO session
session = client.createSession(user, password);
% Initiliaze the service used to save the Regions of Interest (ROI)
iUpdate = session.getUpdateService();
dataset = getDatasets(session, datasetId, true);
images = toMatlabList(dataset.linkedImageList);
% Iterate through the images
datasetName = dataset.getName().getValue();
disp(datasetName);
values = zeros(2, numel(images));
for i = 1 : numel(images)
    image = images(i);
    imageId = image.getId().getValue();
    % Load the channels information to determine the channel to analyze
    channels = loadChannels(session, image);
    channelIndex = 0;
    for j = 1 : numel(channels)
        channel = channels(j);
        channelId = channel.getId().getValue();
        channelName = channel.getLogicalChannel().getName();
        % Determine the index of the channel to analyze
        if contains(char(datasetName), char(channelName))
            channelIndex = j;
            break
        end
    end
    % Load the plane, OMERO index starts at 0. sizeZ and SizeT = 1
    plane = getPlane(session, image, 0, channelIndex, 0);
    [~, threshold] = edge(plane, 'sobel');
    fudgeFactor = .5;
    BWs = edge(plane,'sobel', threshold * fudgeFactor);
    figure, imshow(BWs), title('binary gradient mask');
    se90 = strel('line', 3, 90);
    se0 = strel('line', 3, 0);
    BWsdil = imdilate(BWs, [se90 se0]);
    figure, imshow(BWsdil), title('dilated gradient mask');
    BWdfill = imfill(BWsdil, 'holes');
    figure, imshow(BWdfill);
    title('binary image with filled holes');
    BWnobord = imclearborder(BWdfill, 4);
    figure, imshow(BWnobord), title('cleared border image');
    seD = strel('diamond',1);
    BWfinal = imerode(BWnobord,seD);
    BWfinal = imerode(BWfinal,seD);
    figure, imshow(BWfinal), title('segmented image');

    [B,L] = bwboundaries(BWnobord, 'noholes');
    roi = omero.model.RoiI;
    for k = 1 : length(B)
       boundary = B{k};
       x_coordinates = boundary(:,2);
       y_coordinates = boundary(:,1);
       shape = createPolyline(x_coordinates, y_coordinates);
       roi.addShape(shape);
       area = polyarea(x_coordinates, y_coordinates);
       max_area = max(max_area, area);
    end
    values(1, i) = imageId;
    values(2, i) = max_area;
    % Link the roi and the image
    roi.setImage(omero.model.ImageI(imageId, false));
    roi = iUpdate.saveAndReturnObject(roi);
end

% create a CSV
headers = 'Dataset_name,ImageID,Area';
tmpName = [tempname,'.csv'];
[filepath,name,ext] = fileparts(tmpName);
f = fullfile(filepath, 'results.csv');
fileID = fopen(f,'w');
fprintf(fileID,'%s\n',headers);
for i = 1 : numel(images)
    row = strcat(char(datasetName), ',', num2str(values(1, i)), ',', num2str(values(2, i)));
    fprintf(fileID,'%s\n',row);
end
fclose(fileID);

% Create a file annotation
fileAnnotation = writeFileAnnotation(session, f, 'mimetype', 'text/csv', 'namespace', 'training.demo');
linkAnnotation(session, fileAnnotation, 'dataset', datasetId);

client.closeSession();
