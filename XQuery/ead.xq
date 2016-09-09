xquery version "3.0";

module namespace ead = "ead";

declare function ead:ead($input as document-node(), $mapping-path as xs:string) as element() {
  let $mapping := csv:parse(file:read-text($mapping-path), map { "separator":"tab", "header":"yes", "quotes":"no" })
  return
    <ead xmlns="urn:isbn:1-931666-22-9" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd">
    {
      ead:eadheader($input, $mapping),
      ead:frontmatter($input, $mapping),
      ead:archdesc($input, $mapping)
    }
    </ead>
};

declare function ead:eadheader($input as node(), $mapping as document-node()) as element() {
  <eadheader>
    <eadid countrycode="{ fn:data(ead:field-value($input, $mapping, "eadid-countrycode")) }">
    {
      fn:data(ead:field-value($input, $mapping, "eadid"))
    }
    </eadid>
    <filedesc>
      <titlestmt>
        <titleproper>{ fn:data(ead:field-value($input, $mapping, "titleproper")) }</titleproper>
      </titlestmt>
    </filedesc>
    <profiledesc>
      <creation>Created by ead.xq on <date>{ fn:current-date() }</date>.</creation>
    </profiledesc>
  </eadheader>
};

declare function ead:frontmatter($input as node(), $mapping as document-node()) as element() {
  <frontmatter/>
};

declare function ead:archdesc($input as node(), $mapping as document-node()) as element() {
  <archdesc>
    <did/>
    <dsc>
    {
      for $value in ead:field-value($input, $mapping, "top-level-component")
      return ead:c($value, $mapping, 1)
    }
    </dsc>
  </archdesc>
};

declare function ead:c($input as node(), $mapping as document-node(), $level as xs:integer) as element() {
  let $tag := if ($level < 10) then fn:concat("c0", fn:string($level)) else fn:concat("c", fn:string($level))
  return element { $tag }
    {
      attribute level { "file" },
      ead:did($input, $mapping),
      ead:altformavail($input, $mapping),
      ead:phystech($input, $mapping),
      ead:scopecontent($input, $mapping),
      ead:controlaccess($input, $mapping),
      for $value in ead:field-value($input, $mapping, "child-component")
      return ead:c($value, $mapping, $level + 1)
    }
};

declare function ead:did($input as node(), $mapping as document-node()) as element() {
  <did>
  {
    for $value in ead:field-value($input, $mapping, "materialspec")
    return <materialspec>{ fn:data($value) }</materialspec>,
    for $value in ead:field-value($input, $mapping, "origination")
    return <origination>{ fn:data($value) }</origination>,
    for $value in ead:field-value($input, $mapping, "unitdate")
    return <unitdate>{ fn:data($value) }</unitdate>,
    for $value in ead:field-value($input, $mapping, "unitid")
    return <unitid>{ fn:data($value) }</unitid>,
    for $value in ead:field-value($input, $mapping, "unitid-main")
    return <unitid label="main">{ fn:data($value) }</unitid>,
    for $value in ead:field-value($input, $mapping, "unittitle")
    return <unittitle>{ fn:data($value) }</unittitle>,
    ead:physdesc($input, $mapping)
  }
  </did>
};

declare function ead:physdesc($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <physdesc>
    {
      for $value in ead:field-value($input, $mapping, "dimensions")
      return <dimensions>{ fn:data($value) }</dimensions>,
      for $value in ead:field-value($input, $mapping, "extent")
      return <extent>{ fn:data($value) }</extent>,
      for $value in ead:field-value($input, $mapping, "physfacet")
      return <physfacet>{ fn:data($value) }</physfacet>,
      for $value in ead:field-value($input, $mapping, "physfacet-binding")
      return <physfacet type="binding">{ fn:data($value) }</physfacet>,
      for $value in ead:field-value($input, $mapping, "physfacet-material")
      return <physfacet type="material">{ fn:data($value) }</physfacet>
    }
    </physdesc>
  return if ($element/*) then $element else ()
};

declare function ead:altformavail($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <altformavail>
    {
      for $value in ead:field-value($input, $mapping, "altformavail")
      return <p>{ fn:data($value) }</p>
    }
    </altformavail>
  return if ($element/*) then $element else ()
};

declare function ead:phystech($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <phystech>
    {
      for $value in ead:field-value($input, $mapping, "phystech")
      return <p>{ fn:data($value) }</p>
    }
    </phystech>
  return if ($element/*) then $element else ()
};

declare function ead:scopecontent($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <scopecontent>
    {
      for $value in ead:field-value($input, $mapping, "scopecontent")
      return <p>{ fn:data($value) }</p>
    }
    </scopecontent>
  return if ($element/*) then $element else ()
};

declare function ead:controlaccess($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <controlaccess>
    {
      ead:controlaccess($input, $mapping, "corpname"),
      ead:controlaccess($input, $mapping, "genreform"),
      ead:controlaccess($input, $mapping, "geogname"),
      ead:controlaccess($input, $mapping, "persname"),
      ead:controlaccess($input, $mapping, "subject")
    }
    </controlaccess>
  return if ($element/*) then $element else ()
};

declare function ead:controlaccess($input as node(), $mapping as document-node(), $type as xs:string) as element()* {
  for $value in ead:field-value($input, $mapping, $type)
  let $authfilenumber := ead:field-value($value, $mapping, "authfilenumber")
  return
    if ($authfilenumber)
    then element { $type } { attribute authfilenumber { $authfilenumber }, fn:data($value) }
    else element { $type } { fn:data($value) }
};

(: get a field value from the input using the mapping :)
declare function ead:field-value($input as node(), $mapping as document-node(), $field as xs:string) as item()* {
  let $query := $mapping/csv/record[field/text() = $field]/query/text()
  return if ($query) then xquery:eval($query, map { "":$input }) else ()
};
