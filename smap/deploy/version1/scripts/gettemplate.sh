#!/bin/sh

if [ $# -lt 2 ]; then
	echo "usage $0 file_name language"
	exit
fi

echo "================================================="
echo "processing $0 $1 $2" 

java -jar /smap_bin/codebook.jar $1 $2
