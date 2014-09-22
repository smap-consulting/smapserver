#!/bin/sh
# Change the name of the smap server
#
# DEPRECATED
#
default="/etc/apache2/sites-available/default"
default_ssl="/etc/apache2/sites-available/default-ssl"

if [ $# -lt 2 ] || [ $# -gt 3 ]
then
        echo "usage: sudo $0 oldname newname"
        exit;
fi

echo "Changing domain name of server from $1 to $2"
echo 'This script should only be used if you have a single apache virtual host running the smap server'
read -r -p 'Do you want to continue? (y/n) ' choice
case $choice in
        n|N) break;;
        y|Y)

        cp $default $default.bu.renamed
        cp $default_ssl $default_ssl.bu.renamed

        cat $default.bu.renamed | sed "s/$1/$2/g" > $default
        cat $default_ssl.bu.renamed | sed "s/$1/$2/g" > $default_ssl

        mv /ebs1/servers/$1 /ebs1/servers/$2

        chown -R tomcat7 /ebs1/servers
        chmod -R 0777 /ebs1/servers/$2/attachments
        chmod -R 0777 /ebs1/servers/$2/media

	service apache2 restart
esac

