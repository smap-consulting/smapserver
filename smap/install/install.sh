#!/bin/sh
##### Installation script for setting up basic Smap server without ssl or virtual hosts

force=$1

if [ "$force" == "force" ]
then
	echo "All existing data will be deleted"
fi

config="auto"
clean="true"
filelocn="/smap"

CATALINA_HOME=/usr/share/tomcat7
postgresDriverLocation="http://jdbc.postgresql.org/download"				# Postgres jdbc driver
#postgresDriver="postgresql-9.2-1002.jdbc4.jar"								# Postgres jdbc driver
postgresDriver="postgresql-9.3-1102.jdbc41.jar"								# Postgres jdbc driver
sd="survey_definitions"														# Postgres config survey definitions db name
results="results"															# Postgres config results db name
tc_server_xml="/etc/tomcat7/server.xml"										# Tomcat config
tc_context_xml="/etc/tomcat7/context.xml"									# Tomcat config
tc_logging="/var/lib/tomcat7/conf/logging.properties"						# Tomcat config
a_config_dir="/etc/apache2/sites-available"									# Apache config	
a_config_conf="/etc/apache2/apache2.conf"									# Apache config
a_config_prefork_conf="/etc/apache2/mods-available/mpm_prefork.conf"		# Apache 2.4 config
a_default_xml="/etc/apache2/sites-available/default"						# Apache config
a_default_ssl_xml="/etc/apache2/sites-available/default-ssl"				# Apache config
upstart_dir="/etc/init"														# Subscriber config for Ubuntu 14.04
service_dir="/etc/systemd/system"											# Subscriber config for Ubuntu 16.04

echo "Setting up your server to run Smap"
echo "If you have already installed Smap and just want to upgrade you need to run deploy.sh and not this script"
echo 'This script has been specified to work on Ubuntu only and assumes you are not using Apache or Postgres currently on the server'
echo 'However Smap can be deployed on any variant of Linux, Mac and Windows'
echo 'If you have an existing database then you may need to apply database patched to complete the installation. The script will not overwrite an existing database.  However you should BACK UP ANY EXISTING DATA BEFORE UPGRADING'
read -r -p 'Do you want to continue? (y/n) ' choice
case $choice in
        n|N) break;;
        y|Y)

echo '##### 0. check configuration'
# Set flag if this is apache2.4
a24=`sudo apachectl -version | grep -c "2\.4"`
if [ $a24 -eq 0 ]; then	
	echo "%%%%%% Warning: Apache configuration files for Apache 2.4 will be installed. You may need to change these for your version of Apache web server"
fi

# Set flag for ubuntu 16.04
u1604=`lsb_release -r | grep -c "16\.04"`

echo '##### 1. Update Ubuntu'
sudo apt-get update
sudo apt-get upgrade -y
sudo sysctl -w kernel.shmmax=67068800		# 64MB of shared memory
sudo apt-get install ntp

echo '##### 2. Install Apache' 
sudo apt-get install apache2 apache2-doc apache2-utils -y
#sudo apt-get install apache2-mpm-worker apache2-doc apache2-utils -y
sudo apt-get install libaprutil1-dbd-pgsql -y
sudo a2enmod auth_digest
sudo a2enmod expires
sudo a2enmod authn_dbd
sudo a2enmod proxy
sudo a2enmod proxy_ajp
sudo a2enmod ssl
sudo a2enmod headers

sudo mkdir /var/www/smap
sudo mkdir /var/www/smap/fieldAnalysis
sudo mkdir /var/www/smap/OpenLayers

echo '##### 3. Install Tomcat'
sudo apt-get install tomcat7 -y

echo '##### 4. Get the Postgres JDBC driver'
if [ ! -f $postgresDriver ]
then
	wget $postgresDriverLocation/$postgresDriver
fi
sudo cp $postgresDriver $CATALINA_HOME/lib/
sudo cp misc_files/tomcat-jdbc.jar $CATALINA_HOME/lib/		# Add file missing from debian version of tomcat

echo '##### 5. Install Postgres / Postgis'

# Install Postgres for Ubuntu 16.04
if [ $u1604 -eq 1 ]; then
PGV=9.5
sudo apt-get install postgresql postgresql-contrib postgis postgresql-$PGV-postgis-2.2 -y
fi

# Install Postgres for Ubuntu 14.04
if [ $u1604 -eq 0 ]; then
PGV=9.3
sudo apt-get install postgresql postgresql-contrib postgis postgresql-$PGV-postgis-2.1 -y
sudo apt-get install postgresql-server-dev-9.3 -y
sudo apt-get install build-essential libxml2-dev -y
sudo apt-get install libgeos-dev libpq-dev libbz2-dev -y
fi

pg_conf="/etc/postgresql/$PGV/main/postgresql.conf"

echo '##### 6. Create folders for files'
sudo mkdir $filelocn
sudo mkdir $filelocn/attachments
sudo mkdir $filelocn/attachments/report
sudo mkdir $filelocn/attachments/report/thumbs
sudo mkdir $filelocn/media
sudo mkdir $filelocn/media/organisation
sudo mkdir $filelocn/templates
sudo mkdir $filelocn/templates/xls
sudo mkdir $filelocn/uploadedSurveys
sudo mkdir $filelocn/misc
sudo mkdir $filelocn/temp
sudo mkdir $filelocn/bin

sudo chown -R tomcat7 $filelocn
sudo chmod -R 0777 $filelocn/attachments
sudo chmod -R 0777 $filelocn/media
sudo chmod -R 0777 $filelocn/uploadedSurveys

# If auto configuration is set then copy the pre-set configuration files to their target destination

if [ "$config" != "manual" ]
then
	echo '##### 7. Copying configuration files'

	sudo service apache2 stop
	sudo service tomcat7 stop
	sudo service postgresql stop

	echo '# copy postgres conf file'
	sudo mv $pg_conf $pg_conf.bu
	sudo cp config_files/postgresql.conf.$PGV $pg_conf

	echo '# copy tomcat server file'
	sudo mv $tc_server_xml $tc_server_xml.bu
	sudo cp config_files/server.xml $tc_server_xml

	echo '# copy tomcat context file'
	sudo mv $tc_context_xml $tc_context_xml.bu
	sudo cp config_files/context.xml $tc_context_xml

	echo '# copy tomcat logging properties file'
	sudo mv $tc_logging $tc_logging.bu
	sudo cp config_files/logging.properties $tc_logging

	echo '# copy Apache cofiguration file'
	sudo mv $a_config_prefork_conf $a_config_prefork_conf.bu
	sudo cp config_files/mpm_prefork.conf $a_config_prefork_conf

	echo "Setting up Apache 2.4"
	sudo cp $a_config_dir/smap.conf $a_config_dir/smap.conf.bu
	sudo cp config_files/a24-smap.conf $a_config_dir/smap.conf

	sudo cp $a_config_dir/smap-ssl.conf $a_config_dir/smap-ssl.conf.bu
	sudo cp config_files/a24-smap-ssl.conf $a_config_dir/smap-ssl.conf
	
	# disable default config - TODO work out how to get Smap to coexist with existing Apache installations	
	sudo a2dissite 000-default
	sudo a2dissite default-ssl
	sudo a2ensite smap.conf
	sudo a2ensite smap-ssl.conf
	
	# Update the volatile configuration setting, only this should change after initial installation
	chmod +x apacheConfig.sh
	./apacheConfig.sh

	echo '# copy subscriber upstart files'
	if [ $u1604 -eq 1 ]; then
		sudo cp config_files/subscribers.service $service_dir
		sudo chmod 664 $service_dir/subscribers.service
		sudo cp config_files/subscribers_fwd.service $service_dir
		sudo chmod 664 $service_dir/subscribers_fwd.service
		
		sudo systemctl enable subscribers.service
		sudo systemctl enable subscribers_fwd.service
	fi
	
	if [ $u1604 -eq 0 ]; then
		sudo cp config_files/subscribers.conf $upstart_dir
		sudo cp config_files/subscribers_fwd.conf $upstart_dir
	fi

	echo '# update bu.sh file'
	sudo cp bu.sh ~postgres/bu.sh
	sudo chown postgres ~postgres/bu.sh

	echo '# update re.sh file'
	sudo cp re.sh ~postgres/re.sh
	sudo chown postgres ~postgres/re.sh


else
	echo '##### 7. Skipping auto configuration'

fi

echo '##### 9. Create user and databases'
sudo service postgresql start
sudo -u postgres createuser -S -D -R ws
echo "alter user ws with password 'ws1234'" | sudo -u postgres psql

echo '##### 10. Create $sd database'

if [ "$force" = "force" ]
then
	echo "drop database $sd;" | sudo -u postgres psql
fi

sd_exists=`sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -w $sd | wc -l`
if [ "$sd_exists"  = "0" ]
then
echo 'survey_definitions database does not exist'
sudo -u postgres createdb -E UTF8 -O ws $sd
echo "CREATE EXTENSION postgis;" | sudo -u postgres psql -d $sd 
echo "ALTER TABLE geometry_columns OWNER TO ws; ALTER TABLE spatial_ref_sys OWNER TO ws; ALTER TABLE geography_columns OWNER TO ws;" | sudo -u postgres psql -d $sd
sudo -u postgres psql -f setupDb.sql -d $sd
else
echo "==================> $sd database already exists.  Apply patches if necessary, to upgrade it."
fi

echo '##### 11. Create $results database'

if [ "$force" = "force" ]
then
	echo "drop database $results;" | sudo -u postgres psql
fi

results_exists=`sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -w $results | wc -l`
if [ "$results_exists"  = "0" ]
then
echo 'results database does not exist'
sudo -u postgres createdb -E UTF8 -O ws $results
echo "CREATE EXTENSION postgis;" | sudo -u postgres psql -d $results
sudo -u postgres echo "ALTER TABLE geometry_columns OWNER TO ws; ALTER TABLE spatial_ref_sys OWNER TO ws; ALTER TABLE geography_columns OWNER TO ws;" | sudo -u postgres psql -d $results
sudo -u postgres psql -f resultsDb.sql -d $results

cd ../geospatial
echo '# adding countries shape files'
sudo -u postgres shp2pgsql -s 4326 -I world_countries_boundary_file_world_2002.shp | sudo -u postgres psql -d $results
sudo -u postgres echo "alter table world_countries_boundary_file_world_2002 owner to ws;" | sudo -u postgres psql -d $results
cd ../install

else
echo "==================> $results database already exists.  Apply patches if necessary, to upgrade it."
fi

echo '##### 12. Setup subscribers'
sudo rm -rf /smap_bin
sudo mkdir /smap_bin
sudo mkdir /var/log/subscribers
sudo cp subscribers.sh /smap_bin
sudo chmod -R 777 /var/log/subscribers
sudo chmod -R +x /var/log/subscribers
chmod +x /smap_bin/subscribers.sh
sudo mkdir /smap_bin/resources
sudo mkdir /smap_bin/resources/css


echo '##### 13. Set up deployment script'
chmod +x ../deploy/deploy.sh

echo '##### 14. Add imagemagick,ffmpeg to generate thumbnails'
if [ $(cat /etc/*-release | grep "DISTRIB_CODENAME=" | cut -d "=" -f2) = "trusty" ];
then  
sudo add-apt-repository ppa:mc3man/trusty-media  && sudo apt-get update -y
fi

sudo apt-get install imagemagick -y
sudo apt-get install ffmpeg -y  --force-yes
sudo apt-get install flvtool2 -y --force-yes

echo '##### 15. PHP Install Skipped'
#sudo apt-get install php5-json -y
#sudo apt-get install libpcre3-dev -y
#sudo apt-get install php5 php5-pgsql libapache2-mod-php5 -y

echo '##### 16. Install Python for xls form translations'
sudo apt-get install python-dev -y
sudo apt-get install libxml2-dev -y
sudo apt-get install libxslt-dev
sudo apt-get install libxslt1-dev -y
sudo apt-get install git -y
sudo apt-get install python-setuptools -y
sudo easy_install pip
sudo pip install setuptools --no-use-wheel --upgrade
sudo pip install xlrd 
sudo pip install -e git+https://github.com/UW-ICTD/pyxform.git@master#egg=pyxform 
sudo cp -r src/pyxform/pyxform/ /smap_bin
sudo sed -i "s/from pyxform import constants/import constants/g" /smap_bin/pyxform/survey.py

echo '##### 17. Backups'
sudo mkdir ~postgres/backups
sudo mkdir ~postgres/restore
sudo chmod +x ~postgres/bu.sh ~postgres/re.sh
sudo chown postgres ~postgres/bu.sh ~postgres/re.sh ~postgres/backups ~postgres/restore

echo '##### 18. install PHP pecl_http extension skipped'
#sudo apt-get install php5 libapache2-mod-php5 php5-xsl php5-curl git php-apc php5-mcrypt -y
#sudo apt-get install libcurl3 php5-dev libcurl4-gnutls-dev libmagic-dev php-pear -y
#sudo apt-get install libcurl3-openssl-dev -y
#sudo printf "\n" | sudo pear upgrade
#sudo printf "\n" | sudo /usr/bin/pecl install pecl_http-1.7.6
#echo "extension=http.so" | sudo tee -a /etc/php5/apache2/php.ini

echo '##### 19. Update miscelaneous file configurations'

echo '##### Add file location to tomcat configuration'

sudo cp /var/lib/tomcat7/conf/web.xml /var/lib/tomcat7/conf/web.xml.bu

sudo sed -i "/<\/web-app>/i \
<context-param>\n\
   <param-name>au.com.smap.files<\/param-name>\n\
   <param-value>$filelocn</param-value>\n\
<\/context-param>" /var/lib/tomcat7/conf/web.xml

echo '##### Add shared memory setting to sysctl.conf'

sudo cp /etc/sysctl.conf /etc/sysctl.conf.bu
echo "kernel.shmmax=67068800" | sudo tee -a /etc/sysctl.conf 
# TODO add "-Djava.net.preferIPv4Stack=true" to JAVA_OPTS

echo '##### Increase shared memory available to tomcat'
sudo cp /etc/default/tomcat7  /etc/default/tomcat7.bu
sudo sed -i "s#-Xmx128m#-Xmx512m#g" /etc/default/tomcat7

echo '##### Allow logon to postgres authenticated by md5 - used to export shape files'
# This could be better written as it is not idempotent, each time the install script is run an additional line will be changed
sudo mv /etc/postgresql/$PGV/main/pg_hba.conf /etc/postgresql/$PGV/main/pg_hba.conf.bu
sudo awk 'BEGIN{doit=0;}/# "local"/{doit=1;isdone=0;}{if(doit==1){isdone=sub("peer","md5",$0);print;if(isdone==1){doit=0}}else{print}}' /etc/postgresql/$PGV/main/pg_hba.conf.bu > x
sudo mv x /etc/postgresql/$PGV/main/pg_hba.conf

echo '##### . Start the servers'
sudo service postgresql start
sudo service tomcat7 start
sudo service apache2 start

echo '##### 20. Enable export to shape files, kmz files and pdf files'
sudo apt-get install zip -y
sudo apt-get install gdal-bin -y
sudo apt-get install ttf-dejavu -y

# Add a file containing the version number
echo "161202" > ~/smap_version

echo '##### 21. Deploy Smap'
cd ../deploy
chmod +x deploy.sh
sudo ./deploy.sh
cd ../install

esac

