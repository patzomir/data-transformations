xquery version "3.0";

import module namespace shared = "shared" at "shared.xq";

(: generate an EAD header element :)
declare function local:gen-header() as element() {
    <eadheader langencoding="iso639-2b" scriptencoding="iso15924" dateencoding="iso8601" countryencoding="iso3166-1" repositoryencoding="iso15511">
        <eadid countrycode="AT">IKG-wien</eadid>
        <filedesc>
            <titlestmt>
                <titleproper>WP2 files of IKG concerning wien</titleproper>
            </titlestmt>
        </filedesc>
        <profiledesc>
            <creation><date>{ fn:current-date() }</date>Created by EHRI using a custom mapping.</creation>
            <langusage>
                <language langcode="ger" scriptcode="Latn">German</language>
            </langusage>
        </profiledesc>
    </eadheader>
};

declare function local:get-values($record as element(), $field as xs:string) as xs:string* {
    let $text := $record/entry[@name = $field]/text()
    return fn:tokenize(fn:replace($text, ";$", ""), "(;|\s+// \*/)\s+")
};

declare function local:get-xtra-info($input-xtra as document-node(), $call-number as xs:string) as element()* {
    $input-xtra/csv/record[./entry[@name = "Call number"]/text() = $call-number]
};

declare function local:transform-file($file as element(), $input-xtra as document-node()) as element() {
    let $call-number := fn:exactly-one(local:get-values($file, "Call number"))
    let $xtra := local:get-xtra-info($input-xtra, $call-number)
    return <c01 level="file">
        <did>
            {
                shared:wrap-each("unitid", map { "label": "callnumber" }, $call-number),
                shared:wrap-each("unitid", map { "label": "previousIdentifier" }, local:get-values($file, "Earlier call numbers")),
                shared:wrap-each("unitdate", $xtra[./entry[@name = "Type"]/text() = "DATE"]/entry[@name = "Text"]/text()),
                shared:wrap-each("unittitle", local:get-values($file, "Title")),
                shared:wrap-all("physdesc", (
                    shared:wrap-each("extent", local:get-values($file, "Extent")),
                    shared:wrap-each("physfacet", map { "type": "binding" }, local:get-values($file, "Binding")),
                    shared:wrap-each("physfacet", map { "type": "material" }, local:get-values($file, "Format"))
                )),
                shared:wrap-each("origination", local:get-values($file, "Provenience"))
            }
        </did>
            {
                shared:wrap-all("altformavail", (
                    shared:wrap-each("p", local:get-values($file, "Microfilm copies"))
                )),
                shared:wrap-all("phystech", (
                    shared:wrap-each("p", local:get-values($file, "Physical condition"))
                )),
                shared:wrap-all("scopecontent", (
                    shared:wrap-each("p", local:get-values($file, "Content description"))
                )),
                shared:wrap-all("controlaccess", (
                    for $org in $xtra[./entry[@name = "Type"]/text() = "O"]
                    return <corpname source="wien-organisations" authfilenumber="{ $org/entry[@name = "JMP IDNO"]/text() }">{ $org/entry[@name = "Text"]/text() }</corpname>,

                    for $loc in $xtra[./entry[@name = "Type"]/text() = "L"]
                    return <geogname source="wien-places" authfilenumber="{ $loc/entry[@name = "JMP IDNO"]/text() }">{ $loc/entry[@name = "Text"]/text() }</geogname>,

                    for $per in $xtra[./entry[@name = "Type"]/text() = "P"]
                    return <persname source="wien-persons" authfilenumber="{ $per/entry[@name = "JMP IDNO"]/text() }">{ $per/entry[@name = "Text"]/text() }</persname>,

                    for $sub in $xtra[./entry[@name = "Type"]/text() = "K"]
                    return <subject source="wien-terms" authfilenumber="{ $sub/entry[@name = "JMP IDNO"]/text() }">{ $sub/entry[@name = "Text"]/text() }</subject>
                ))
            }
        </c01>
};

declare function local:transform-collection($collection as element(), $input-xtra as document-node()) as element() {
    <archdesc level="collection">
        <did>
            <unitid type="callnumber">wien</unitid>
            <unittitle>wien</unittitle>
            <abstract>Files and Documents compiled by IKG and edited by EHRI to be part of the Wien Research Guide.</abstract>
        </did>
        <dsc>
            {
                for $file at $pos-file in $collection/record
                return local:transform-file($file, $input-xtra)
            }
        </dsc>
    </archdesc>
};

declare function local:transform($input-main as document-node(), $input-xtra as document-node()) as element() {
    <ead
        xmlns="urn:isbn:1-931666-22-9"
        xmlns:xlink="http://www.w3.org/1999/xlink"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd"
        audience="external">
        { local:gen-header() }
        { local:transform-collection($input-main/*:csv, $input-xtra) }
    </ead>
};

(: parameters :)
let $csv-params := map { "separator": "tab", "header": "yes", "format": "attributes" }
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $input-main := "/home/georgi/IdeaProjects/TestBaseX/data/wien-main.tsv"
let $input-xtra := "/home/georgi/IdeaProjects/TestBaseX/data/wien-xtra.tsv"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/wien-output/"

(: transform input and write output :)
let $input-main := csv:parse(file:read-text($input-main), $csv-params)
let $input-xtra := csv:parse(file:read-text($input-xtra), $csv-params)
for $ead at $pos-ead in local:transform($input-main, $input-xtra)
let $file-path := fn:concat($output-dir, "ead_", shared:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
