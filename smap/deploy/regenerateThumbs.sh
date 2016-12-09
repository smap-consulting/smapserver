#!/bin/sh
#
# This script will recreate all the thumbnails and flash video copies of uploaded media file
#
if [ $(cat /etc/*-release | grep "DISTRIB_CODENAME=" | cut -d "=" -f2) = "trusty" ];
then  sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu trusty main'  && sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu saucy main'  && sudo apt-get update
sudo apt-get update
sudo apt-get install ffmpeg -y  --force-yes
sudo apt-get install flvtool2 -y  --force-yes
fi


for type in 3gp mpeg mpg mpe qt mov mp4 m4p avi movie 3ga mp2 mp3 mpga m4a amr
do
        if [ "$type" = "3ga" ] || [ "$type" = "mp2" ] || [ "$type" = "mp3" ] || [ "$type" = "mpga" ] || [ "$type" = "m4a" ] || [ "$type" = "amr" ]; then
                content_type="audio/x"
        else
                content_type="video/x"
        fi
        echo $type
        echo $content_type
        for filepath in `find /smap/attachments -name "*.$type" -print`
        do
                dirname=`dirname $filepath`
                file=`basename $filepath`
                base="${file%.*}"
                ext="${file##*.}"

                echo "base: " $base " " $ext
                /smap_bin/processAttachment.sh $base $dirname $content_type $ext

        done
done

chown -R tomcat7 /smap/attachments/

