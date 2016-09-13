xquery version "3.0";

import module namespace transform = "transform" at "transform.xqm";

let $source-path := "/home/georgi/Desktop/dossin-collections.xml"
let $target-path := "/home/georgi/Desktop/ead-dossin/"
let $configuration-path := "/home/georgi/git/data-transformations/XQuery/configuration-kazerne_dossin.tsv"

let $source-document := fn:doc($source-path)
for $target-document at $count in transform:transform($source-document, $configuration-path, "urn:isbn:1-931666-22-9")
return file:write(fn:concat($target-path, $count, ".xml"), $target-document, map { "omit-xml-declaration": "no" })
