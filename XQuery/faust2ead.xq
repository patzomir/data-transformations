xquery version "3.0";

(: pad a number with leading zeroes :)
(: - $number: the number as a string :)
(: - $length: the total length of the padded number as integer :)
declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
    if (fn:string-length($number) = $length)
    then $number
    else local:pad-with-zeroes(fn:concat("0", $number), $length)
};

(: generate a sequence of elements :)
(: - $values: the sequence of corresponding values :)
(: - tag: the tag of the elements to generate :)
declare function local:gen-elements($values as xs:string*, $tag as xs:string) as element()* {
    for $value in $values
    return element { $tag } { $value }
};

(: generate an EAD header element :)
declare function local:gen-header() as element() {
    <eadheader>
        <eadid countrycode="DE">NL</eadid>
        <filedesc>
            <titlestmt>
                <titleproper/>
                <author>IfZ</author>
            </titlestmt>
        </filedesc>
        <profiledesc>
            <creation>EHRI created this EAD based on the Faust-output and selection from the IfZ München <date>{ fn:current-date() }</date></creation>
            <langusage>
                <language scriptcode="Latn" langcode="ger">German</language>
            </langusage>
        </profiledesc>
        <revisiondesc/>
    </eadheader>
};

(: get additional information for a given Faust object :)
declare function local:get-xtra-info($faust-xtra as document-node(), $ref as xs:string) as element()? {
    let $ref := fn:concat("Objekt ", $ref, " / ED")
    return fn:zero-or-one($faust-xtra/ED/FAUST-Objekt[Weitere_Bestandsangaben/text() = $ref])
};

(: transform a single Faust object to a file-level component :)
declare function local:transform-file($faust-main as document-node(), $faust-xtra as document-node(), $struc as xs:string, $sign as xs:string, $tome as xs:string, $ref as xs:string) as element() {
    let $file := fn:exactly-one($faust-main/ED/FAUST-Objekt[Signatur/text() = $sign and Bandnummer/text() = $tome and Ref/text() = $ref])
    let $xtra-info := local:get-xtra-info($faust-xtra, $ref)
    return <c03 level="file">
        <did>
            <unitid label="ehri_main_identifier" identifier="{ $ref }">{ $sign } / { $tome }, { $ref }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
            { local:gen-elements(fn:string-join((fn:zero-or-one($file/Laufzeit-Beginn/text()), fn:zero-or-one($file/Laufzeit-Ende/text())), "-"), "unitdate") }
            { local:gen-elements($file/Titel/text(), "unittitle") }
            { local:gen-elements($file/Untertitel/text(), "unittitle") }
            <abstract>
                { local:gen-elements($file/Bestandskurzbeschreibung/text(), "p") }
            </abstract>
            <origination>
                { local:gen-elements($file/Autor/text(), "persname") }
            </origination>
            <physdesc>
                { local:gen-elements($file/Umfang/text(), "dimensions") }
            </physdesc>
        </did>
        <bioghist>
            { local:gen-elements($xtra-info/Vita/text(), "p") }
        </bioghist>
        <scopecontent>
            { local:gen-elements($xtra-info/Zum_Bestand/text(), "p") }
            { local:gen-elements($file/Enthalt/text(), "p") }
            { local:gen-elements($file/Darin_auch/text(), "p") }
        </scopecontent>
        <accessrestrict>
            { local:gen-elements($xtra-info/Bestandsnutzung/text(), "p") }
        </accessrestrict>
        <altformavail>
            { local:gen-elements($file/Digitalisierung/text(), "p") }
            { local:gen-elements($file/Internetadresse/text(), "p") }
            { local:gen-elements($file/Online_Prasentation/text(), "p") }
        </altformavail>
        <controlaccess>
            { local:gen-elements(fn:tokenize($file/Personenregister/text(), ";\s*"), "persname") }
            { local:gen-elements(fn:tokenize($file/Sachregister/text(), ";\s*"), "subject") }
            { local:gen-elements($file/Thesaurus/text(), "subject") }
        </controlaccess>
    </c03>
};

(: transform a group of Faust objects with the same signature and tome number to a subseries-level component :)
declare function local:transform-subseries($faust-main as document-node(), $faust-xtra as document-node(), $struc as xs:string, $sign as xs:string, $tome as xs:string) as element() {
    let $subseries := $faust-main/ED/FAUST-Objekt[Signatur/text() = $sign and Bandnummer/text() = $tome]
    return <c02 level="subseries">
        <did>
            <unitid label="ehri_main_identifier">{ $sign } / { $tome }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        {
            for $ref at $pos-ref in fn:distinct-values($subseries/Ref)
            let $struc-ref := fn:concat($struc, ".", fn:string($pos-ref))
            return local:transform-file($faust-main, $faust-xtra, $struc-ref, $sign, $tome, $ref)
        }
    </c02>
};

(: transform a group of Faust objects with the same signature to a series-level component :)
declare function local:transform-series($faust-main as document-node(), $faust-xtra as document-node(), $struc as xs:string, $sign as xs:string) as element() {
    let $series := $faust-main/ED/FAUST-Objekt[Signatur/text() = $sign]
    return <c01 level="series">
        <did>
            <unitid label="ehri_main_identifier">{ $sign }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        {
            for $tome at $pos-tome in fn:distinct-values($series/Bandnummer)
            let $struc-tome := fn:concat($struc, ".", fn:string($pos-tome))
            return local:transform-subseries($faust-main, $faust-xtra, $struc-tome, $sign, $tome)
        }
    </c01>
};

(: transform a group of Faust objects to a recordgroup-level component :)
declare function local:transform-recordgroup($faust-main as document-node(), $faust-xtra as document-node(), $struc as xs:string) as element() {
    let $recordgroup := $faust-main/ED/FAUST-Objekt
    return <archdesc level="recordgrp">
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        <dsc>
            {
                for $sign at $pos-sign in fn:distinct-values($recordgroup/Signatur)
                let $struc-sign := fn:concat($struc, ".", fn:string($pos-sign))
                return local:transform-series($faust-main, $faust-xtra, $struc-sign, $sign)
            }
        </dsc>
    </archdesc>
};

(: transform a Faust file to EAD :)
declare function local:transform($faust-main as document-node(), $faust-xtra as document-node()) as element() {
    <ead
    xmlns="urn:isbn:1-931666-22-9"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd"
    audience="external">
        { local:gen-header() }
        { local:transform-recordgroup($faust-main, $faust-xtra, "1") }
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $faust-main := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-main.xml"
let $faust-xtra := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-xtra.xml"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/faust-output/"

(: transform input and write output :)
for $ead at $pos-ead in local:transform(fn:doc($faust-main), fn:doc($faust-xtra))
let $file-path := fn:concat($output-dir, "ead_", local:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
