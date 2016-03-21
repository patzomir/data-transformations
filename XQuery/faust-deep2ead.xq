xquery version "3.0";

(: pad a number with leading zeroes :)
(: - $number: the number as a string :)
(: - $length: the total length of the padded number as integer :)
declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
    if (fn:string-length($number) = $length)
    then $number
    else local:pad-with-zeroes(fn:concat("0", $number), $length)
};

declare function local:struc2tag($struc as xs:string) as xs:string {
    let $level := fn:string-length(fn:replace($struc, "[^\.]", ""))
    let $tag := fn:concat("c", local:pad-with-zeroes(fn:string($level), 2))
    return $tag
};

(: generate a sequence of elements, wrapping each child with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function local:wrap-each($tag as xs:string, $children as item()*) as element()* {
    for $child in $children
    return element { $tag } { $child }
};

(: generate a sequence of elements, wrapping all children with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function local:wrap-all($tag as xs:string, $children as item()*) as element()? {
    if (fn:empty($children))
    then ()
    else element { $tag } { $children }
};

(: generate an EAD header element :)
declare function local:gen-header() as element() {
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
declare function local:get-xtra-info($faust-xtra as document-node(), $ref as xs:string) as element()? {
    let $ref := fn:concat("Objekt ", $ref, " / ED")
    return fn:zero-or-one($faust-xtra/ED/FAUST-Objekt[Weitere_Bestandsangaben/text() = $ref])
};

declare function local:transform-file($file as element(), $faust-xtra as document-node(), $struc as xs:string) as element() {
    let $file := fn:exactly-one($file/ED/FAUSTObjekt)
    let $xtra-info := local:get-xtra-info($faust-xtra, $file/Ref/text())
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

                local:wrap-each("unitdate", $file/Datierungsangaben/text()),
                local:wrap-each("unittitle", $file/Titel/text()),
                local:wrap-each("unittitle", $file/Untertitel/text()),
                local:wrap-each("unittitle", $file/Bestand/text()),
                local:wrap-all("unittitle", (
                    local:wrap-each("occupation", $file/Beruf/text()))),
                local:wrap-all("abstract", (
                    local:wrap-each("p", $file/Bestandskurzbeschreibung/text()))),
                local:wrap-all("origination", (
                    local:wrap-each("persname", $file/Autor/text()))),
                local:wrap-all("physdesc", (
                    local:wrap-each("dimensions", $file/Umfang/text())))
            }
        </did>,

        local:wrap-all("bioghist", local:wrap-each("p", $xtra-info/Vita/text())),
        local:wrap-all("scopecontent", (
            local:wrap-each("p", $xtra-info/Zum_Bestand/text()),
            local:wrap-each("p", $file/Enthalt/text()),
            local:wrap-each("p", $file/Darin_auch/text()))),
        local:wrap-all("accessrestrict", (
            local:wrap-each("p", $xtra-info/Bestandsnutzung/text()))),
        local:wrap-all("altformavail", (
            local:wrap-each("p", $file/Digitalisierung/text()),
            local:wrap-each("p", $file/Internetadresse/text()),
            local:wrap-each("p", $file/Online_Prasentation/text()))),
        local:wrap-all("controlaccess", (
            local:wrap-each("persname", $file/Personenregister/text()),
            local:wrap-each("subject", $file/Sachregister/text()),
            local:wrap-each("subject", $file/Thesaurus/text())))
    }
};

declare function local:transform-series($series as element(), $faust-xtra as document-node(), $struc as xs:string) as element() {
    element { local:struc2tag($struc) } {
        attribute level { "series" },

        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
            { local:wrap-each("unittitle", $series/collection_Titel/text()) }
        </did>,

        for $subseries at $pos-subser in $series/collection
        let $struc-subser := fn:concat($struc, ".", fn:string($pos-subser))
        return local:transform-series($subseries, $faust-xtra, $struc-subser),

        let $num-subser := fn:count($series/collection)
        for $file at $pos-file in $series/FAUST-Objekt
        let $struc-file := fn:concat($struc, ".", fn:string($num-subser + $pos-file))
        return local:transform-file($file, $faust-xtra, $struc-file)
    }
};

declare function local:transform-recordgroup($recordgroup as element(), $faust-xtra as document-node(), $struc as xs:string) as element() {
    <archdesc level="recordgrp">
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
        </did>
        <dsc>
            {
                for $series at $pos-ser in $recordgroup/collection
                let $struc-ser := fn:concat($struc, ".", fn:string($pos-ser))
                return local:transform-series($series, $faust-xtra, $struc-ser),

                let $num-ser := fn:count($recordgroup/collection)
                for $file at $pos-file in $recordgroup/FAUST-Objekt
                let $struc-file := fn:concat($struc, ".", fn:string($num-ser + $pos-file))
                return local:transform-file($file, $faust-xtra, $struc-file)
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
        { local:transform-recordgroup($faust-main/*:root, $faust-xtra, "1") }
    </ead>
};

(: serialization parameters :)
let $ser-params := map { "omit-xml-declaration": "no" }
let $pad-length := 4

(: file locations :)
let $faust-main := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-main-deep.xml"
let $faust-xtra := "/home/georgi/IdeaProjects/TestBaseX/data/faust-input/xport-xtra.xml"
let $output-dir := "/home/georgi/IdeaProjects/TestBaseX/data/faust-output/"

(: transform input and write output :)
for $ead at $pos-ead in local:transform(fn:doc($faust-main), fn:doc($faust-xtra))
let $file-path := fn:concat($output-dir, "ead_", local:pad-with-zeroes(fn:string($pos-ead), $pad-length), ".xml")
return file:write($file-path, $ead, $ser-params)
