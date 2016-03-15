xquery version "3.0";

declare function local:faust2eads($main as xs:string, $xtra as xs:string, $mapp as xs:string) as element()* {
    let $root := doc($main)/root

    for $collection in $root/collection
    return <ead xmlns="urn:isbn:1-931666-22-9" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xlink="http://www.w3.org/1999/xlink" audience="external" xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd">
        { local:make-header() }
        <archdesc level="recordgrp">
            <did>
                <unitid label="ehri_internal_id">0</unitid>
                <unitid label="ehri_structure">0</unitid>
                <unittitle encodinganalog="collection_Titel">Nachlässe</unittitle>
                <unitid label="ehri_main_identifier">NL</unitid>
            </did>
            <scopecontent>
                <p>Die Abgabe privater Unterlagen an das IfZ-Archiv blieb in den ersten Jahrzehnten seines Bestehens auf Ausnahmefälle beschränkt. Erst Ende der 70er Jahre wuchs die Nachlassabteilung an. In der mittlerweile großen Bestandsgruppe der Nachlässe befinden sich viele Unterlagen von Wehrmachtsangehörigen, Angehörigen des militärischen und zivilen deutschen Widerstands sowie Unterlagen von Verfolgten. Nachdem die Forschung sich seit einiger Zeit verstärkt mit Alltagsgeschichte beschäftigt, übernimmt das Archiv seit einigen Jahren verstärkt auch dafür einschlägige Unterlagen, beispielsweise von früheren HJ- und BDM-Mitgliedern sowie Feldpost. Neben der Zeit des Nationalsozialismus bilden Unterlagen zu Neuen Sozialen Bewegungen, die oftmals auch die Aufarbeitung der NS-Vergangenheit widerspiegeln, einen weiteren Schwerpunkt der Nachlass-Überlieferung.</p>
            </scopecontent>
            <processinfo>
                <p>This collection has been selected by EHRI. Only file descriptions with relevant keywords have been included.</p>
            </processinfo>
            <dsc>
                { local:collection2component($collection, 1) }
            </dsc>
        </archdesc>
    </ead>
};

declare function local:make-header() as element() {
    <eadheader>
        <eadid countrycode="DE">NL</eadid>
        <filedesc>
            <titlestmt>
                <titleproper/>
                <author>IfZ</author>
            </titlestmt>
        </filedesc>
        <profiledesc>
            <creation>EHRI created this EAD based on the Faust-output and selection from the IfZ München
                <date>2015-03-04T09:15:25.542+02:00</date></creation>
            <langusage>
                <language scriptcode="Latn" langcode="ger">German</language>
            </langusage>
        </profiledesc>
        <revisiondesc>
            <change>
                <date>2015-03-05 15:50:53</date>
                <item>EHRI has choosen the ehri_structure to be the unitid with label ehri_main_identifier if none were given. If multiple ehri_main_identifier were given, their label was renamed to ehri_multiple_identifier</item>
            </change>
            <change>
                <date>2015/03/05 15:50:49</date>
                <item>EHRI added a unitid with label "ehri_internal_identifier" to give every node a unique id.</item>
            </change>
            <change>
                <date>2015-03-05 15:50:43</date>
                <item>EHRI added a unitid with label "ehri_structure" to indicate the structure of the EAD file on every c-node. This is done to make comparisons of two versions of the same EAD (as indicated by the eadid) possible.</item>
            </change>
        </revisiondesc>
    </eadheader>
};

declare function local:collection2component($collection as element(), $level as xs:integer) as element() {
    let $tag := concat("c", local:pad-with-zeroes(string($level), 2))
    return element { $tag } {
        attribute level { "series" },
        element did { local:collection2did($collection)/* },

        for $subcollection in $collection/collection
        return local:collection2component($subcollection, $level + 1)
    }
};

declare function local:collection2did($collection as element()) as element() {
    <did>
        <unittitle encodinganalog="collection_Titel">{ data($collection/collection_Titel) }</unittitle>
    </did>
};

declare function local:pad-with-zeroes($number as xs:string, $num_zeroes as xs:integer) as xs:string {
    if (string-length($number) = $num_zeroes)
    then $number
    else local:pad-with-zeroes(concat("0", $number), $num_zeroes)
};

let $params := map { "omit-xml-declaration": "no" }

let $input_main := "/home/georgi/IdeaProjects/TestBaseX/data/EHRI_Export_personalpapers.xml"
let $input_xtra := "/home/georgi/IdeaProjects/TestBaseX/data/EHRI_weitereBestandsang.xml"
let $input_mapp := "/home/georgi/IdeaProjects/TestBaseX/data/mappingSignaturRefWeitere.xml"
let $output_dir := "/home/georgi/IdeaProjects/TestBaseX/data/out/"
let $output_pre := "ead_"
let $output_suf := ".xml"
let $num_zeroes := 4

for $ead at $count in local:faust2eads($input_main, $input_xtra, $input_mapp)
let $file := concat($output_dir, $output_pre, local:pad-with-zeroes(string($count), $num_zeroes), $output_suf)
return file:write($file, $ead, $params)
