#!/bin/sh
##### Installation script for setting up basic Smap server without ssl or virtual hosts

config="auto"
clean="true"
filelocn="/smap"

CATALINA_HOME=/usr/share/tomcat7
postgresDriverLocation="http://jdbc.postgresql.org/download"				# Postgres jdbc driver
#postgresDriver="postgresql-9.2-1002.jdbc4.jar"								# Postgres jdbc driver
postgresDriver="postgresql-9.3-1102.jdbc41.jar"								# Postgres jdbc driver
PGV=9.3																		# Postgres version
#PGSV=2.1																	# Postgis version
pg_conf="/etc/postgresql/$PGV/main/postgresql.conf"							# Postgres config
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
upstart_dir="/etc/init"														# Subscriber config

echo "Setting up your server to run Smap"
echo "If you have already installed Smap and just want to upgrade you need to run deploy.sh and not this script"
echo 'This script has been specified to work on Ubuntu only and assumes you are not using Apache or Postgres currently on the server'
echo 'However Smap can be deployed on any variant of Linux, Mac and Windows'
echo 'If you have an existing database then you may need to apply database patched to complete the installation. The script will not overwrite an existing database.  However you should BACK UP ANY EXISTING DATA BEFORE UPGRADING'
read -r -p 'Do you want to continue? (y/n) ' choice
case $choice in
        n|N) break;;
        y|Y)


#echo '##### 0. Get repository for postgis 2.1'
#
# The following lines are taken from http://wiki.postgresql.org/wiki/Apt
# If there are errors you should manually install postgres and postgis
#CODENAME=$(lsb_release -cs 2>/dev/null)
#echo "Writing /etc/apt/sources.list.d/pgdg.list ..."
#sudo tee -a /etc/apt/sources.list.d/pgdg.list <<EOF
#deb http://apt.postgresql.org/pub/repos/apt/ $CODENAME-pgdg main
#EOF
#wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

echo '##### 1. Update Ubuntu'
sudo apt-get update
sudo apt-get upgrade -y
sudo sysctl -w kernel.shmmax=67068800		# 64MB of shared memory

echo '##### 2. Install Apache' 
# Note install pre-fork as mod-php is not compatable with mpm-worker
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
sudo a2ensite default-ssl

sudo mkdir /var/www/smap
sudo mkdir /var/www/smap/fieldAnalysis
sudo mkdir /var/www/smap/OpenLayers

# Set flag if this is apache2.4
a24=`sudo apachectl -version | grep -c "2\.4"`

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

#sudo apt-get install language-pack-en-base -y
sudo apt-get install postgresql postgresql-contrib postgis postgresql-$PGV-postgis-2.1 -y
#sudo apt-get install postgresql-$PGV-postgis-scripts -y
sudo apt-get install postgresql-server-dev-$PGV -y
sudo apt-get install build-essential libxml2-dev -y
sudo apt-get install libgeos-dev libpq-dev libbz2-dev -y

echo '##### 6. Create folders for files'
sudo mkdir $filelocn
sudo mkdir $filelocn/attachments
sudo mkdir $filelocn/attachments/report
sudo mkdir $filelocn/attachments/report/thumbs
sudo mkdir $filelocn/media
sudo mkdir $filelocn/templates
sudo mkdir $filelocn/templates/xls
sudo mkdir $filelocn/uploadedSurveys
sudo mkdir $filelocn/temp

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
	sudo cp config_files/postgresql.conf $pg_conf

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
	if [ $a24 -eq 0 ]; then	
		sudo mv $a_config_conf $a_config_conf.bu
		sudo cp config_files/apache2.conf $a_config_conf
	fi
	if [ $a24 -eq 1 ]; then	
		sudo mv $a_config_prefork_conf $a_config_prefork_conf.bu
		sudo cp config_files/mpm_prefork.conf $a_config_prefork_conf
	fi

	echo '# copy apache default file'

        rm -f config_files/*fix*
	if [ $a24 -eq 0 ]; then	
		echo "Setting up Apache 2.2"
		sudo mv $a_default_xml $a_default_xml.bu
		cat config_files/default | sed "s#{your_files}#$filelocn#g" > config_files/default.fix2
		sudo cp config_files/default.fix2 $a_default_xml

		sudo mv $a_default_ssl_xml $a_default_ssl_xml.bu
		cat config_files/default-ssl | sed "s#{your_files}#$filelocn#g" > config_files/default-ssl.fix2
		sudo cp config_files/default-ssl.fix2 $a_default_ssl_xml
	fi
	if [ $a24 -eq 1 ]; then	
		echo "Setting up Apache 2.4"
		cat config_files/a24_default | sed "s#{your_files}#$filelocn#g" > config_files/default.fix2
		sudo cp $a_config_dir/000-default.conf $a_config_dir/000-default.conf.bu
		sudo cp config_files/default.fix2 $a_config_dir/000-default.conf

		cat config_files/a24_default-ssl | sed "s#{your_files}#$filelocn#g" > config_files/default-ssl.fix2
		sudo cp $a_config_dir/default-ssl.conf $a_config_dir/default-ssl.conf.bu
		sudo cp config_files/default-ssl.fix2 $a_config_dir/default-ssl.conf

		sudo a2ensite 000-default
		sudo a2ensite default-ssl
	fi

	echo '# copy subscriber upstart files'
	sed -i "s#{your_files}#$filelocn#g" config_files/subscribers.conf
	sudo cp config_files/subscribers.conf $upstart_dir
	sed -i "s#{your_files}#$filelocn#g" config_files/subscribers_fwd.conf
	sudo cp config_files/subscribers_fwd.conf $upstart_dir

	echo '# update bu.sh file'
	cat bu.sh | sed "s#{your_files}#$filelocn#g" > bu.sh.fix
	sudo cp bu.sh.fix ~postgres/bu.sh
	sudo chown postgres ~postgres/bu.sh

	echo '# update re.sh file'
	cat re.sh | sed "s#{your_files}#$filelocn#g" > re.sh.fix
	sudo cp re.sh.fix ~postgres/re.sh
	sudo chown postgres ~postgres/re.sh


else
	echo '##### 7. Skipping auto configuration'

fi

echo '##### 9. Create user and databases'
sudo service postgresql start
sudo -u postgres createuser -S -D -R ws
echo "alter user ws with password 'ws1234'" | sudo -u postgres psql

echo '##### 10. Create $sd database'
sd_exists=`sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -w $sd | wc -l`
if [ "$sd_exists"  = "0" ]
then
echo 'survey_definitions table does not exist'
sudo -u postgres createdb -E UTF8 -O ws $sd
echo "CREATE EXTENSION postgis;" | sudo -u postgres psql -d $sd 
#sudo -u postgres psql -f /usr/share/postgresql/$PGV/contrib/postgis-$PGSV/postgis.sql -q -d $sd
#sudo -u postgres psql -f /usr/share/postgresql/$PGV/contrib/postgis-$PGSV/spatial_ref_sys.sql -q -d $sd
echo "ALTER TABLE geometry_columns OWNER TO ws; ALTER TABLE spatial_ref_sys OWNER TO ws; ALTER TABLE geography_columns OWNER TO ws;" | sudo -u postgres psql -d $sd
sudo -u postgres psql -f setupDb.sql -d $sd
else
echo "==================> $sd database already exists.  Apply patches if necessary, to upgrade it."
fi

echo '##### 11. Create $results database'
results_exists=`sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -w $results | wc -l`
if [ "$results_exists"  = "0" ]
then
echo 'results table does not exist'
sudo -u postgres createdb -E UTF8 -O ws $results
echo "CREATE EXTENSION postgis;" | sudo -u postgres psql -d $results
#sudo -u postgres psql -f /usr/share/postgresql/$PGV/contrib/postgis-$PGSV/postgis.sql -q -d $results
#sudo -u postgres psql -f /usr/share/postgresql/$PGV/contrib/postgis-$PGSV/spatial_ref_sys.sql -q -d $results
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
sudo rm -rf /usr/bin/smap
sudo mkdir /usr/bin/smap
sudo mkdir /var/log/subscribers
sudo cp subscribers.sh /usr/bin/smap
sudo chmod -R 777 /var/log/subscribers
sudo chmod -R +x /var/log/subscribers
chmod +x /usr/bin/smap/subscribers.sh

echo '##### 13. Set up deployment script'
chmod +x ../deploy/deploy.sh

echo '##### 14. Add imagemagick,ffmpeg to generate thumbnails'
if [ $(cat /etc/*-release | grep "DISTRIB_CODENAME=" | cut -d "=" -f2) = "trusty" ];
then  
sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu trusty main'  && sudo add-apt-repository 'deb  http://ppa.launchpad.net/jon-severinsson/ffmpeg/ubuntu saucy main'  && sudo apt-get update -y
fi

sudo apt-get install imagemagick -y
sudo apt-get install ffmpeg -y  --force-yes
sudo apt-get install flvtool2 -y --force-yes

echo '##### 15. Install PHP'
sudo apt-get install php5-json -y
sudo apt-get install libpcre3-dev -y
sudo apt-get install php5 php5-pgsql libapache2-mod-php5 -y

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
sudo cp -r src/pyxform/pyxform/ /usr/bin/smap/

echo '##### 17. Backups'
sudo mkdir ~postgres/backups
sudo mkdir ~postgres/restore
sudo chmod +x ~postgres/bu.sh ~postgres/re.sh
sudo chown postgres ~postgres/bu.sh ~postgres/re.sh ~postgres/backups ~postgres/restore

echo '##### 18. install PHP pecl_http extension'
sudo apt-get install php5 libapache2-mod-php5 php5-xsl php5-curl git php-apc php5-mcrypt -y
sudo apt-get install libcurl3 php5-dev libcurl4-gnutls-dev libmagic-dev php-pear -y
sudo apt-get install libcurl3-openssl-dev -y
sudo printf "\n" | sudo pear upgrade
sudo printf "\n" | sudo /usr/bin/pecl install pecl_http-1.7.6
echo "extension=http.so" | sudo tee -a /etc/php5/apache2/php.ini

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
echo "1409" > ~/smap_version

echo '##### 21. Deploy Smap'
cd ../deploy
chmod +x deploy.sh
sudo ./deploy.sh $mysql_password
cd ../install

esac

