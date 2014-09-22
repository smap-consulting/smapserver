#!/bin/sh

if [ $# -lt 1 ]; then
	echo "usage $0 file_name"
	exit
fi

echo "================================================="
echo "processing $0 $1 $2" 

java -jar /usr/bin/smap/codebook.jar $1 $2
