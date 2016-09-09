xquery version "3.0";

import module namespace ead = "ead" at "ead.xq";

(: parameters :)
let $input-path := "/home/georgi/Desktop/2016.08.12.document.json"
let $output-path := "/home/georgi/Desktop/ead-ushmm.xml"
let $mapping-path := "/home/georgi/git/data-transformations/XQuery/mapping-ushmm.tsv"

let $input := json:parse(file:read-text($input-path), map { "format":"attributes" })
let $output := ead:ead($input, $mapping-path)
return file:write($output-path, $output, map { "omit-xml-declaration":"no" })
