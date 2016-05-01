#!/bin/sh
deploy_from="version1"

service apache2 stop
service tomcat7 stop
service postgresql stop

cd $deploy_from
for f in `ls *.war`
do
echo "restarting:" $f
rm /var/lib/tomcat7/webapps/$f
fdir=`echo $f | sed "s/\([a-zA-Z0-9]*\)\..*/\1/"`
echo "deleting folder:" $fdir
rm -rf /var/lib/tomcat7/webapps/$fdir
done
cd ..

if [ -e $deploy_from/smapServer.tgz ]
then
	echo "Updating smapServer"
	rm -rf /var/www/smap/OpenLayers
	rm -rf /var/www/smap/js
	rm -rf /var/www/smap/css
	rm -rf /var/www/smap/*.html
	tar -xzf $deploy_from/smapServer.tgz -C /var/www/smap
fi

if [ -e $deploy_from/fieldAnalysis.tgz ]
then
        echo "Updating fieldAnalysis"
        rm -rf /var/www/smap/fieldAnalysis
        tar -xzf $deploy_from/fieldAnalysis.tgz -C /var/www/smap
fi

if [ -e $deploy_from/fieldManager.tgz ]
then
        echo "Updating fieldManager"
        rm -rf /var/www/smap/fieldManager
        tar -xzf $deploy_from/fieldManager.tgz -C /var/www/smap
fi

if [ -e $deploy_from/tasks.tgz ]
then
        echo "Updating tasks"
        rm -rf /var/www/smap/tasks
        tar -xzf $deploy_from/tasks.tgz -C /var/www/smap
fi

cp $deploy_from/fieldTask.apk /var/www/smap
cp $deploy_from/smapUploader.jar /var/www/smap
cp $deploy_from/fieldTask.apk /var/www/default
cp -r $deploy_from/smapIcons/WebContent/* /var/www/smap/smapIcons
cp $deploy_from/*.war /var/lib/tomcat7/webapps

# deploy webforms
if [ -e $deploy_from/webforms.tgz ]
then
	echo "Updating webforms"
        rm -rf /var/www/smap/webforms
        mkdir /var/www/smap/webforms
        tar -xzf $deploy_from/webforms.tgz -C /var/www/smap/
fi

# change owner for apache web directory
chown -R www-data:www-data /var/www/smap
chmod -R o-rwx /var/www/smap

#
service subscribers stop
service subscribers_fwd stop
cp $deploy_from/subscribers.jar /usr/bin/smap
cp $deploy_from/codebook.jar /usr/bin/smap
cp -r $deploy_from/subscribers/default /usr/bin/smap
cp -r $deploy_from/subscribers/etc/* /usr/bin/smap
cp -r $deploy_from/resources /usr/bin/smap
cp  $deploy_from/resources/fonts/* /usr/share/fonts/truetype


chmod +x /usr/bin/smap/*.sh

cd /var/log/subscribers
rm *.log_old
rename 's/\.log$/\.log_old/g' *.log
cd

# Copy any customised files
if [ -e ~/custom/web ]
then
        echo "copy custom web files"
        cp -vr ~/custom/web/* /var/www/smap
fi
if [ -e ~/custom/subscribers/default ]
then
        echo "copy custom subscriber data files"
        cp -v ~/custom/subscribers/default/* /usr/bin/smap/default
fi

# Delete temporary files
sudo rm -rf /smap/temp/*

service subscribers start
service subscribers_fwd start
#
service postgresql start
service tomcat7 start
service apache2 start
