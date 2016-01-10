#!/bin/sh

echo "Setting up Apache 2.4"
a_config_dir="/etc/apache2/sites-available"	

sudo cp $a_config_dir/smap-volatile.conf $a_config_dir/smap-volatile.conf.bu
sudo cp config_files/a24-smap-volatile.conf $a_config_dir/smap-volatile.conf

sudo service apache2 reload

