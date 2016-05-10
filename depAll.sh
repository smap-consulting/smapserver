#!/bin/sh
cd smapServer
./dep.sh
cd ..

cd fieldAnalysis
./dep.sh
cd ..

cd tasks
./dep.sh
cd ..

cd fieldManagerClient
./dep.sh
cd ..
