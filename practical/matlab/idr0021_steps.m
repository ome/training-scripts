% Note: In Matlab create a New -> Script. Copy and paste
% everything into the new file. But do not run the whole script. 
% Select the code block of each exercise, right-click and 
% "Evaluate Selection". Then proceed to the next exercise. 
% Later exercises cannot be run unless the previous exercises
% have been executed successfully.
% If you get stuck, right-click on "Workspace", "Clear Workspace" 
% and try again from the beginning.

% Exercise 1
% Connect to OMERO and print your group ID.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

client = loadOmero('outreach.openmicroscopy.org');
session = client.createSession('USER', 'PASSWORD');
client.enableKeepAlive(60);
eventContext = session.getAdminService().getEventContext();
groupId = eventContext.groupId;
disp(groupId);


% Exercise 2
% List the images of a particular dataset.
% Use the Id of the 'matlab-dataset' here.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

datasetId = DATASET_ID;
loadedDatasets = getDatasets(session, datasetId, true);
dataset = loadedDatasets(1);
datasetName = dataset.getName().getValue();
disp(datasetName)
datasetImages = getImages(session, 'dataset', datasetId);
for i = 1 : length(datasetImages)
    image = datasetImages(i);
    fprintf('%s , %i\n', image.getName().getValue(), image.getId().getValue());
end


% Exercise 3
% Get the name of the target protein from the
% map annotations (key-value pairs).
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

 % Pick one image from the dataset (they all have the same target protein):
image = datasetImages(1);
annotations = getObjectAnnotations(session, 'map', 'image', image.getId().getValue());
% Iterate through all map annotations ('key-value pairs'):
for j = 1 : length(annotations)
    rows = annotations(j).getMapValue();
    for k = 0 : rows.size() - 1
        if rows.get(k).name == 'Antibody Target'
            target = rows.get(k).value;
            break;
        end
    end
end
fprintf('Target protein: %s\n', target);


% Exercise 4
% Determine the channel indices of the relevant channel
% (the channel in which the target protein is stained).
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

for i = 1 : length(datasetImages)
    image = datasetImages(i);
    channels = loadChannels(session, image);
    for j = 1 : numel(channels) 
        channel = channels(j);
        channelId = channel.getId().getValue();
        lc = channel.getLogicalChannel();
        channelName = lc.getName().getValue();
        if channelName == target
            channelIndex = j;
            disp(channelIndex);
        end
    end
end


% Exercise 5
% Perform image segmentation on one image.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

fprintf('Using image: %s, channel: %s (index: %i)\n', ...
    image.getName().getValue(), target, channelIndex)
% Get the pixel values of the relevant plane
% (Note: channel index in OMERO starts with 0)
z = 0;
t = 0;
ch = channelIndex - 1
plane = getPlane(session, image, z, ch, t);

minPixelsPerCentriole = 20;   % minimum size of objects of interest
multiplier = 6; % x times standard dev used as threshold
pix_values = reshape(plane, [numel(plane), 1]);   % reshape to 1 column
pix_mean = mean(pix_values);
pix_std = std(pix_values);
threshold = pix_mean + multiplier * pix_std;
bwRaw = imbinarize(plane, threshold);
bwFinal = bwareaopen(bwRaw, minPixelsPerCentriole);  % remove small objects
imshow(bwFinal);
title(strcat(string(image.getName().getValue()), ' (segmented)'));


% Exercise 6 
% Putting it all together: Analyse the whole dataset and
% save results as ROIs and CSV file.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

iUpdate = session.getUpdateService(); % needed to save the ROIs
csv_data = java.util.ArrayList;
for i = 1 : length(datasetImages)
    image = datasetImages(i);
    channels = loadChannels(session, image);
    for j = 1 : numel(channels) 
        channel = channels(j);
        channelId = channel.getId().getValue();
        lc = channel.getLogicalChannel();
        channelName = lc.getName().getValue();
        if channelName == target
            channelIndex = j;
            break;
        end
    end
    
    fprintf('Analyse Image: %s, Channel: %i\n', image.getName().getValue(), channelIndex);
    plane = getPlane(session, image, z, channelIndex - 1, t); 
    pix_values = reshape(plane, [numel(plane), 1]);
    pix_mean = mean(pix_values);
    pix_std = std(pix_values);
    threshold = pix_mean + multiplier * pix_std;
    bwRaw = imbinarize(plane, threshold);
    % separate the objects:
    bwFinal = bwareaopen(bwRaw, minPixelsPerCentriole);
    [B,L] = bwboundaries(bwFinal, 'noholes');
    % calculate some properties:
    props = regionprops(bwFinal, plane, {'Perimeter', 'MaxIntensity'});
    roi = omero.model.RoiI;
    max_area = 0;
    max_per = 0;
    max_in = 0;
    csv_row = java.util.ArrayList;
    for b = 1:length(B)
        boundary = B{b};
        x_coordinates = boundary(:,2);
        y_coordinates = boundary(:,1);
        shape = createPolygon(x_coordinates, y_coordinates);
        setShapeCoordinates(shape, z, channelIndex - 1, t);
        roi.addShape(shape);
        area = polyarea(x_coordinates, y_coordinates);
        max_area = max(max_area, area);
        max_per = max(max_per, props(b).Perimeter(1));
        max_in = max(max_in, props(b).MaxIntensity(1));
    end
    % Link the roi and the image
    imageId = image.getId().getValue();
    imageName = image.getName().getValue();
    roi.setImage(omero.model.ImageI(imageId, false));
    if ~isempty(B)
        roi = iUpdate.saveAndReturnObject(roi);
        csv_row.add(imageId);
        csv_row.add(imageName);
        csv_row.add(max_area);
        csv_row.add(max_per);
        csv_row.add(max_in);
        csv_data.add(csv_row);
    end 
end

% Create a CSV file
headers = 'ImageID,Image_Name,Max_Area,Max_Perimeter,Max_Intesity';
fileID = fopen('results.csv','w');
fprintf(fileID,'%s\n',headers);
for kk = 0: csv_data.size()-1
        csv_row = csv_data.get(kk);
        row = strcat(num2str(csv_row.get(0)), ',', num2str(csv_row.get(1)),...
            ',', num2str(csv_row.get(2)), ',', num2str(csv_row.get(3)), ...
            ',', num2str(csv_row.get(4)));
        fprintf(fileID,'%s\n',row);
end
fclose(fileID);
% Create and link the CSV file annotation
fileAnnotation = writeFileAnnotation(session, 'results.csv', 'mimetype',... 
'text/csv', 'namespace', 'training.demo');
linkAnnotation(session, fileAnnotation, 'dataset', datasetId);


% Exercise 7:
% Save the results as OMERO.table.
% After this step go back to OMERO.web, select an image 
% from the evaluated dataset and expand the 'Tables' tab
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

columns = javaArray('omero.grid.Column', 5);
columns(1) = omero.grid.LongColumn('Image', '', []);
columns(2) = omero.grid.StringColumn('Image_Name', '', 255, []);
columns(3) = omero.grid.DoubleColumn('Max_Area', '', []);
columns(4) = omero.grid.DoubleColumn('Max_Perimeter', '', []);
columns(5) = omero.grid.DoubleColumn('Max_Intesity', '', []);
table = session.sharedResources().newTable(1, char('from_matlab'));
table.initialize(columns);
for kk = 0: csv_data.size()-1
    csv_row = csv_data.get(kk);
    row = javaArray('omero.grid.Column', 1);
    row(1) = omero.grid.LongColumn('Image', '', [csv_row.get(0)]);
    row(2) = omero.grid.StringColumn('Image_Name', '', 255, [csv_row.get(1)]);
    row(3) = omero.grid.DoubleColumn('Max_Area', '', [csv_row.get(2)]);
    row(4) = omero.grid.DoubleColumn('Max_Perimeter', '', [csv_row.get(3)]);
    row(5) = omero.grid.DoubleColumn('Max_Intesity', '', [csv_row.get(4)]);
    table.addData(row);
end
file = table.getOriginalFile();
% link table to the dataset
fa = omero.model.FileAnnotationI;
fa.setFile(file);
fa.setNs(rstring(omero.constants.namespaces.NSBULKANNOTATIONS.value));
linkAnnotation(session, fa, 'dataset', datasetId);


% End.
%%%%%%

% Close connection
client.closeSession();
clear client;
clear session;

