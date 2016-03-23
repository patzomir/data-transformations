xquery version "3.0";

import module namespace shared = "shared" at "shared.xq";
import module namespace shared-faust = "shared-faust" at "shared-faust.xq";

(: transform a single Faust object to a file-level component :)
declare function local:transform-file($input-main as document-node(), $input-xtra as document-node(), $struc as xs:string, $sign as xs:string, $tome as xs:string, $ref as xs:string) as element() {
    let $file := fn:exactly-one($input-main/ED/FAUST-Objekt[Signatur/text() = $sign and Bandnummer/text() = $tome and Ref/text() = $ref])
    let $xtra-info := shared-faust:get-xtra-info($input-xtra, $ref)
    return <c03 level="file">
        <did>
            <unitid label="ehri_main_identifier" identifier="{ $ref }">{ $sign } / { $tome }, { $ref }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
            {
                shared:wrap-each("unitdate", fn:string-join((fn:zero-or-one($file/Laufzeit-Beginn/text()), fn:zero-or-one($file/Laufzeit-Ende/text())), "-")),
                shared:wrap-each("unittitle", $file/Titel/text()),
                shared:wrap-each("unittitle", $file/Untertitel/text()),
                shared:wrap-all("abstract", (
                    shared:wrap-each("p", $file/Bestandskurzbeschreibung/text()))),
                shared:wrap-all("origination", (
                    shared:wrap-each("persname", $file/Autor/text()))),
                shared:wrap-all("physdesc", (
                    shared:wrap-each("dimensions", $file/Umfang/text())))
            }
        </did>
        {
            shared:wrap-all("bioghist", (
                shared:wrap-each("p", $xtra-info/Vita/text()))),
            shared:wrap-all("scopecontent", (
                shared:wrap-each("p", $xtra-info/Zum_Bestand/text()),
                shared:wrap-each("p", $file/Enthalt/text()),
                shared:wrap-each("p", $file/Darin_auch/text()))),
            shared:wrap-all("accessrestrict", (
                shared:wrap-each("p", $xtra-info/Bestandsnutzung/text()))),
            shared:wrap-all("altformavail", (
                shared:wrap-each("p", $file/Digitalisierung/text()),
                shared:wrap-each("p", $file/Internetadresse/text()),
                shared:wrap-each("p", $file/Online_Prasentation/text()))),
            shared:wrap-all("controlaccess", (
                shared:wrap-each("persname", fn:tokenize($file/Personenregister/text(), ";\s*")),
                shared:wrap-each("subject", fn:tokenize($file/Sachregister/text(), ";\s*")),
                shared:wrap-each("subject", $file/Thesaurus/text())))
        }
    </c03>
};

(: transform a group of Faust objects with the same signature and tome number to a subseries-level component :)
declare function local:transform-subseries($input-main as document-node(), $input-xtra as document-node(), $struc as xs:string, $sign as xs:string, $tome as xs:string) as element() {
    let $subseries := $input-main/ED/FAUST-Objekt[Signatur/text() = $sign and Bandnummer/text() = $tome]
    return <c02 level="subseries">
        <did>
            <unitid label="ehri_main_identifier">{ $sign } / { $tome }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        {
            for $ref at $pos-ref in fn:distinct-values($subseries/Ref)
            let $struc-ref := fn:concat($struc, ".", fn:string($pos-ref))
            return local:transform-file($input-main, $input-xtra, $struc-ref, $sign, $tome, $ref)
        }
    </c02>
};

(: transform a group of Faust objects with the same signature to a series-level component :)
declare function local:transform-series($input-main as document-node(), $input-xtra as document-node(), $struc as xs:string, $sign as xs:string) as element() {
    let $series := $input-main/ED/FAUST-Objekt[Signatur/text() = $sign]
    return <c01 level="series">
        <did>
            <unitid label="ehri_main_identifier">{ $sign }</unitid>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        {
            for $tome at $pos-tome in fn:distinct-values($series/Bandnummer)
            let $struc-tome := fn:concat($struc, ".", fn:string($pos-tome))
            return local:transform-subseries($input-main, $input-xtra, $struc-tome, $sign, $tome)
        }
    </c01>
};

(: transform a group of Faust objects to a recordgroup-level component :)
declare function local:transform-recordgroup($input-main as document-node(), $input-xtra as document-node(), $struc as xs:string) as element() {
    let $recordgroup := $input-main/ED/FAUST-Objekt
    return <archdesc level="recordgrp">
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        <dsc>
            {
                for $sign at $pos-sign in fn:distinct-values($recordgroup/Signatur)
                let $struc-sign := fn:concat($struc, ".", fn:string($pos-sign))
                return local:transform-series($input-main, $input-xtra, $struc-sign, $sign)
            }
        </dsc>
    </archdesc>
};

(: transform a Faust file to EAD :)
declare function local:transform($input-main as document-node(), $input-xtra as document-node()) as element() {
    <ead
    xmlns="urn:isbn:1-931666-22-9"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd"
    audience="external">
        { shared-faust:gen-header() }
        { local:transform-recordgroup($input-main, $input-xtra, "1") }
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $input-main := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-main.xml"
let $input-xtra := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-xtra.xml"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/faust-output/"

(: transform input and write output :)
for $ead at $pos-ead in local:transform(fn:doc($input-main), fn:doc($input-xtra))
let $file-path := fn:concat($output-dir, "ead_", shared:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
