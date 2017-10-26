#!/bin/sh

# Minify
node tools/r.js -o tools/build.js

# Create a tar file and copy to the deploy directory
export COPYFILE_DISABLE=true
tar -zcf tasks.tgz tasks
cp tasks.tgz ~/os_deploy

# deploy to local
sudo rm -rf /Library/WebServer/Documents/tasks
sudo mkdir /Library/WebServer/Documents/tasks
sudo cp -rf tasks/* /Library/WebServer/Documents/tasks
sudo apachectl restart
rm tasks.tgz

# clean up the temporary tasks directory but first check that it is the right one
if [ -f dep.sh ]
then
	rm -rf tasks
fi
