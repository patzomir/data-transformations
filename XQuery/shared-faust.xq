xquery version "3.0";

module namespace shared-faust = "shared-faust";

(: default namespace for EAD elements :)
declare default element namespace "urn:isbn:1-931666-22-9";

(: generate an EAD header element :)
declare function shared-faust:gen-header() as element() {
    <eadheader>
        <eadid countrycode="DE">NL</eadid>
        <filedesc>
            <titlestmt>
                <author>IfZ</author>
            </titlestmt>
        </filedesc>
        <profiledesc>
            <creation>EHRI created this EAD based on the Faust-output and selection from the IfZ München <date>{ fn:current-date() }</date></creation>
            <langusage>
                <language scriptcode="Latn" langcode="ger">German</language>
            </langusage>
        </profiledesc>
    </eadheader>
};

(: get additional information for a given Faust object :)
declare function shared-faust:get-xtra-info($input-xtra as document-node(), $ref as xs:string) as element()? {
    let $ref := fn:concat("Objekt ", $ref, " / ED")
    return fn:zero-or-one($input-xtra/ED/FAUST-Objekt[Weitere_Bestandsangaben/text() = $ref])
};
