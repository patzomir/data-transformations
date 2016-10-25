xquery version "3.0";

import module namespace transform = "transform" at "transform.xqm";

let $source-path := "/home/georgi/Downloads/collections-formatted.xml"
let $target-path := "/home/georgi/Downloads/kazerne-dossin-ead/"
let $configuration-path := "/home/georgi/git/data-transformations/XQuery/configuration-kazerne_dossin.tsv"
let $structure-path := "/home/georgi/git/data-transformations/XQuery/ead2002.struct"

let $namespaces := map { "": "urn:isbn:1-931666-22-9", "xlink": "http://www.w3.org/1999/xlink", "xsi": "http://www.w3.org/2001/XMLSchema-instance" }
let $source-document := fn:doc($source-path)

for $target-document at $count in transform:transform($source-document, $configuration-path, $structure-path, $namespaces)
return file:write(fn:concat($target-path, $count, ".xml"), $target-document, map { "omit-xml-declaration": "no" })
