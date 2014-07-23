#!/bin/sh

# Minify
node tools/r.js -o tools/build.js

# Create a tar file and copy to the deploy directory
export COPYFILE_DISABLE=true
tar -zcf fieldAnalysis.tgz fieldAnalysis
cp fieldAnalysis.tgz ~/deploy

# deploy to local
rm -rf /Library/WebServer/Documents/fieldAnalysis
mkdir /Library/WebServer/Documents/fieldAnalysis
cp -rf fieldAnalysis/* /Library/WebServer/Documents/fieldAnalysis
sudo apachectl restart
rm -rf fieldAnalysis
rm fieldAnalysis.tgz
