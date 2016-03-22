xquery version "3.0";

import module namespace shared = "shared" at "shared.xq";

(: input and output files passed by BaseX :)
declare variable $input as xs:string external;
declare variable $output as xs:string external;

declare function local:transform($solr as document-node()) as element() {
    <ead xmlns="urn:isbn:1-931666-22-9" xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <eadheader countryencoding="iso3166-1" dateencoding="iso8601" langencoding="iso639-2b" repositoryencoding="iso15511" scriptencoding="iso15924">
            <eadid countrycode="US">USHMM</eadid>
            <filedesc>
                <titlestmt>
                    <titleproper>USHMM Files</titleproper>
                </titlestmt>
            </filedesc>
            <profiledesc>
                <creation>Created by EHRI on <date>{current-date()}</date>.</creation>
                <langusage>
                    <language langcode="eng">English</language>
                </langusage>
            </profiledesc>
        </eadheader>
        <archdesc level="collection">
            <did>
                <abstract>Files transformed from Solr using XQuery.</abstract>
                <unitid>ushmm_041213</unitid>
                <unittitle>USHMM Files</unittitle>
            </did>
            <dsc>
                {
                    for $file in $solr/*:add/*:doc
                    return <c01 level="file">
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
                }
            </dsc>
        </archdesc>
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }

let $solr := fn:doc($input)
let $ead := local:transform($solr)
return file:write($output, $ead, $ser-params)
