@echo off

rem ***********************************************************************
rem * 1. Set R environment variables. Choose appropriate version numbers.
rem ***********************************************************************
    set R_HOME=C:\Program Files\R\R-3.0.3
    set R_LIBS_USER=%USERPROFILE%\Documents\R\win-library\3.0

rem ***********************************************************************


rem ***********************************************************************
rem * 2. Set remaining R environment variables.
rem ***********************************************************************
    set R_LIBS=%R_HOME%\library
    set R_SHARE_DIR=%R_HOME%\share 
    set R_INCLUDE_DIR=%R_HOME%\include
    set R_DOC_DIR=%R_HOME%\doc

rem ***********************************************************************


rem ***********************************************************************
rem * 3. Include R DLLs in PATH, and set directory holding the shared 
rem *    library (libjri.dll).
rem ***********************************************************************
    set PATH=%PATH%;%R_HOME%\bin\x64
    set JRI_LIB_PATH=%R_LIBS_USER%\rJava\jri\x64;%R_LIBS%\rJava\jri\x64

rem ***********************************************************************


rem ***********************************************************************
rem * 4. It is usually not necessary to modify the JAVA_COMMAND parameter, 
rem *    but if you like to run a specific Java Virtual Machine, you may 
rem *    set the path to the java command of that JVM.
rem ***********************************************************************
    set JAVA_COMMAND=java

rem ***********************************************************************


rem ***********************************************************************
rem * 5. The HEAP_SIZE variable line defines the Java heap size in MB. 
rem *    That is the total amount of memory available to MZmine 2.
rem *    Please adjust according to the amount of memory of your computer.
rem *    Maximum value on a 32-bit Windows system is about 1300.
rem ***********************************************************************
    set HEAP_SIZE=1024

rem ***********************************************************************


rem ***********************************************************************
rem * 6. The TMP_FILE_DIRECTORY parameter defines the location where 
rem *    temporary files (parsed raw data) will be placed. Default is  
rem *    %TEMP%, which represents the system temporary directory.
rem ***********************************************************************
    set TMP_FILE_DIRECTORY=%TEMP%

rem ***********************************************************************



rem ***********************************************************************
rem * It is not necessary to modify the following section.
rem ***********************************************************************

set JAVA_PARAMETERS=-XX:+UseParallelGC -Djava.io.tmpdir=%TMP_FILE_DIRECTORY% -Xms%HEAP_SIZE%m -Xmx%HEAP_SIZE%m -Djava.library.path="%JRI_LIB_PATH%"
set CLASS_PATH=MZminePI.jar
set MAIN_CLASS=net.sf.mzmine.main.MZmineCore

rem Show java version, in case a problem occurs
%JAVA_COMMAND% -version > MZminePI.log 2>&1
echo. >> MZminePI.log

rem This command starts the Java Virtual Machine
echo Running MZminePI...
echo %JAVA_COMMAND% %JAVA_PARAMETERS% -classpath %CLASS_PATH% %MAIN_CLASS% %* >> MZminePI.log
echo. >> MZminePI.log
%JAVA_COMMAND% %JAVA_PARAMETERS% -classpath %CLASS_PATH% %MAIN_CLASS% %* >> MZminePI.log 2>&1

rem If there was an error, give the user chance to see it
IF ERRORLEVEL 1 (
	echo See MZminePI.log for errors
	pause
)

rem ***********************************************************************
