#!/bin/sh

# Minify
node tools/r.js -o tools/build.js

export COPYFILE_DISABLE=true
# Create a tar file and copy to the deploy directory
cd smapServer
tar -zcf smapServer.tgz *
cp smapServer.tgz ~/deploy
rm smapServer.tgz
cd ..

# deploy to local
cp -rf smapServer/* /Library/WebServer/Documents
sudo apachectl restart
rm -rf smapServer
