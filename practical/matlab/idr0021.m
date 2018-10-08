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
% The shapes are saved as polygons.
% The script has been tested with Matlab2017a

host='outreach.openmicroscopy.org';
% To be modified
user='USERNAME';
password='PASSWORD';
projectId = 4253;
online_algo = false;


client = loadOmero(host);
client.enableKeepAlive(60);
% Create an OMERO session
session = client.createSession(user, password);
% Initiliaze the service used to save the Regions of Interest (ROI)
iUpdate = session.getUpdateService();
% Load the Project/Datasets/Images
project = getProjects(session, projectId, true);
datasets = toMatlabList(project.linkedDatasetList);

% Iterate through the datasets
values = java.util.HashMap;
for i = 1 : numel(datasets)
    dataset = datasets(i);
    datasetName = dataset.getName().getValue();
    images = toMatlabList(dataset.linkedImageList);
    value_images = java.util.ArrayList;
    for k = 1 : numel(images)
        val = java.util.ArrayList;
        image = images(k);
        imageName = image.getName().getValue();
        if endsWith(imageName, '.tif')
            continue
        end
        imageId = image.getId().getValue();
        disp(imageId);
        % Load the channels information to determine the channel to analyze
        channels = loadChannels(session, image);
        channelIndex = 0;
        for j = 1 : numel(channels)
            channel = channels(j);
            channelId = channel.getId().getValue();
            channelName = channel.getLogicalChannel().getName().getValue();
            % Determine the index of the channel to analyze
            if contains(char(datasetName), char(channelName))
                channelIndex = j-1; % OMERO index starts at 0
                break
            end
        end
        z = 0;
        t = 0;
        % Load the plane, OMERO index starts at 0. sizeZ and SizeT = 1
        plane = getPlane(session, image, z, channelIndex, t);
        if ~online_algo
            threshNstd = 6;
            minPixelsPerCentriole = 20;   % minimum size of objects of interest
            vals = reshape(plane, [numel(plane), 1]);   % reshape to 1 column
            mean1 = mean(vals);
            std1 = std(vals);
            % images are mostly background, so estimate threshold using basic stats
            thresh1 = mean1 + threshNstd * std1;
            bwRaw = imbinarize(plane, thresh1);
            BWfinal = bwareaopen(bwRaw, minPixelsPerCentriole);  % remove small objects
            fig = figure; imshow(BWfinal), title('segmented image');
        else
            method = 'roberts';
            factor = 1;
            [~, threshold] = edge(plane, method);
            BWs = edge(plane, method, threshold*factor);
            se90 = strel('line', 3, 90);
            se0 = strel('line', 3, 0);
            BWsdil = imdilate(BWs, [se90 se0]);
            BWnobord = imclearborder(BWsdil, 4);
            seD = strel('diamond',1);
            BWfinal = imerode(BWnobord,seD);
            BWfinal = imerode(BWfinal,seD);
            fig = figure; imshow(BWfinal), title('segmented image online algorithm');
        end
        [B,L] = bwboundaries(BWfinal, 'noholes');
        roi = omero.model.RoiI;
        max_area = 0;
        for b = 1:length(B)
            boundary = B{b};
            x_coordinates = boundary(:,2);
            y_coordinates = boundary(:,1);
            shape = createPolygon(x_coordinates, y_coordinates);
            setShapeCoordinates(shape, z, channelIndex, t);
            roi.addShape(shape);
            area = polyarea(x_coordinates, y_coordinates);
            max_area = max(max_area, area);
        end
        % Link the roi and the image
        roi.setImage(omero.model.ImageI(imageId, false));
        if ~isempty(B)
            roi = iUpdate.saveAndReturnObject(roi);
            val.add(imageId);
            val.add(max_area);
            value_images.add(val);
        end
        close(fig);
    end
    values.put(i, value_images);
end
% create a CSV
headers = 'DatasetName,ImageID,Area';
tmpName = [tempname,'.csv'];
[filepath,name,ext] = fileparts(tmpName);
f = fullfile(filepath, 'results.csv');
fileID = fopen(f,'w');
fprintf(fileID,'%s\n',headers);
for d = 1 : numel(datasets)
    values_images = values.get(d);
    dataset = datasets(d);
    datasetName = dataset.getName().getValue();
    for kk = 0: values_images.size()-1
        val = values_images.get(kk);
        row = strcat(char(datasetName), ',', num2str(val.get(0)), ',', num2str(val.get(1)));
        fprintf(fileID,'%s\n',row);
    end  
end
fclose(fileID);

% Read the CSV and plot ImageID vs Area
data = csvread(f, 1, 1);
col1 = data(:, 1);
col2 = data(:, 2);
scatter(col1, col2)
xlabel('ImageID')
ylabel('Area')

% Create a file annotation and link it to the Project
fileAnnotation = writeFileAnnotation(session, f, 'mimetype', 'text/csv', 'namespace', 'training.demo');
linkAnnotation(session, fileAnnotation, 'project', projectId);

% Create an OMERO table
columns = javaArray('omero.grid.Column', 2);
columns(1) = omero.grid.LongColumn('Image', '', []);
columns(2) = omero.grid.DoubleColumn('Area', '', []);
% Create a new table.
table = session.sharedResources().newTable(1, char('cell_matlab'));
% Initialize the table
table.initialize(columns);
for i = 1 : numel(datasets)
    values_images = values.get(i);
    dataset = datasets(i);
    datasetName = dataset.getName().getValue();
    for kk = 0: values_images.size()-1
        val = values_images.get(kk);
        row = javaArray('omero.grid.Column', 1);
        row(1) = omero.grid.LongColumn('Image', '', [val.get(0)]);
        row(2) = omero.grid.DoubleColumn('Area', '', [val.get(1)]);
        table.addData(row);
    end
end
file = table.getOriginalFile(); % if you need to interact with the table
% link table to an Image
fa = omero.model.FileAnnotationI;
fa.setFile(file);
fa.setNs(rstring(omero.constants.namespaces.NSBULKANNOTATIONS.value));
linkAnnotation(session, fa, 'project', projectId);
disp("Done");
client.closeSession();
