xquery version "3.0";

declare function local:legalize-text(
  $text as xs:string
) as xs:string {
  fn:replace($text, "[\p{IsC}]", "")
};

(: get the translation for an element or attribute in the specified language :)
declare function local:get-translation(
  $node as node(),
  $translations as document-node(),
  $language as xs:string
) as xs:string {
  let $node-name := fn:local-name($node)
  let $translation := fn:zero-or-one(
    $translations/csv/record[node-name/text() = $node-name]/*[fn:local-name() = $language]/text()
  )
  
  (: if there is a translation, return it; otherwise, return the node name :)
  return if ($translation) then $translation else $node-name
};

(: generate a tooltip for an element :)
declare function local:generate-tooltip(
  $element as element()
) as element()? {
  let $text := $element/@svrl_text
  let $role := $element/@svrl_role
  let $docu := $element/@svrl_docu
  
  (: only create tooltip if there is at least text :)
  return if ($text) then
    <div class="tooltip">
      { if ($role) then <span class="role">{ fn:data($role) }</span> else () }
      <span class="text">{ fn:data($text) }</span>
      { if ($docu) then <a class="docu" href="{ fn:data($docu) }">{ fn:data($docu) }</a> else () }
    </div>
  else ()
};

(: get the formatting information for an element :)
declare function local:get-formatting-record(
  $element as element(),
  $formatting as document-node()
) as element()? {
  let $formatting-records := $formatting/csv/record[source-element/text() = fn:local-name($element)]
  
  (: get the record that matches by element, attribute and atttribute value :)
  let $matches-by-value := fn:zero-or-one(
    for $attribute in $element/@*
    return $formatting-records[source-attribute/text() = fn:local-name($attribute) and source-attribute-value/text() = fn:data($attribute)]
  )
  
  (: get the record that matches by element and attribute :)
  let $matches-by-attribute := fn:zero-or-one(
    for $attribute in $element/@*
    return $formatting-records[source-attribute/text() = fn:local-name($attribute) and fn:not(source-attribute-value/text())]
  )
  
  (: get the record that matches by element :)
  let $matches-by-element := fn:zero-or-one(
    $formatting-records[fn:not(source-attribute/text()) and fn:not(source-attribute-value/text())]
  )
  
  (: return the best-matching record :)
  return if ($matches-by-value) then $matches-by-value
    else if ($matches-by-attribute) then $matches-by-attribute
    else if ($matches-by-element) then $matches-by-element
    else ()
};

(: check if an element is a formatting element :)
declare function local:is-formatting-element(
  $element as element(),
  $formatting as document-node()
) as xs:boolean {
  if (local:get-formatting-record($element, $formatting)) then fn:true() else fn:false()
};

(: transform an attribute to HTML :)
declare function local:attribute-to-html(
  $attribute as attribute(),
  $translations as document-node(),
  $language as xs:string
) as element() {
  <div class="attribute { fn:local-name($attribute) }">
    <span class="name">{ local:get-translation($attribute, $translations, $language) }</span>
    <span class="value">{ local:legalize-text(fn:data($attribute)) }</span>
  </div>
};

(: transform a content node (formatting element or text node) to HTML :)
declare function local:content-to-html(
  $content as node(),
  $formatting as document-node()
) as element()? {
  typeswitch($content)
    
    (: if the content node is a text node, simply return it :)
    case text() return <span class="text">{ local:legalize-text($content) }</span>
    
    (: if the content node is an element, format it and return it :)
    case element() return
      let $formatting-record := local:get-formatting-record($content, $formatting)
      let $text := local:legalize-text(fn:data($content))
      return if ($formatting-record and $text)
        then element { $formatting-record/target-element/text() } {
          attribute class { "formatting-element" },
          attribute style { $formatting-record/target-style/text() },
          $text
        }
        else ()
    
    (: should not happen :)
    default return ()
};

(: transform an element to HTML :)
declare function local:element-to-html(
  $element as element(),
  $formatting as document-node(),
  $translations as document-node(),
  $language as xs:string
) as element()? {
  let $tooltip := local:generate-tooltip($element)
  let $attributes := $element/@*[fn:not(fn:starts-with(fn:local-name(), "svrl_"))]
  let $contents := $element/text() | $element/*[local:is-formatting-element(., $formatting)]
  let $children := $element/*[fn:not(local:is-formatting-element(., $formatting))]
  
  (: only create non-empty elements :)
  return if ($tooltip or $attributes or $contents or $children)
    then <div class="element { fn:local-name($element) }" id="{ random:uuid() }">
        <div class="meta { if ($tooltip) then "with-tooltip" else "without-tooltip" }">
          <span class="name">{ local:get-translation($element, $translations, $language) }</span>
          { $tooltip }
        </div>
        {
          if ($attributes) then <div class="attributes">
              { for $attribute in $attributes return local:attribute-to-html($attribute, $translations, $language) }
            </div>
          else ()
        }
        {
          if ($contents) then <div class="contents">
              { for $content in $contents return local:content-to-html($content, $formatting) }
            </div>
          else ()
        }
        { for $child in $children return local:element-to-html($child, $formatting, $translations, $language) }
      </div>
    else ()
};

(: generate a table of contents :)
declare function local:generate-table-of-contents(
  $root as element()*
) as element()? {
  <div class="table-of-contents">
    {
      for $element-with-tooltip in $root//div[@id and div/div/@class = "tooltip"]
      return <a href="#{ fn:data($element-with-tooltip/@id) }">{ $element-with-tooltip/div/span[@class = "name"]/text() }</a>
    }
  </div>
};

(: transform a document to HTML :)
declare function local:document-to-html(
  $document-path as xs:string,
  $stylesheet-location as xs:string,
  $formatting as document-node(),
  $translations as document-node(),
  $language as xs:string
) as document-node() {
  let $root := for $element in fn:doc($document-path)/*
    return local:element-to-html($element, $formatting, $translations, $language)
  
  return document {
    <html>
      <head>
        <link rel="stylesheet" href="{ $stylesheet-location }" />
        <title>{ $document-path }</title>
      </head>
      <body>
        { local:generate-table-of-contents($root) }
        { $root }
      </body>
    </html>
  }
};

(: resource locations :)
let $stylesheet-location := "ead.css"
let $formatting-path := "/home/georgi/git/data-transformations/XQuery/formatting.tsv"
let $translations-path := "/home/georgi/git/data-transformations/XQuery/labels.tsv"

(: parameters :)
let $language := "en"
let $document-path := "/home/georgi/schem/data/docs/personalpapers_injected.xml"
let $html-path := "/home/georgi/schem/test.html"

(: transform the document to HTML :)
let $formatting := csv:parse(file:read-text($formatting-path),
  map { "separator": "tab", "header": "yes" })
let $translations := csv:parse(file:read-text($translations-path),
  map { "separator": "tab", "header": "yes" })
let $html := local:document-to-html($document-path, $stylesheet-location, $formatting, $translations, $language)

(: write the HTML to file :)
return file:write($html-path, $html,
  map { "method": "html", "media-type": "text/html", "include-content-type": "yes" })
