#!/bin/sh
deploy_from="version1"
# Set flag for ubuntu version
u1404=`lsb_release -r | grep -c "14\.04"`
u1604=`lsb_release -r | grep -c "16\.04"`
u1804=`lsb_release -r | grep -c "18\.04"`

if [ $u1804 -eq 1 ]; then
    TOMCAT_VERSION=tomcat8
else
    TOMCAT_VERSION=tomcat7
fi

# save directory that contains deploy script
cwd=`pwd`

#
# stop services
#
service apache2 stop
service $TOMCAT_VERSION stop
if [ $u1404 -eq 1 ]; then
service subscribers stop
service subscribers_fwd stop
fi
if [ $u1604 -eq 1 ]; then
systemctl stop subscribers
systemctl stop subscribers_fwd
fi
if [ $u1804 -eq 1 ]; then
systemctl stop subscribers
systemctl stop subscribers_fwd
fi
service postgresql stop

cd $deploy_from
for f in `ls *.war`
do
echo "restarting:" $f
rm /var/lib/$TOMCAT_VERSION/webapps/$f
fdir=`echo $f | sed "s/\([a-zA-Z0-9]*\)\..*/\1/"`
echo "deleting folder:" $fdir
rm -rf /var/lib/$TOMCAT_VERSION/webapps/$fdir
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
cp $deploy_from/fieldTaskPreJellyBean.apk /var/www/smap
cp $deploy_from/smapUploader.jar /var/www/smap
cp $deploy_from/fieldTask.apk /var/www/default
cp -r $deploy_from/smapIcons/WebContent/* /var/www/smap/smapIcons
cp $deploy_from/*.war /var/lib/$TOMCAT_VERSION/webapps
chown -R $TOMCAT_VERSION /var/lib/$TOMCAT_VERSION/webapps

# deploy webforms
#if [ -e $deploy_from/webforms.tgz ]
#then
#	echo "Updating webforms"
#        rm -rf /var/www/smap/webforms
#        mkdir /var/www/smap/webforms
#        tar -xzf $deploy_from/webforms.tgz -C /var/www/smap/
#fi

# change owner for apache web directory
chown -R www-data:www-data /var/www/smap
chmod -R o-rwx /var/www/smap

#
# smap bin
cp ../install/subscribers.sh /smap_bin
cp $deploy_from/subscribers.jar /smap_bin
cp $deploy_from/codebook.jar /smap_bin
cp -r $deploy_from/subscribers/default /smap_bin
cp -r $deploy_from/resources /smap_bin
cp -r $deploy_from/scripts/* /smap_bin
cp  $deploy_from/resources/fonts/* /usr/share/fonts/truetype
chmod +x /smap_bin/*.sh

# Copy aws credentials
sudo cp  $deploy_from/resources/properties/credentials /usr/share/$TOMCAT_VERSION/.aws

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
        cp -v ~/custom/subscribers/default/* /smap_bin/default
fi

# Delete temporary files
rm -rf /smap/temp/*

# Restart Servers
service postgresql start
service $TOMCAT_VERSION start
service apache2 start

if [ $u1404 -eq 1 ]; then
service subscribers start
service subscribers_fwd start
fi
if [ $u1604 -eq 1 ]; then
systemctl start subscribers
systemctl start subscribers_fwd
fi
if [ $u1804 -eq 1 ]; then
systemctl start subscribers
systemctl start subscribers_fwd
fi

# Hosted Only
# Start disk monitor
cd $cwd
sudo -u postgres psql -f ./rates.sql -q -d survey_definitions 2>&1 | grep -v duplicate | grep -v "already exists"
sudo -u ubuntu ~ubuntu/smap/deploy/manage.sh
