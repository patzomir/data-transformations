xquery version "3.0";

declare namespace ead = "urn:isbn:1-931666-22-9";

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
    <div class="field" style="display: flex; padding-left: 10px">
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
          return
            if (exists($item/@svrl_text)) then
            <li style="background-color: salmon">{local:transform-text(data($item))}
              <span class="message" style="margin-left: 10px; padding: 2px; border: solid darkred 2px; background-color: lavenderblush; color: darkred">[{data($item/@svrl_role)}]: {data($item/@svrl_text)}</span>
            </li>
            else <li>{local:transform-text(data($item))}</li>
        }
        
        </ul>
      }
    </div>
  
  else ()
};

(: transform a component of an archival description to html :)
declare function local:transform-component($component as element(), $language as xs:string, $translations as element()) as element() {
  <div class="component" style="margin: 20px; border: solid black 1px; background-color: oldlace">
  {
    (: transform fields :)
    local:transform-field($component/ead:did/ead:unitid, $language, $translations),
    local:transform-field($component/ead:did/ead:unitdate, $language, $translations),
    local:transform-field($component/ead:did/ead:unittitle, $language, $translations),
    local:transform-field($component/ead:did/ead:physdesc/ead:extent, $language, $translations),
    local:transform-field($component/ead:did/ead:physdesc/ead:physfacet, $language, $translations),
    local:transform-field($component/ead:did/ead:physdesc/ead:dimensions, $language, $translations),
    local:transform-field($component/ead:did/ead:origination, $language, $translations),
    local:transform-field($component/ead:bioghist, $language, $translations),
    local:transform-field($component/ead:scopecontent, $language, $translations),
    local:transform-field($component/ead:altformavail, $language, $translations),
    local:transform-field($component/ead:accessrestrict, $language, $translations),
    local:transform-field($component/ead:controlaccess/ead:corpname, $language, $translations),
    local:transform-field($component/ead:controlaccess/ead:geogname, $language, $translations),
    local:transform-field($component/ead:controlaccess/ead:subject, $language, $translations),
    
    (: recursively transform embedded components :)
    for $next-component in $component/*[matches(local-name(), "c[0-1][0-9]")]
    return local:transform-component($next-component, $language, $translations)
  }
  </div>
};

(: transform an ead to html :)
declare function local:transform-ead($ead as element(), $language as xs:string, $translations as element()) as element() {
  <html>
    <head>
    {
      if (string-length(data($ead/ead:eadheader/ead:filedesc/ead:titlestmt/ead:titleproper)) > 0)
      then <title>{data($ead/ead:eadheader/ead:filedesc/ead:titlestmt/ead:titleproper)}</title>
      else <title>EHRI EAD</title>
    }
      <style>
      li .message &#123;
        visibility: hidden
      &#125;
      
      li:hover .message &#123;
        visibility: visible
      &#125;
      </style>
    </head>
    <body>
      <div class="header" style="margin: 20px">
      {
        (: transform ead header :)
        local:transform-field($ead/ead:eadheader/ead:filedesc/ead:titlestmt/ead:author, $language, $translations),
        local:transform-field($ead/ead:eadheader/ead:profiledesc/ead:langusage/ead:language, $language, $translations)
      }
      </div>
      
      <div class="description" style="margin: 20px; border: solid gray 1px; background-color: lightyellow">
      {
        (: transform archival description :)
        local:transform-field($ead/ead:archdesc/ead:did/ead:unitid, $language, $translations),
        local:transform-field($ead/ead:archdesc/ead:did/ead:unittitle, $language, $translations),
        local:transform-field($ead/ead:archdesc/ead:did/ead:abstract, $language, $translations),
        local:transform-field($ead/ead:archdesc/ead:scopecontent, $language, $translations),
        local:transform-field($ead/ead:archdesc/ead:did/ead:processinfo, $language, $translations)
      }
      </div>
    
    {
      (: transform components in archival description :)
      for $component in $ead/ead:archdesc/ead:dsc/ead:c01
      return local:transform-component($component, $language, $translations)
    }
    </body>
  </html>
};

(: should be arguments to the script :)
let $ead-path := "/home/georgi/schem/data/docs/personalpapers_injected.xml"
let $translations-path := "/home/georgi/schem/translations.xml"
let $html-path := "/home/georgi/schem/data/html/test.html"
let $language := "en"

(: transform ead to html :)
let $ead := doc($ead-path)/ead:ead
let $translations := doc($translations-path)/translations
let $html := local:transform-ead($ead, $language, $translations)

(: write the html :)
let $ser-params := map { "method": "html", "encoding": "UTF-8", "media-type": "text/html", "include-content-type": "yes" }
return file:write($html-path, $html, $ser-params)
