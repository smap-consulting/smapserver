#!/bin/sh

# Minify
node tools/r.js -o tools/build.js

# Create a tar file and copy to the deploy directory
tar -zcf smapServer.tgz smapServer
cp smapServer.tgz ~/deploy

# deploy to local
cp -rf smapServer/* /Library/WebServer/Documents
sudo apachectl restart
rm -rf smapServer
rm smapServer.tgz
