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
  let $label := $labels/csv/record[field/text()=$field]/*[local-name() = $language]/text()
  return
    if ($label)
    then $label
    else $field
};

declare function local:transform-element($element as element(), $special-tags as xs:string*, $language as xs:string, $labels as document-node()) as element() {
  let $tag := local-name($element)
  return
    <div class="{local:field2class($tag)}">
      <span class="label">{local:field2label($tag, $language, $labels)}</span>
      {
        let $role := $element/@svrl_role
        let $text := $element/@svrl_text
        return
          if ($role and $text)
          then
            <div class="message">
              <span class="role">{data($role)}</span>
              <span class="text">{data($text)}</span>
            </div>
          else (),
        
        for $attribute in $element/@*
          let $attribute-name := local-name($attribute)
          return
            if (not($attribute-name = "svrl_role" or $attribute-name = "svrl_text"))
            then
              <div class="attribute">
                <span class="label">{local:field2label($attribute-name, $language, $labels)}</span>
                <span class="value">{data($attribute)}</span>
              </div>
            else ()
      }
      <div class="content">
      {
        for $child in $element/ead:*
        let $child-tag := local-name($child)
        return
          if (index-of($special-tags, $child-tag))
          then element {$child-tag} {local:legalize-text(data($child))}
          else local:transform-element($child, $special-tags, $language, $labels),
        
        for $text in $element/text()
        return <span class="text">{local:legalize-text($text)}</span>
      }
      </div>
    </div>
};

declare function local:transform-document($ead as document-node(), $special-tags as xs:string*, $language as xs:string, $labels as document-node()) as element() {
  <html>
    <head>
      <link rel="stylesheet" href="ead.css"/>
    </head>
    <body>
    {
      for $component in $ead/ead:ead/ead:archdesc/ead:dsc/ead:c01
      return local:transform-element($component, $special-tags, $language, $labels)
    }
    </body>
  </html>
};

let $ead-path := "/home/georgi/schem/data/docs/personalpapers_injected.xml"
let $labels-path := "/home/georgi/schem/labels.tsv"
let $html-path := "/home/georgi/schem/test.html"

let $language := "en"
let $special-tags := ("p")

let $ead := doc($ead-path)
let $labels := csv:parse(file:read-text($labels-path), map {"separator": "tab", "header": "yes"})

let $html := local:transform-document($ead, $special-tags, $language, $labels)
return file:write($html-path, $html, map {"method": "html", "media-type": "text/html", "encoding": "UTF-8", "include-content-type": "yes"})
