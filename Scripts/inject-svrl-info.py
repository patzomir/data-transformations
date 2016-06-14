#!/usr/bin/env python

import sys
from lxml import etree

# adjust xpath expressions to work correctly
def fix_path(path):
    path = path.replace('[namespace-uri()=\'urn:isbn:1-931666-22-9\']', '')
    path = path.replace('/*:', '/ead:')
    return path

# check arguments
if len(sys.argv) != 4:
    sys.exit('USAGE: ' + sys.argv[0] + ' <input xml> <input svrl> <output xml>')

# map of namespaces
namespaces = { 'ead': 'urn:isbn:1-931666-22-9', 'svrl': 'http://purl.oclc.org/dsdl/svrl' }

# parse input files
xml = etree.parse(sys.argv[1])
svrl = etree.parse(sys.argv[2])

# iterate through failed tests
for fail in svrl.xpath('/svrl:schematron-output/svrl:failed-assert', namespaces=namespaces):
    text = fail.xpath('svrl:text/text()', namespaces=namespaces)[0]
    role = fail.get('role')
    path = fail.get('location')
    path = fix_path(path)
    
    # add svrl info as attributes of target elements
    for target in xml.xpath(path, namespaces=namespaces):
        target.set('svrl_text', text)
        target.set('svrl_role', role)

# write resulting xml
xml.write(sys.argv[3], encoding='UTF-8', pretty_print=True, xml_declaration=True)

