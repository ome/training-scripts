% Exercise 1
% Connect to OMERO and print your group ID.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

client = loadOmero('outreach.openmicroscopy.org');
session = client.createSession('USER', 'PASSWORD');
client.enableKeepAlive(60);
eventContext = session.getAdminService().getEventContext();
groupId = eventContext.groupId;
disp(groupId)

% Exercise 2
% List the images of a particular dataset.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
datasetId = 2608;
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
% Determine the channel indices of the channels
% matching the dataset name.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
for i = 1 : length(datasetImages)
    image = datasetImages(i);
    channels = loadChannels(session, image);
    for j = 1 : numel(channels) 
        channel = channels(j);
        channelId = channel.getId().getValue();
        lc = channel.getLogicalChannel();
        channelName = lc.getName().getValue();
        if channelName == datasetName
            index = j;
            disp(index);
        end
    end
end

% Exercise 4
% Perform image segmentation on one image.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
image = datasetImages(1);
channelIndex = 0;
z = 0;
t = 0;
plane = getPlane(session, image, z, channelIndex, t);
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
          
% Exercise 5 
% Create ROIs and CSV table from the segmentation result.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
iUpdate = session.getUpdateService(); % needed to save the ROIs

% Create a CSV file
headers = 'ROI_Index,Area';
fileID = fopen('results.csv','w');
fprintf(fileID,'%s\n',headers);

[B,L] = bwboundaries(BWfinal, 'noholes');
roindex = 0
for b = 1:length(B)
    roindex = roindex + 1;
    roi = omero.model.RoiI;
    boundary = B{b};
    x_coordinates = boundary(:,2);
    y_coordinates = boundary(:,1);
    shape = createPolygon(x_coordinates, y_coordinates);
    label = string(roindex);
    shape.setTextValue(rstring(label))
    setShapeCoordinates(shape, z, channelIndex, t);
    roi.addShape(shape);
    roi.setImage(omero.model.ImageI(image.getId().getValue(), false));
    iUpdate.saveAndReturnObject(roi);
    % Add line to CSV file
    area = polyarea(x_coordinates, y_coordinates);
    fprintf(fileID,'%i,%d\n', roindex, area);
end
fclose(fileID);

% Create and link the CSV file annotation
fileAnnotation = writeFileAnnotation(session, 'results.csv', 'mimetype', 'text/csv', 'namespace', 'training.demo');
linkAnnotation(session, fileAnnotation, 'image', image.getId().getValue());

% End
%%%%%

% Close connection
client.closeSession();
clear client;
clear session;
return;

