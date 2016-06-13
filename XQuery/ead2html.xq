xquery version "3.0";

declare default element namespace "urn:isbn:1-931666-22-9";

declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
  if (string-length($number) = $length) then $number
  else local:pad-with-zeroes(concat("0", $number), $length)
};

declare function local:transform-text($text as xs:string) as xs:string {
  replace($text, "[\p{IsC}]", "")
};

declare function local:transform-value($value as node()) as item() {
  if ($value instance of text()) then local:transform-text($value)
  else element {local-name($value)} {local:transform-text(data($value))}
};

declare function local:transform-field($field as element()*) as element()? {
  if (string-length(string-join(data($field), "")) > 0) then
    <tr>
      <td style="vertical-align: top">{local-name($field[1])}</td>
      <td style="vertical-align: top">
        <ul>
        {
          for $item in $field
          return
            if (string-length(data($item)) > 0) then
              <li>
              {
                for $value in $item/text() | $item/*
                return local:transform-value($value)
              }
              </li>
            
            else ()
        }
        </ul>
      </td>
    </tr>
  
  else ()
};

declare function local:transform-component($component as element(), $level as xs:integer) as element() {
  <table style="margin-left: {25 * $level}px">
  {
    local:transform-field($component/did/unitid),
    local:transform-field($component/did/unittitle),
    local:transform-field($component/bioghist),
    local:transform-field($component/scopecontent),
    local:transform-field($component/altformavail),
    local:transform-field($component/accessrestrict),
    local:transform-field($component/controlaccess/subject),
    
    let $next-level := $level + 1
    let $next-component-tag := concat("c", local:pad-with-zeroes(string($next-level), 2))
    return
      for $next-component in $component/*[local-name() = $next-component-tag]
      return local:transform-component($next-component, $next-level)
  }
  </table>
};

declare function local:transform-ead($ead as element()) as element() {
  <html>
    <head>
    </head>
    <body>
    {
      for $component in $ead/archdesc/dsc/c01
      return local:transform-component($component, 1)
    }
    </body>
  </html>
};

let $ead-path := "/home/georgi/schem/data/docs/personalpapers.xml"
let $html-path := "/home/georgi/schem/data/html/test.html"

let $ead := doc($ead-path)
let $html := local:transform-ead($ead/ead)

let $ser-params := map { "method": "html", "encoding": "UTF-8", "media-type": "text/html", "include-content-type": "yes" }
return file:write($html-path, $html, $ser-params)
