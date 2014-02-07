#!/bin/bash

DIR="/cygdrive/c/Users/dschmidt"
BINARY_DIR="$DIR/work/MZminePI"
SOURCE_DIR="$DIR/workspace/MZminePI"
WEB_DIR="$DIR/Dropbox/Work/Veritomyx/VTMX/interface/MZminePI"
WEB_DIR="$SOURCE_DIR"

MZmineCore="$SOURCE_DIR/src/main/java/net/sf/mzmine/main/MZmineCore.java"
VTMXlive=`grep "boolean VtmxLive" $MZmineCore | sed -e 's/.*= //' -e 's/;.*//'`
VER=`grep "String  MZmineVersion" $MZmineCore | sed -e 's/.*= "//' -e 's/".*//'`

# only build source tar for live code
if [ "$VTMXlive" != "true" ]; then
	echo "Cannot export test code"
	echo "See VtmxLive variable in $MZmineCore"

else

	# export binary
	OUT="$WEB_DIR/MZminePI-$VER.zip"
	echo
	echo Building $OUT...

	cd $BINARY_DIR/..
	# don't copy the config or log files
	rm -f MZminePI/conf/config.xml MZminePI/*.log
	zip -rq $OUT MZminePI
	echo "$OUT (`cat $OUT | wc -c` bytes)"

	# export source
	OUT="$WEB_DIR/MZminePI-$VER-src.zip"
	echo
	echo Building $OUT...

	cd $SOURCE_DIR/..
	# don't copy the config file
	mv MZminePI/src/main/conf/config.xml .
	# copy just the source and licenses from this tree
	zip -rq $OUT MZminePI/assembly.xml MZminePI/LICENSE.txt MZminePI/pom.xml MZminePI/README.txt MZminePI/src
	# restore the config file
	mv config.xml MZminePI/src/main/conf
	echo "$OUT (`cat $OUT | wc -c` bytes)"
fi

sleep 10
