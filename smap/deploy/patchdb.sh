#!/bin/sh
deploy_from="version1"

# Apply security updates
unattended-upgrades

# Set flag for ubuntu version
u1404=`lsb_release -r | grep -c "14\.04"`
u1604=`lsb_release -r | grep -c "16\.04"`
u1804=`lsb_release -r | grep -c "18\.04"`

if [ $u1804 -eq 1 ]; then
    TOMCAT_VERSION=tomcat8
else
    TOMCAT_VERSION=tomcat7
fi

CATALINA_HOME=/usr/share/$TOMCAT_VERSION

# Copy postgres driver
cp -r $deploy_from/jdbc/* $CATALINA_HOME/lib/ 

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
sudo -u postgres psql -f ./sd.sql -q -d survey_definitions 2>&1 | grep -v "already exists" | grep -v "duplicate key" | grep -vi "addgeometrycolumn" | grep -v "implicit index" | grep -v "skipping" | grep -v "is duplicated" | grep -v "create unique index" | grep -v CONTEXT
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
        sudo cp -v  ../install/subscribers.sh /smap_bin
	sudo sed -i "s#{your_files}#/smap#g" /etc/init/subscribers_fwd.conf
	echo "Modifying URLs of attachments to remove hostname, also moving uploaded files to facilitate forwarding of old surveys"
	java -jar version1/patch.jar apply survey_definitions results
	sudo chown -R $TOMCAT_version /smap/uploadedSurveys
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
sudo chown -R $TOMCAT_version /var/log/subscribers
fi

# version 14.11
# Yes 14.11 patches being reapplied as last release was actually 14.10.02
if [ $version -lt "1411" ]
then
echo "Applying patches for version 14.11"
sudo chown -R $TOMCAT_version /smap/attachments
fi


# version 15.01
if [ $version -lt "1501" ]
then
echo "Applying patches for version 15.01"
sudo mkdir /smap/media/organisation
sudo chown -R $TOMCAT_version /smap/media
fi

# version 15.02
if [ $version -lt "1502" ]
then
echo "Applying patches for version 15.02"
sudo rm /var/lib/$TOMCAT_VERSION/webapps/fieldManager.war
fi

# version 15.03
if [ $version -lt "1503" ]
then
sudo mkdir /smap_bin/resources
sudo mkdir /smap_bin/resources/css
fi

# version 15.09
if [ $version -lt "1509" ]
then

# Patch the database
java -jar version1/patch1505.jar apply survey_definitions results

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
sudo chown $TOMCAT_version /smap/misc

fi

# version 15.11
if [ $version -lt "1511" ]
then
java -jar version1/patch.jar apply survey_definitions results
fi

# version 16.01
if [ $version -lt "1601" ]
then
	java -jar version1/patchcomplete.jar apply survey_definitions results
fi

# version 16.02
if [ $version -lt "1602" ]
then
	echo "no patches for 16.02"
fi

# version 16.03
if [ $version -lt "1603" ]
then
	echo "no patches for 16.03"
fi

# version 16.12
if [ $version -lt "1612" ]
then
echo "Applying patches for version 16.12"
sudo mkdir /smap_bin
sudo mkdir /smap_bin/resources
sudo mkdir /smap_bin/resources/css

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
sudo rm -rf /smap_bin/pyxform
sudo cp -r src/pyxform/pyxform/ /smap_bin
sudo a2enmod headers

sudo chown -R $TOMCAT_version /smap_bin

fi

# version 16.12
if [ $version -lt "1612" ]
then
	echo '# copy subscriber upstart files'
	upstart_dir="/etc/init"			
	service_dir="/etc/systemd/system"
	if [ $u1804 -eq 1 ]; then
		sudo cp ../install/config_files/subscribers.service $service_dir
		sudo chmod 664 $service_dir/subscribers.service
		sudo cp ../install/config_files/subscribers_fwd.service $service_dir
		sudo chmod 664 $service_dir/subscribers_fwd.service
		
		sudo sed -i "s#tomcat7#tomcat8#g" $service_dir/subscribers.service
		sudo sed -i "s#tomcat7#tomcat8#g" $service_dir/subscribers_fwd.service
	fi
	
	if [ $u1604 -eq 1 ]; then
		sudo cp ../install/config_files/subscribers.service $service_dir
		sudo chmod 664 $service_dir/subscribers.service
		sudo cp ../install/config_files/subscribers_fwd.service $service_dir
		sudo chmod 664 $service_dir/subscribers_fwd.service
	fi
	
	if [ $u1404 -eq 1 ]; then
		sudo cp ../install/config_files/subscribers.conf $upstart_dir
		sudo cp ../install/config_files/subscribers_fwd.conf $upstart_dir
	fi

fi

# version 17.10
if [ ! -e /usr/share/$TOMCAT_VERSION/.aws ]
then
sudo mkdir /usr/share/$TOMCAT_VERSION/.aws
fi


#####################################################################################
# All versions
# Copy the new apache configuration files

cd ../install
chmod +x apacheConfig.sh
./apacheConfig.sh
cd ../deploy
 
# Patch pyxform
#sudo sed -i "s/from pyxform import constants/import constants/g" /smap_bin/pyxform/survey.py

# update version reference
new_version="1906"
echo "$new_version" > ~/smap_version
