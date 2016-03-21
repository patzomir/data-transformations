xquery version "3.0";

(: pad a number with leading zeroes :)
(: - $number: the number as a string :)
(: - $length: the total length of the padded number as integer :)
declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
    if (fn:string-length($number) = $length)
    then $number
    else local:pad-with-zeroes(fn:concat("0", $number), $length)
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
            {
                local:wrap-each("unitdate", fn:string-join((fn:zero-or-one($file/Laufzeit-Beginn/text()), fn:zero-or-one($file/Laufzeit-Ende/text())), "-")),
                local:wrap-each("unittitle", $file/Titel/text()),
                local:wrap-each("unittitle", $file/Untertitel/text()),
                local:wrap-all("abstract", (
                    local:wrap-each("p", $file/Bestandskurzbeschreibung/text()))),
                local:wrap-all("origination", (
                    local:wrap-each("persname", $file/Autor/text()))),
                local:wrap-all("physdesc", (
                    local:wrap-each("dimensions", $file/Umfang/text())))
            }
        </did>
        {
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
                local:wrap-each("persname", fn:tokenize($file/Personenregister/text(), ";\s*")),
                local:wrap-each("subject", fn:tokenize($file/Sachregister/text(), ";\s*")),
                local:wrap-each("subject", $file/Thesaurus/text())))
        }
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
