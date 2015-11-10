#!/bin/sh

# Get latest webformsClient
cp ~/git/webformsClient/public/build/js/webform-combined.min.js WebContent/build/js/webform-combined.min.js

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
sudo rm -rf /Library/WebServer/Documents/js
sudo cp -rf smapServer/* /Library/WebServer/Documents
sudo apachectl restart

# clean up the temporary smapServer directory but first check that it is the right one
if [ -f dep.sh ]
then
	rm -rf smapServer
fi
