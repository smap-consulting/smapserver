#!/bin/sh

echo "Setting up Apache 2.4"
sudo cp config_files/a24-smap-volatile.conf $a_config_dir/smap-volatile.conf

sudo service apache2 reload

