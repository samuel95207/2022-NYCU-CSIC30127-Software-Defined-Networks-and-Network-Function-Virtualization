#!/bin/sh

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

ln -s /etc/apparmor.d/usr.sbin.dhcpd /etc/apparmor.d/disable/
apparmor_parser -R /etc/apparmor.d/usr.sbin.dhcpd