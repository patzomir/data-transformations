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

(: read configurations :)
let $formatting := csv:parse(file:read-text($formatting-path), $csv_options)
let $translations := csv:parse(file:read-text($translations-path), $csv_options)

(: transform each XML document to HTML :)
let $index-items := for $document-name in file:list($document-dir, fn:false(), "*.xml,*.XML")
  let $document-path := fn:concat($document-dir, $document-name)
  let $html := ead2html:document-to-html($document-path, $stylesheet-location, $formatting, $translations, $language)
  
  (: write the HTML to file :)
  let $html-name := fn:replace($document-name, ".(xml|XML)$", ".html")
  let $html-path := fn:concat($html-dir, $html-name)
  let $void := file:write($html-path, $html, $html_options)
  
  (: return a link to the HTML file and the number of errors in it :)
  let $num-errors := fn:count($html/html/body/div[@class = "table-of-contents"]/a)
  return <div class="index-item { if ($num-errors > 0) then "with-errors" else "without-errors" }">
      <a href="{ $html-name }">{ $document-name }</a>
      <span class="num-errors">{ $num-errors }</span>
    </div>

(: construct the index :)
let $index := document {
  <html>
    <head>
      <link rel="stylesheet" href="{ $stylesheet-location }" />
      <title>EAD Preview Index</title>
    </head>
    <body>
      <div class="index">
        { $index-items }
      </div>
    </body>
  </html>
}

(: write the index to file :)
let $index-path := fn:concat($html-dir, "index.html")
return file:write($index-path, $index, $html_options)
