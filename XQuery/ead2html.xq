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

(: transform a field of a component to html :)
declare function local:transform-field($field as element()*) as element()? {
  
  (: check if the field actually has any content :)
  if (string-length(string-join(data($field), "")) > 0) then
    <div style="display: flex; padding-left: 10px">
      <h5 style="position: relative; top: -5px; width: 20%">{local-name($field[1])}</h5>
      
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
declare function local:transform-component($component as element(), $level as xs:integer) as element() {
  <div style="margin: 20px; border: solid black 1px; background-color: whitesmoke">
  {
    (: transform fields :)
    local:transform-field($component/did/unitid),
    local:transform-field($component/did/unittitle),
    local:transform-field($component/did/physdesc/extent),
    local:transform-field($component/did/physdesc/physfacet),
    local:transform-field($component/did/physdesc/dimensions),
    local:transform-field($component/did/origination),
    local:transform-field($component/bioghist),
    local:transform-field($component/scopecontent),
    local:transform-field($component/altformavail),
    local:transform-field($component/accessrestrict),
    local:transform-field($component/controlaccess/corpname),
    local:transform-field($component/controlaccess/geogname),
    local:transform-field($component/controlaccess/subject),
    
    (: recursively transform next-level components :)
    let $next-level := $level + 1
    let $next-component-tag := concat("c", local:pad-with-zeroes(string($next-level), 2))
    return
      for $next-component in $component/*[local-name() = $next-component-tag]
      return local:transform-component($next-component, $next-level)
  }
  </div>
};

(: transform an ead to html :)
declare function local:transform-ead($ead as element()) as element() {
  <html>
    <head>
    </head>
    <body>
      <div style="margin: 20px; border: solid black 1px; background-color: floralwhite">
      {
        (: transform archival description :)
        local:transform-field($ead/archdesc/did/unitid),
        local:transform-field($ead/archdesc/did/unittitle),
        local:transform-field($ead/archdesc/did/abstract),
        local:transform-field($ead/archdesc/scopecontent),
        local:transform-field($ead/archdesc/did/processinfo)
      }
      </div>
    
    {
      (: transform components in archival description :)
      for $component in $ead/archdesc/dsc/c01
      return local:transform-component($component, 1)
    }
    </body>
  </html>
};

(: should be arguments to the script :)
let $ead-path := "/home/georgi/schem/data/docs/personalpapers.xml"
let $html-path := "/home/georgi/schem/data/html/test.html"

(: parse and transform the ead :)
let $ead := doc($ead-path)
let $html := local:transform-ead($ead/ead)

(: write the html :)
let $ser-params := map { "method": "html", "encoding": "UTF-8", "media-type": "text/html", "include-content-type": "yes" }
return file:write($html-path, $html, $ser-params)
