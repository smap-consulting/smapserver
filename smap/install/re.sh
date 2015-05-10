#!/bin/sh
file="smap_bu.tgz"
gpgfile="$file.gpg"
rm -rf restore/*

# Decrypt
echo `cat passwordfile` | gpg  --batch -q --passphrase-fd 0 -o $file  -d $gpgfile

tar -xzf $file -C restore


