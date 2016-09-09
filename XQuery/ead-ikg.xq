xquery version "3.0";

import module namespace ead = "ead" at "ead.xq";

(: parameters :)
let $input-main-path := "/home/georgi/Desktop/ikg-main.tsv"
let $input-meta-path := "/home/georgi/Desktop/ikg-meta.tsv"
let $output-path := "/home/georgi/Desktop/ead-ikg.xml"
let $mapping-path := "/home/georgi/git/data-transformations/XQuery/mapping-ikg.tsv"

let $input := document
  {
    <root>
      <main>
      {
        csv:parse(file:read-text($input-main-path), map { "separator":"tab", "header":"yes", "quotes":"no" })
      }
      </main>
      <meta>
      {
        csv:parse(file:read-text($input-meta-path), map { "separator":"tab", "header":"yes", "quotes":"no" })
      }
      </meta>
    </root>
  }

return file:write($output-path, ead:ead($input, $mapping-path), map { "omit-xml-declaration":"no" })
