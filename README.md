#OpenCV Mosiac
Generates a similiar image using other images as pixels. 

##Details
Useing the OpenCV Java bindings we divide each image into tiles and calulate histograms for each quadrant. Then compare the related histograms to the portion of the input image to find the closest match. This process is multithreaded using streams but is very work intensive. You can tune the comparitor parameters to achieve faster but worse results.

##Install
To install you'll need OpenCV and Java 8. Then clone the repo and run
```
ant -DocvJarDir=/usr/local/Cellar/opencv/2.4.9/share/OpenCV/java -DocvLibDir=/usr/local/Cellar/opencv/2.4.3/share/OpenCV/java
```
* change your java paths to point to your OpenCV java dir.
