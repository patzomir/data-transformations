xquery version "3.0";

declare default element namespace "urn:isbn:1-931666-22-9";

(: pad a number with leading zeroes :)
declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
  if (string-length($number) = $length) then $number
  else local:pad-with-zeroes(concat("0", $number), $length)
};

(: transform a text value to html (just remove illegal characters) :)
declare function local:transform-text($text as xs:string) as xs:string {
  replace($text, "[\p{IsC}]", "")
};

declare function local:translate-field($field-name as xs:string, $language as xs:string, $translations as element()) as xs:string {
  let $translation := $translations/*:field[@name=$field-name]/*:label[@language=$language]/text()
  return
    if (exists($translation)) then $translation
    else $field-name
};

(: transform a field of a component to html :)
declare function local:transform-field($field as element()*, $language as xs:string, $translations as element()) as element()? {
  
  (: check if the field actually has any content :)
  if (string-length(string-join(data($field), "")) > 0) then
    <div style="display: flex; padding-left: 10px">
      <h5 style="position: relative; top: -5px; width: 20%">{local:translate-field(local-name($field[1]), $language, $translations)}</h5>
      
      {
        (: display element values in a container (e.g. <p>) :)
        if (exists($field/*)) then
        <div style="width: 60%">
        {
          for $item in $field
          return
            for $child in $item/*
            return element {local-name($child)} {local:transform-text(data($child))}
        }
        </div>
        
        (: display multiple values as list :)
        else
        <ul style="width: 60%">
        {
          for $item in $field
          return <li>{local:transform-text(data($item))}</li>
        }
        
        </ul>
      }
    </div>
  
  else ()
};

(: transform a component of an archival description to html :)
declare function local:transform-component($component as element(), $level as xs:integer, $language as xs:string, $translations as element()) as element() {
  <div style="margin: 20px; border: solid black 1px; background-color: oldlace">
  {
    (: transform fields :)
    local:transform-field($component/did/unitid, $language, $translations),
    local:transform-field($component/did/unittitle, $language, $translations),
    local:transform-field($component/did/physdesc/extent, $language, $translations),
    local:transform-field($component/did/physdesc/physfacet, $language, $translations),
    local:transform-field($component/did/physdesc/dimensions, $language, $translations),
    local:transform-field($component/did/origination, $language, $translations),
    local:transform-field($component/bioghist, $language, $translations),
    local:transform-field($component/scopecontent, $language, $translations),
    local:transform-field($component/altformavail, $language, $translations),
    local:transform-field($component/accessrestrict, $language, $translations),
    local:transform-field($component/controlaccess/corpname, $language, $translations),
    local:transform-field($component/controlaccess/geogname, $language, $translations),
    local:transform-field($component/controlaccess/subject, $language, $translations),
    
    (: recursively transform next-level components :)
    let $next-level := $level + 1
    let $next-component-tag := concat("c", local:pad-with-zeroes(string($next-level), 2))
    return
      for $next-component in $component/*[local-name() = $next-component-tag]
      return local:transform-component($next-component, $next-level, $language, $translations)
  }
  </div>
};

(: transform an ead to html :)
declare function local:transform-ead($ead as element(), $language as xs:string, $translations as element()) as element() {
  <html>
    <head>
    {
      if (string-length(data($ead/eadheader/filedesc/titlestmt/titleproper)) > 0)
      then <title>{data($ead/eadheader/filedesc/titlestmt/titleproper)}</title>
      else <title>EHRI EAD</title>
    }
    </head>
    <body>
      <div style="margin: 20px">
      {
        (: transform ead header :)
        local:transform-field($ead/eadheader/filedesc/titlestmt/author, $language, $translations),
        local:transform-field($ead/eadheader/profiledesc/langusage/language, $language, $translations)
      }
      </div>
      
      <div style="margin: 20px; border: solid gray 1px; background-color: lightyellow">
      {
        (: transform archival description :)
        local:transform-field($ead/archdesc/did/unitid, $language, $translations),
        local:transform-field($ead/archdesc/did/unittitle, $language, $translations),
        local:transform-field($ead/archdesc/did/abstract, $language, $translations),
        local:transform-field($ead/archdesc/scopecontent, $language, $translations),
        local:transform-field($ead/archdesc/did/processinfo, $language, $translations)
      }
      </div>
    
    {
      (: transform components in archival description :)
      for $component in $ead/archdesc/dsc/c01
      return local:transform-component($component, 1, $language, $translations)
    }
    </body>
  </html>
};

(: should be arguments to the script :)
let $ead-path := "/home/georgi/schem/data/docs/personalpapers.xml"
let $translations-path := "/home/georgi/schem/translations.xml"
let $html-path := "/home/georgi/schem/data/html/test.html"
let $language := "en"

(: parse and transform the ead :)
let $ead := doc($ead-path)
let $translations := doc($translations-path)
let $html := local:transform-ead($ead/ead, $language, $translations/*:translations)

(: write the html :)
let $ser-params := map { "method": "html", "encoding": "UTF-8", "media-type": "text/html", "include-content-type": "yes" }
return file:write($html-path, $html, $ser-params)
