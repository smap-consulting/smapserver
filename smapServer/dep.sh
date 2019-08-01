#!/bin/sh

if [ "$2" = wf ]
then
	echo "building webforms"
	pushd ~/git/webform
	grunt develop
	popd
	./enk_up.sh
fi

if [ "$1" != develop ]
then
	# Minify webform bundle
	rm WebContent/build/js/webform-bundle.min.js

	echo "--------------------------- transpiling with babel to es5"
	babel WebContent/build/js/webform-bundle.js --out-file WebContent/build/js/webform-bundle.es5.js

	echo "--------------------------- google closure compile"
	java -jar ~/compiler-latest/closure-compiler-v20190106.jar --js WebContent/build/js/webform-bundle.es5.js --js_output_file WebContent/build/js/webform-bundle.min.js 

	rm WebContent/build/js/webform-bundle.es5.js
fi

# Minify the smap server code
echo "--------------------------- minify smap server code"
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

# copy the motd
cp ~/motd.html /Library/WebServer/Documents

# clean up the temporary smapServer directory but first check that it is the right one
if [ -f dep.sh ]
then
	rm -rf smapServer
fi
