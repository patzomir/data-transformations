xquery version "3.0";

declare namespace ead = "urn:isbn:1-931666-22-9";

declare function local:legalize-text($text as xs:string) as xs:string {
  replace($text, "[\p{IsC}]", "")
};

declare function local:field2class($field as xs:string) as xs:string {
  if (matches($field, "c[0-1][0-9]"))
  then "component"
  else $field
};

declare function local:field2label($field as xs:string, $language as xs:string, $labels as document-node()) as xs:string {
  let $label := $labels/csv/record[field/text() = $field]/*[local-name() = $language]/text()
  return
    if ($label)
    then $label
    else $field
};

declare function local:field-value2label($field as xs:string, $value as xs:string, $language as xs:string, $labels as document-node()) as xs:string {
  let $label := $labels/csv/record[field/text() = $field and value/text() = $value]/*[local-name() = $language]/text()
  return
    if ($label)
    then $label
    else $value
};

declare function local:transform-element($element as element(), $special-tags as xs:string*, $language as xs:string, $labels as document-node(), $labels-attr as document-node(), $labels-attr-vals as document-node()) as element() {
  let $tag := local-name($element)
  let $role := $element/@svrl_role
  let $text := $element/@svrl_text
  return
    <div class="{local:field2class($tag), if ($role and $text) then "fail" else (if ($element/@*) then "info" else())}">
      <div class="meta">
        <span class="label">{local:field2label($tag, $language, $labels)}</span>
        {
          if ($element/@*)
          then
          <table class="tooltip">
          {
            if ($role and $text)
            then
              <tr class="message">
                <td class="role">{data($role)}</td>
                <td class="text">{data($text)}</td>
              </tr>
            else (),
            
            for $attribute in $element/@*
              let $attribute-name := local-name($attribute)
              return
                if (not($attribute-name = "svrl_role" or $attribute-name = "svrl_text"))
                then
                  <tr class="attribute">
                    <td class="label">{local:field2label($attribute-name, $language, $labels-attr)}</td>
                    <td class="value">{local:field-value2label($attribute-name, data($attribute), $language, $labels-attr-vals)}</td>
                  </tr>
                else ()
          }
          </table>
          else ()
        }
      </div>
      <div class="content">
      {
        for $child in $element/ead:*
        let $child-tag := local-name($child)
        return
          if (index-of($special-tags, $child-tag))
          then <span class="text">{local:legalize-text(data($child))}</span>
          else local:transform-element($child, $special-tags, $language, $labels, $labels-attr, $labels-attr-vals),
        
        for $text in $element/text()
        return <span class="text">{local:legalize-text($text)}</span>
      }
      </div>
    </div>
};

declare function local:transform-document($ead as document-node(), $special-tags as xs:string*, $language as xs:string, $labels as document-node(), $labels-attr as document-node(), $labels-attr-vals as document-node()) as element() {
  <html>
    <head>
      <link rel="stylesheet" href="ead.css"/>
      <title>{data($ead/ead:ead/ead:eadheader/ead:eadid)}</title>
    </head>
    <body>
    {
      local:transform-element($ead/ead:ead/ead:eadheader, $special-tags, $language, $labels, $labels-attr, $labels-attr-vals),
      for $component in $ead/ead:ead/ead:archdesc/ead:dsc/ead:c01
      return local:transform-element($component, $special-tags, $language, $labels, $labels-attr, $labels-attr-vals)
    }
    </body>
  </html>
};

let $ead-path := "/home/georgi/schem/data/docs/personalpapers_injected.xml"
let $labels-path := "/home/georgi/schem/labels.tsv"
let $labels-attr-path := "/home/georgi/schem/labels-attrib.tsv"
let $labels-attr-vals-path := "/home/georgi/schem/labels-attrib-values.tsv"
let $html-path := "/home/georgi/schem/test.html"

let $language := "en"
let $special-tags := ("p")

let $ead := doc($ead-path)
let $labels := csv:parse(file:read-text($labels-path), map {"separator": "tab", "header": "yes"})
let $labels-attr := csv:parse(file:read-text($labels-attr-path), map {"separator": "tab", "header": "yes"})
let $labels-attr-vals := csv:parse(file:read-text($labels-attr-vals-path), map {"separator": "tab", "header": "yes"})

let $html := local:transform-document($ead, $special-tags, $language, $labels, $labels-attr, $labels-attr-vals)
return file:write($html-path, $html, map {"method": "html", "media-type": "text/html", "encoding": "UTF-8", "include-content-type": "yes"})
