#!/bin/sh

IFS=$'\n'

for f in *.xml
do
	filename=$(basename "$f")
	filename="${filename%.*}"
	echo "processing $filename"
	mkdir $filename
	mv $filename.xml $filename
done
