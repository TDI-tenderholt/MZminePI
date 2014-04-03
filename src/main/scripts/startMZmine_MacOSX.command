#!/bin/sh

# ***************************************************************************
# * 1. Set R environment variables. Choose appropriate version numbers.
# *    Current may be used for the version number for R_HOME.
# ***************************************************************************
export R_HOME=/Library/Frameworks/R.framework/Versions/Current/Resources
export R_USER_LIBS=${HOME}/Library/R/2.15/library

# ***************************************************************************


# ***************************************************************************
# * 2. Set the directory holding the JRI shared library (libjri.jnilib).
# ***************************************************************************
JRI_LIB_PATH=${R_HOME}/library/rJava/jri:${R_USER_LIBS}/rJava/jri

# ***************************************************************************


# ***************************************************************************
# 3. It is usually not necessary to modify the JAVA_COMMAND parameter, but 
#    if you like to run a specific Java Virtual Machine, you may set the 
#    path to the java command of that JVM. By default, we use a Mac OS X 
#    specific path to enforce using Java 6 VM
# ***************************************************************************
JAVA_FRAMEWORK_DIR=/System/Library/Frameworks/JavaVM.framework
JAVA_COMMAND=${JAVA_FRAMEWORK_DIR}/Versions/1.6/Commands/java

# ***************************************************************************


# ***************************************************************************
# 4. The HEAP_SIZE variable line defines the Java heap size in MB. That is 
#    the total amount of memory available to MZmine 2. Please adjust 
#    according to the amount of memory of your computer.
# ***************************************************************************
HEAP_SIZE=1024

# ***************************************************************************


# ***************************************************************************
# 5. The TMP_FILE_DIRECTORY parameter defines the location where temporary 
#    files (parsed raw data) will be placed. Default is /tmp.
# ***************************************************************************
TMP_FILE_DIRECTORY=/tmp

# ***************************************************************************


# ***************************************************************************
# It is not necessary to modify the following section
# ***************************************************************************

JAVA_PARAMETERS="-XX:+UseParallelGC -Xdock:name='MZminePI' -Xdock:icon=icons/MZmineIcon.png -Djava.io.tmpdir=$TMP_FILE_DIRECTORY $USE_64_BIT -Dapple.laf.useScreenMenuBar=true -Xms${HEAP_SIZE}m -Xmx${HEAP_SIZE}m -Djava.library.path=${JRI_LIB_PATH}"
CLASS_PATH=MZminePI.jar
MAIN_CLASS=net.sf.mzmine.main.MZmineCore 

# Make sure we are in the correct directory
SCRIPTDIR=`dirname "$0"`
cd "$SCRIPTDIR"

# Show java version, in case a problem occurs
echo "-version" | xargs $JAVA_COMMAND > MZminePI.log 2>&1
echo >> MZminePI.log

# This command starts the Java Virtual Machine
echo Running MZminePI...
echo $JAVA_COMMAND "$JAVA_PARAMETERS" -classpath $CLASS_PATH $MAIN_CLASS "$@" >> MZminePI.log
echo >> MZminePI.log
echo "$JAVA_PARAMETERS" -classpath $CLASS_PATH $MAIN_CLASS "$@" | xargs $JAVA_COMMAND >> MZminePI.log 2>&1

# ***************************************************************************

