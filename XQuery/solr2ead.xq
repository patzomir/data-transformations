xquery version "3.0";

import module namespace shared = "shared" at "shared.xq";

(: generate an EAD header element :)
declare function local:gen-header() as element() {
    <eadheader countryencoding="iso3166-1" dateencoding="iso8601" langencoding="iso639-2b" repositoryencoding="iso15511" scriptencoding="iso15924">
        <eadid countrycode="US">USHMM</eadid>
        <filedesc>
            <titlestmt>
                <titleproper>USHMM Files</titleproper>
            </titlestmt>
        </filedesc>
        <profiledesc>
            <creation>Created by EHRI on <date>{ fn:current-date() }</date>.</creation>
            <langusage>
                <language langcode="eng">English</language>
            </langusage>
        </profiledesc>
    </eadheader>
};

(: transform a file-level component :)
declare function local:transform-file($file as element()) as element() {
    <c01 level="file">
        <did>
            {shared:wrap-each("abstract", $file/*:field[@name = "brief_desc"]/text())}
            <physdesc>
                {shared:wrap-each("dimensions", $file/*:field[@name = "dimensions"]/text())}
            </physdesc>
            {
                shared:wrap-each("unitid", $file/*:field[@name = "id"]/text()),
                shared:wrap-each("unitdate", $file/*:field[@name = "display_date"]/text()),
                shared:wrap-each("unittitle", $file/*:field[@name = "title"]/text())
            }
        </did>
        {shared:wrap-each("bioghist", $file/*:field[@name = "creator_bio"]/text())}
    </c01>
};

(: transform a collection-level component :)
declare function local:transform-collection($collection as element()) as element() {
    <archdesc level="collection">
        <did>
            <abstract>Files transformed from Solr using XQuery.</abstract>
            <unitid>ushmm_041213</unitid>
            <unittitle>USHMM Files</unittitle>
        </did>
        <dsc>
            {
                for $file in $collection/*:doc
                return local:transform-file($file)
            }
        </dsc>
    </archdesc>
};

(: transform a SOLR input to EAD :)
declare function local:transform($input-main as document-node()) as element()* {
    <ead
    xmlns="urn:isbn:1-931666-22-9"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd"
    audience="external">
        { local:gen-header() }
        { local:transform-collection($input-main/*:add) }
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $input-main := "/home/georgi/IdeaProjects/TestBaseX/data/solr.xml"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/solr-output/"

(: transform input and write output :)
for $ead at $pos-ead in local:transform(fn:doc($input-main))
let $file-path := fn:concat($output-dir, "ead_", shared:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
