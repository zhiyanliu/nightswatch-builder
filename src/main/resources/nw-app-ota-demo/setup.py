#!/usr/bin/python3
# -*- coding: utf-8 -*-

import os
import sys


# both two will be injected by Night's Watch - Builder, DON"T edit manually.
credentials_pkg_url = "<CREDENTIALS_PACKAGE_URL>"
nw_ranger_pkg_url = "<NW_RANGER_PACKAGE_URL>"

# step1, download asset

credentials_pkg_path = "/tmp/credentials.zip"
nw_ranger_pkg_path = "/tmp/nightswatch-ranger.tar.gz"
nw_ranger_home = "/opt/nightswatch-ranger"

# download device root CA, certificates and keys
rc = os.system("curl -o %s -fs '%s'" % (credentials_pkg_path, credentials_pkg_url))
if 0 != rc:
    sys.exit(rc)

# download Night's Watch - Ranger package
rc = os.system("curl -o %s -fs '%s'" % (nw_ranger_pkg_path, nw_ranger_pkg_url))
if 0 != rc:
    sys.exit(rc)

# step2, install asset

# un-package Night's Watch - Ranger package
rc = os.system("tar zxf %s --no-same-owner -C /opt" % nw_ranger_pkg_path)
if 0 != rc:
    sys.exit(rc)

# copy device root CA, certificates and keys
rc = os.system("unzip -o %s -d %s/certs/p1" % (credentials_pkg_path, nw_ranger_home))
if 0 != rc:
    sys.exit(rc)

# step3, launch Ranger daemon

bin = "%s/ranger" % nw_ranger_home
os.execl(bin, bin)
