SDNRouterAndLoadBalancer
========================

Programming Assignment 3 for the CS 640 - Introduction to Computer Networks


It is necessary to download the project "floodlight-plus" to the same directory of the Eclipse project.

Run the commans below.

git clone https://bitbucket.org/sdnhub/floodlight-plus.git

cd ~/project3

ln -s ../floodlight-plus

patch -p1 < floodlight.patch

cd project3/floodlight-plus

./setup-eclipse


- IMPORTANT

Maybe it can be required to change the Java Build Path in the 'project3'.
