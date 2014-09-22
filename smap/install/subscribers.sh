#!/bin/sh
cd /usr/bin/smap
java -jar subscribers.jar $1 $2 $3 >> /var/log/subscribers/subscriber_$1_$3.log 2>&1
