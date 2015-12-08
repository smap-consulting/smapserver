#!/bin/sh

echo '##### 0. check configuration'
# Set flag if this is apache2.4
a24=`sudo apachectl -version | grep -c "2\.4"`
a_config_dir="/etc/apache2/sites-available"
if [ $a24 -eq 0 ]; then	
	echo "%%%%%% Warning: Apache configuration files for Apache 2.4 will be installed. "
	echo "PLease install Apache 2.4 prior to upgrading"
	exit 1;
fi



version=1
if [ -e ~/smap_version ]
then
	version=`cat ~/smap_version`
fi

echo "Current Smap Version is $version"

# Apply database patches
echo "applying patches to survey_definitions"
sudo -u postgres psql -f ./sd.sql -q -d survey_definitions 2>&1 | grep -v "already exists" | grep -v "duplicate key" | grep -vi "addgeometrycolumn" | grep -v "implicit index" | grep -v "skipping" | grep -v "create unique index" | grep -v CONTEXT
echo "applying patches to results"
sudo -u postgres psql -f ./results.sql -q -d results 2>&1 | grep -v "already exists"

# Version 14.02
if [ $version -lt "1402" ]
then
	echo "Applying patches for version 14.02"
	sudo apt-get install gdal-bin
fi

# version 14.03
if [ $version -lt "1403" ]
then
	echo "Applying patches for version 14.03"
	echo "set up forwarding - assumes uploaded files are under /smap"
        sudo cp -v  ../install/config_files/subscribers_fwd.conf /etc/init
        sudo cp -v  ../install/subscribers.sh /usr/bin/smap
	sudo sed -i "s#{your_files}#/smap#g" /etc/init/subscribers_fwd.conf
	echo "Modifying URLs of attachments to remove hostname, also moving uploaded files to facilitate forwarding of old surveys"
	java -jar version1/patch.jar apply survey_definitions results
	sudo chown -R tomcat7 /smap/uploadedSurveys
fi

# version 14.05
if [ $version -lt "1405" ]
then
	echo "Applying patches for version 14.05"
	echo "Upgrade pyxform to support geoshape and geotrace types"
	sudo apt-get install python-dev -y
	sudo apt-get install libxml2-dev -y
	sudo apt-get install libxslt-dev
	sudo apt-get install libxslt1-dev -y
	sudo apt-get install git -y
	sudo apt-get install python-setuptools -y
	sudo easy_install pip
	sudo pip install setuptools --no-use-wheel --upgrade
	sudo pip install xlrd
	sudo rm -rf src/pyxform
	sudo pip install -e git+https://github.com/UW-ICTD/pyxform.git@master#egg=pyxform
	sudo rm -rf /usr/bin/smap/pyxform
	sudo cp -r src/pyxform/pyxform/ /usr/bin/smap/
	sudo a2enmod headers
fi

# version 14.08
if [ $version -lt "1408" ]
then
echo "Applying patches for version 14.08"
echo "installing ffmpeg"
if [ $(cat /etc/*-release | grep "DISTRIB_CODENAME=" | cut -d "=" -f2) == 'trusty' ];
then  
sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu trusty main'  && sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu saucy main'  && sudo apt-get update
fi
sudo apt-get update -y
sudo apt-get install ffmpeg -y
fi

# version 14.10

if [ $version -lt "1410" ]
then
echo "Applying patches for version 14.10"
sudo chown -R tomcat7 /var/log/subscribers
fi

# version 14.11
# Yes 14.11 patches being reapplied as last release was actually 14.10.02
if [ $version -lt "1411" ]
then
echo "Applying patches for version 14.11"
sudo chown -R tomcat7 /smap/attachments
fi


# version 15.01
if [ $version -lt "1501" ]
then
echo "Applying patches for version 15.01"
sudo mkdir /smap/media/organisation
sudo chown -R tomcat7 /smap/media
echo "1501" > ~/smap_version
fi

# version 15.02
if [ $version -lt "1502" ]
then
echo "Applying patches for version 15.02"
sudo rm /var/lib/tomcat7/webapps/fieldManager.war
echo "1502" > ~/smap_version
fi

# version 15.03
if [ $version -lt "1503" ]
then
sudo mkdir /usr/bin/smap/resources
sudo mkdir /usr/bin/smap/resources/css
echo "1503" > ~/smap_version
fi

# version 15.09
if [ $version -lt "1509" ]
then

# Patch the database
java -jar version1/patch1505.jar apply survey_definitions results
echo "1509" > ~/smap_version

cd ../install
# Set up new apache configuration structure

sudo cp  $a_config_dir/smap.conf $a_config_dir/smap.conf.bu
sudo cp config_files/a24-smap.conf $a_config_dir/smap.conf

sudo cp $a_config_dir/smap-ssl.conf $a_config_dir/smap-ssl.conf.bu
sudo cp config_files/a24-smap-ssl.conf $a_config_dir/smap-ssl.conf

sudo a2ensite  smap.conf
sudo a2ensite  smap-ssl.conf

# Disable default sites - TODO find some way of smap coexistign with other sites on the same apache server automatically
sudo a2dissite 000-default
sudo a2dissite default-ssl

cd ../deploy

# Create miscelaneous directory
sudo mkdir /smap/misc
sudo chown tomcat7 /smap/misc

fi

# version 15.10
if [ $version -lt "1510" ]
then
echo "1510" > ~/smap_version
fi

# version 15.11
if [ $version -lt "1511" ]
then
java -jar version1/patch.jar apply survey_definitions results
echo "1510" > ~/smap_version
fi

#####################################################################################
# All versions
# Copy the new apache configuration files

cd ../install
chmod +x apacheConfig.sh
./apacheConfig.sh
cd ../deploy

# Patch pyxform
sudo sed -i "s/from pyxform import constants/import constants/g" /usr/bin/smap/pyxform/survey.py

