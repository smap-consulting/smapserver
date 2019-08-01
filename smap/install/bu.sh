#!/bin/sh
export PATH=/usr/local/bin:$PATH
export HOME=/var/lib/postgresql

# back up the databases on this host
file="smap_bu.tgz"
final_file="`cat ~ubuntu/hostname`-`date +\%Y-\%m-\%d`-smap_bu.tgz.gpg"
rm -rf backups/*
rm $file
rm $file.gpg

# Dump databases
pg_dump -c -Fc survey_definitions > backups/sd.dmp
pg_dump -c -Fc results > backups/results.dmp
pg_dump survey_definitions -c > backups/survey_definitions.sql
pg_dump results -c > backups/results.sql

tar -zcf $file backups/*
rm -rf backups/*

# Encrypt
echo `cat passwordfile` | gpg --batch -q --passphrase-fd 0 --cipher-algo AES256 -c $file
rm $file

# Copy encrypted file to s3
echo "copy to s3"
aws s3 cp $file.gpg s3://smap2/$final_file
rm $file.gpg

# Synchronise other files
bucket=`cat ~ubuntu/bucket`
aws s3 sync /smap s3://$bucket --exclude "temp/*"
