xquery version "3.0";

import module namespace shared = "shared" at "shared.xq";
import module namespace shared-faust = "shared-faust" at "shared-faust.xq";

(: generate a component tag based on its structure string :)
declare function local:struc2tag($struc as xs:string) as xs:string {
    let $level := fn:string-length(fn:replace($struc, "[^\.]", ""))
    let $tag := fn:concat("c", shared:pad-with-zeroes(fn:string($level), 2))
    return $tag
};

(: transform a file-level component :)
declare function local:transform-file($file as element(), $input-xtra as document-node(), $struc as xs:string) as element() {
    let $file := fn:exactly-one($file/ED/FAUSTObjekt)
    let $xtra-info := shared-faust:get-xtra-info($input-xtra, $file/Ref/text())
    return element { local:struc2tag($struc) } {
        attribute level {"file"},

        <did>
            <unitid label="ehri_main_identifier" identifier="{ $file/Ref/text() }">{ $file/Signatur/text() } / { $file/Bandnummer/text() }, { $file/Ref/text() }</unitid>
            <unitid label="ehri_structure">{$struc}</unitid>
            {
                let $date-range := fn:string-join((fn:zero-or-one($file/LaufzeitBeginn/text()), fn:zero-or-one($file/LaufzeitEnde/text())), "-")
                return if (fn:string-length($date-range) > 0)
                then <unitdate normal="{ fn:replace($date-range, "-", "/") }">{ $date-range }</unitdate>
                else (),

                shared:wrap-each("unitdate", $file/Datierungsangaben/text()),
                shared:wrap-each("unittitle", $file/Titel/text()),
                shared:wrap-each("unittitle", $file/Untertitel/text()),
                shared:wrap-each("unittitle", $file/Bestand/text()),
                shared:wrap-all("unittitle", (
                    shared:wrap-each("occupation", $file/Beruf/text()))),
                shared:wrap-all("abstract", (
                    shared:wrap-each("p", $file/Bestandskurzbeschreibung/text()))),
                shared:wrap-all("origination", (
                    shared:wrap-each("persname", $file/Autor/text()))),
                shared:wrap-all("physdesc", (
                    shared:wrap-each("dimensions", $file/Umfang/text())))
            }
        </did>,

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
            shared:wrap-each("persname", $file/Personenregister/text()),
            shared:wrap-each("subject", $file/Sachregister/text()),
            shared:wrap-each("subject", $file/Thesaurus/text())))
    }
};

(: transform a series-level component :)
declare function local:transform-series($series as element(), $input-xtra as document-node(), $struc as xs:string) as element() {
    element { local:struc2tag($struc) } {
        attribute level { "series" },

        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
            { shared:wrap-each("unittitle", $series/collection_Titel/text()) }
        </did>,

        for $subseries at $pos-subser in $series/collection
        let $struc-subser := fn:concat($struc, ".", fn:string($pos-subser))
        return local:transform-series($subseries, $input-xtra, $struc-subser),

        let $num-subser := fn:count($series/collection)
        for $file at $pos-file in $series/FAUST-Objekt
        let $struc-file := fn:concat($struc, ".", fn:string($num-subser + $pos-file))
        return local:transform-file($file, $input-xtra, $struc-file)
    }
};

(: transform a recordgroup-level component :)
declare function local:transform-recordgroup($recordgroup as element(), $input-xtra as document-node(), $struc as xs:string) as element() {
    <archdesc level="recordgrp">
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        <dsc>
            {
                for $series at $pos-ser in $recordgroup/collection
                let $struc-ser := fn:concat($struc, ".", fn:string($pos-ser))
                return local:transform-series($series, $input-xtra, $struc-ser),

                let $num-ser := fn:count($recordgroup/collection)
                for $file at $pos-file in $recordgroup/FAUST-Objekt
                let $struc-file := fn:concat($struc, ".", fn:string($num-ser + $pos-file))
                return local:transform-file($file, $input-xtra, $struc-file)
            }
        </dsc>
    </archdesc>
};

(: transform a Faust input to EAD :)
declare function local:transform($input-main as document-node(), $input-xtra as document-node()) as element()* {
    <ead
    xmlns="urn:isbn:1-931666-22-9"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd"
    audience="external">
        { shared-faust:gen-header() }
        { local:transform-recordgroup($input-main/*:root, $input-xtra, "1") }
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $input-main := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-main-deep.xml"
let $input-xtra := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-xtra.xml"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/faust-output/"

(: transform input and write output :)
for $ead at $pos-ead in local:transform(fn:doc($input-main), fn:doc($input-xtra))
let $file-path := fn:concat($output-dir, "ead_", shared:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
