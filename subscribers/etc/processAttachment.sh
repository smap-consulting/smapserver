#!/bin/sh

if [ $# -lt "4" ]; then
	echo "usage $0 filename directory content_type extension"
fi

echo "================================================="
echo "processing $0 $1 $2 $3 $4" 

filename=$1
destdir=$2
contenttype=$3
ext=$4
destfile="$destdir/$filename.$ext"
destthumbnail="$destdir/thumbs/$filename.$ext.jpg"
destflv="$destdir/flv/$filename.$ext.flv"


# If content type is "image" create a thumbnail
type=`echo $contenttype | cut -c 1-5`
if [ x"$type" = ximage ]; then
	echo "--------------------------------------"
	echo "Creating thumbnails $destthumbnail from $destfile"
	rm $destthumbnail
	sh -c "/usr/bin/convert -thumbnail 100 $destfile $destthumbnail"
fi

#If content type is "video" create a thumbnail and a flowplayer friendly video
if [ x"$type" = xvideo ]; then
	echo "--------------------------------------"
	echo "Creating thumbnails $destthumbnail from $destfile"
	rm $destthumbnail
	sh -c "/usr/bin/ffmpeg -i $destfile -vf scale=-1:100  $destthumbnail"

	echo "--------------------------------------"
	echo "Creating flash movie and adding meta data"
	rm $destflv
	sh -c "/usr/bin/ffmpeg -i $destfile -ar 22050 -f flv $destflv"
	sh -c "flvtool2 -UP $destflv"
fi

#If content type is "audio" create a a flowplayer friendly video
if [ x"$type" = xaudio ]; then
	echo "--------------------------------------"
	echo "Creating flash movie and adding meta data"
	rm $destflv
	sh -c "/usr/bin/ffmpeg -i $destfile -ar 22050 -f flv $destflv"
	sh -c "flvtool2 -UP $destflv"
fi
