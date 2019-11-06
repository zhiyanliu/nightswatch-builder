#!/usr/bin/python3
# -*- coding: utf-8 -*-

credentials_pkg_url = "<CREDENTIALS_PACKAGE_URL>"
nw_ranger_pkg_url = "<NW_RANGER_PACKAGE_URL>"


f = open("/tmp/hello",'w')
f.write("credentials_pkg_url = %s\n" % credentials_pkg_url)
f.write("nw_ranger_pkg_url = %s\n" % nw_ranger_pkg_url)
f.close()
