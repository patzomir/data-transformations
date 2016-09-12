xquery version "3.0";

import module namespace transform = "transform" at "transform.xqm";

let $source-path := "C:\Users\Georgi\Downloads\dossin-collections.xml"
let $target-path := "C:\Users\Georgi\Downloads\dossin-ead\"
let $configuration-path := "C:\Users\Georgi\git\data-transformations\XQuery\configuration-kazerne_dossin.tsv"

let $source-document := fn:doc($source-path)
for $target-document at $count in transform:transform($source-document, $configuration-path)
return file:write(fn:concat($target-path, $count, ".xml"), $target-document, map { "omit-xml-declaration": "no" })
