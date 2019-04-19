% Note: Do not run the whole script. Select the code block
% of each exercise, right-click and "Evaluate Selection". 
% Then proceed to the next exercise.
% The exercises build on top of each other (later exercises
% cannot be run unless the previous exercises have been 
% executed successfully). If you get stuck, clear the workspace
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
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

datasetId = MATLAB_DATASET_ID;
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
% Get the target protein name (== name of the relevant channel).
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

image = datasetImages(1); % Pick one image from the dataset (they all have the same target protein)
annotations = getObjectAnnotations(session, 'map', 'image', image.getId().getValue());
% Iterate through all map annotations ('key-value pairs')
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
% Determine the channel indices of the relevant channel.
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

fprintf('Using image: %s, channel: %s (index: %i)\n', image.getName().getValue(), target, channelIndex)
z = 0;
t = 0;
plane = getPlane(session, image, z, channelIndex - 1, t);
threshNstd = 6;
minPixelsPerCentriole = 20;   % minimum size of objects of interest
vals = reshape(plane, [numel(plane), 1]);   % reshape to 1 column
mean1 = mean(vals);
std1 = std(vals);
% images are mostly background, so estimate threshold using basic stats
thresh1 = mean1 + threshNstd * std1;
bwRaw = imbinarize(plane, thresh1);
BWfinal = bwareaopen(bwRaw, minPixelsPerCentriole);  % remove small objects
imshow(BWfinal);
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
    plane = getPlane(session, image, z, channelIndex - 1, t); % channel index in OMERO starts with 0
    threshNstd = 6;
    minPixelsPerCentriole = 20;   % minimum size of objects of interest
    vals = reshape(plane, [numel(plane), 1]);   % reshape to 1 column
    mean1 = mean(vals);
    std1 = std(vals);
    % images are mostly background, so estimate threshold using basic stats
    thresh1 = mean1 + threshNstd * std1;
    bwRaw = imbinarize(plane, thresh1);
    BWfinal = bwareaopen(bwRaw, minPixelsPerCentriole);
    
    [B,L] = bwboundaries(BWfinal, 'noholes');
    roi = omero.model.RoiI;
    max_area = 0;
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
    end
    % Link the roi and the image
    imageId = image.getId().getValue();
    roi.setImage(omero.model.ImageI(imageId, false));
    if ~isempty(B)
        roi = iUpdate.saveAndReturnObject(roi);
        csv_row.add(imageId);
        csv_row.add(max_area);
        csv_data.add(csv_row);
    end 
end

% Create a CSV file
headers = 'ImageID,Area';
fileID = fopen('results.csv','w');
fprintf(fileID,'%s\n',headers);
for kk = 0: csv_data.size()-1
        csv_row = csv_data.get(kk);
        row = strcat(num2str(csv_row.get(0)), ',', num2str(csv_row.get(1)));
        fprintf(fileID,'%s\n',row);
end
fclose(fileID);
% Create and link the CSV file annotation
fileAnnotation = writeFileAnnotation(session, 'results.csv', 'mimetype', 'text/csv', 'namespace', 'training.demo');
linkAnnotation(session, fileAnnotation, 'dataset', datasetId);


% Exercise 7:
% Save the results as OMERO.table.
% After this step go back to OMERO.web,
% select an image and expand the 'Tables' tab
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

columns = javaArray('omero.grid.Column', 2);
columns(1) = omero.grid.LongColumn('Image', '', []);
columns(2) = omero.grid.DoubleColumn('Area', '', []);
table = session.sharedResources().newTable(1, char('from_matlab'));
table.initialize(columns);
for kk = 0: csv_data.size()-1
    csv_row = csv_data.get(kk);
    row = javaArray('omero.grid.Column', 1);
    row(1) = omero.grid.LongColumn('Image', '', [csv_row.get(0)]);
    row(2) = omero.grid.DoubleColumn('Area', '', [csv_row.get(1)]);
    table.addData(row);
end
file = table.getOriginalFile();
% link table to an Image
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

