xquery version "3.0";

module namespace ead = "ead";

declare function ead:ead($input as node(), $mapping-path as xs:string) as element() {
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
  <eadheader/>
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
    for $value in ead:field-value($input, $mapping, "unitdate")
    return <unitdate>{ fn:data($value) }</unitdate>,
    for $value in ead:field-value($input, $mapping, "unitid")
    return <unitid>{ fn:data($value) }</unitid>,
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
      return <physfacet>{ fn:data($value) }</physfacet>
    }
    </physdesc>
  return if ($element/*) then $element else ()
};

declare function ead:controlaccess($input as node(), $mapping as document-node()) as element()? {
  let $element :=
    <controlaccess>
    {
      for $value in ead:field-value($input, $mapping, "corpname")
      return <corpname>{ fn:data($value) }</corpname>,
      for $value in ead:field-value($input, $mapping, "genreform")
      return <genreform>{ fn:data($value) }</genreform>,
      for $value in ead:field-value($input, $mapping, "geogname")
      return <geogname>{ fn:data($value) }</geogname>,
      for $value in ead:field-value($input, $mapping, "persname")
      return <persname>{ fn:data($value) }</persname>,
      for $value in ead:field-value($input, $mapping, "subject")
      return <subject>{ fn:data($value) }</subject>
    }
    </controlaccess>
  return if ($element/*) then $element else ()
};

(: get a field value from the input using the mapping :)
declare function ead:field-value($input as node(), $mapping as document-node(), $field as xs:string) as element()* {
  let $path := $mapping/csv/record[field/text() = $field]/path/text()
  return if ($path) then xquery:eval($path, map { "":$input }) else ()
};
