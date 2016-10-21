xquery version "3.0";

import module namespace ead2html = "ead2html" at "ead2html.xqm";

(: serialization options :)
let $csv_options := map { "separator": "tab", "header": "yes" }
let $html_options := map { "method": "html", "media-type": "text/html", "include-content-type": "yes" }

(: resource locations :)
let $stylesheet-location := "../ead.css"
let $formatting-path := "/home/georgi/git/data-transformations/XQuery/formatting.tsv"
let $translations-path := "/home/georgi/git/data-transformations/XQuery/labels.tsv"

(: script parameters :)
let $language := "de"
let $document-dir := "/home/georgi/schem/injected/"
let $html-dir := "/home/georgi/schem/output/"

let $formatting := csv:parse(file:read-text($formatting-path), $csv_options)
let $translations := csv:parse(file:read-text($translations-path), $csv_options)

for $document-path-relative in file:list($document-dir, fn:false(), "*.xml,*.XML")
  let $document-path := fn:concat($document-dir, $document-path-relative)
  let $html-path := fn:concat($html-dir, fn:replace($document-path-relative, ".(xml|XML)$", ".html"))
  let $html := ead2html:document-to-html($document-path, $stylesheet-location, $formatting, $translations, $language)
  return file:write($html-path, $html, $html_options)
