xquery version "3.0";

(: get the value of a field from the input using the mapping :)
declare function local:get-value($input as node(), $mapping as node(), $field as xs:string) as element()* {
  let $path := $mapping/csv/record[field/text() = $field]/path/text()
  return if ($path) then xquery:eval($path, map { "":$input }) else ()
};

(: generate component at the given level :)
declare function local:gen-c($input as node(), $mapping as node(), $level as xs:integer) as element() {
  let $tag := if ($level < 10) then fn:concat("c0", fn:string($level)) else fn:concat("c", fn:string($level))
  return element { $tag } {
    attribute level { "file" },
    <did>
    {
      for $value in fn:data(local:get-value($input, $mapping, "unitid"))
      return <unitid>{ $value }</unitid>,
      for $value in fn:data(local:get-value($input, $mapping, "unittitle"))
      return <unittitle>{ $value }</unittitle>,
      for $value in fn:data(local:get-value($input, $mapping, "physdesc"))
      return <physdesc>{ $value }</physdesc>,
      <langmaterial>
      {
        for $value in fn:data(local:get-value($input, $mapping, "language"))
        return <language>{ $value }</language>
      }
      </langmaterial>
    }
    </did>,
    for $value in fn:data(local:get-value($input, $mapping, "accessrestrict"))
    return <accessrestrict>{ $value }</accessrestrict>,
    for $value in fn:data(local:get-value($input, $mapping, "userestrict"))
    return <userestrict>{ $value }</userestrict>,
    <controlaccess>
    {
      for $value in fn:data(local:get-value($input, $mapping, "corpname"))
      return <corpname>{ $value }</corpname>,
      for $value in fn:data(local:get-value($input, $mapping, "genreform"))
      return <genreform>{ $value }</genreform>,
      for $value in fn:data(local:get-value($input, $mapping, "geogname"))
      return <geogname>{ $value }</geogname>,
      for $value in fn:data(local:get-value($input, $mapping, "persname"))
      return <persname>{ $value }</persname>,
      for $value in fn:data(local:get-value($input, $mapping, "subject"))
      return <subject>{ $value }</subject>
    }
    </controlaccess>,
    for $value in fn:data(local:get-value($input, $mapping, "child-component"))
    return local:gen-c($value, $mapping, $level + 1)
  }
};

(: generate archival description :)
declare function local:gen-archdesc($input as node(), $mapping as node()) as element() {
  <archdesc>
    <dsc>
    {
      for $c in local:get-value($input, $mapping, "top-level-component")
      return local:gen-c($c, $mapping, 1)
    }
    </dsc>
  </archdesc>
};

(: generate front matter :)
declare function local:gen-frontmatter() as element() {
  <frontmatter/>
};

(: generate EAD header :)
declare function local:gen-eadheader() as element() {
  <eadheader/>
};

(: generate EAD :)
declare function local:gen-ead($input as node(), $mapping as node()) as element() {
  <ead
    xmlns="urn:isbn:1-931666-22-9"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd">
  {
    local:gen-eadheader(),
    local:gen-frontmatter(),
    local:gen-archdesc($input, $mapping)
  }
  </ead>
};

(: read input :)
let $input-path := "/home/georgi/Desktop/2016.08.12.document.json"
let $input-encoding := "UTF-8"
let $input := json:parse(file:read-text($input-path, $input-encoding),
  map { "format":"attributes" })

(: read mapping :)
let $mapping-path := "/home/georgi/git/data-transformations/XQuery/mapping-ushmm.tsv"
let $mapping-encoding := "UTF-8"
let $mapping := csv:parse(file:read-text($mapping-path, $mapping-encoding),
  map { "separator":"tab", "header":"yes", "quotes":"no" })

(: write output :)
let $output-path := "/home/georgi/Desktop/ead-ushmm.xml"
let $output-encoding := "UTF-8"
let $output := local:gen-ead($input, $mapping)
return file:write($output-path, $output,
  map { "encoding":$output-encoding, "omit-xml-declaration":"no" })
