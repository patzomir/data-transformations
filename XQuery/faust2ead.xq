xquery version "3.0";

(: transform Faust to EAD :)
declare function local:transform-faust($main as document-node(), $xtra as document-node(), $mapp as document-node()) as element()* {
    let $root := $main/root

    (: in the example, this indexing is inconsistent: sometimes it starts at 0 and sometimes at 1 :)
    (: I cannot grasp the logic behind the example, so I will simply assume it always start at 1 :)
    let $root_struc := "1"

    for $collection at $count in $root/collection
    let $struc := concat($root_struc, ".", string($count))
    let $sig := $collection//FAUST-Objekt/ED/FAUSTObjekt/Signatur/text()
    let $xtra_info := local:get-xtra-info($sig[1], $xtra, $mapp)
    return <ead xmlns="urn:isbn:1-931666-22-9" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xlink="http://www.w3.org/1999/xlink" audience="external" xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd">
        { local:generate-header() }
        <archdesc level="recordgrp">
            <did>
                <unitid label="ehri_internal_id">0</unitid>
                <unitid label="ehri_structure">{ $root_struc }</unitid>
                <unitid label="ehri_main_identifier">NL</unitid>
                <unittitle encodinganalog="collection_Titel">Nachlässe</unittitle>
            </did>
            <scopecontent>
                <p>Die Abgabe privater Unterlagen an das IfZ-Archiv blieb in den ersten Jahrzehnten seines Bestehens auf Ausnahmefälle beschränkt. Erst Ende der 70er Jahre wuchs die Nachlassabteilung an. In der mittlerweile großen Bestandsgruppe der Nachlässe befinden sich viele Unterlagen von Wehrmachtsangehörigen, Angehörigen des militärischen und zivilen deutschen Widerstands sowie Unterlagen von Verfolgten. Nachdem die Forschung sich seit einiger Zeit verstärkt mit Alltagsgeschichte beschäftigt, übernimmt das Archiv seit einigen Jahren verstärkt auch dafür einschlägige Unterlagen, beispielsweise von früheren HJ- und BDM-Mitgliedern sowie Feldpost. Neben der Zeit des Nationalsozialismus bilden Unterlagen zu Neuen Sozialen Bewegungen, die oftmals auch die Aufarbeitung der NS-Vergangenheit widerspiegeln, einen weiteren Schwerpunkt der Nachlass-Überlieferung.</p>
            </scopecontent>
            <processinfo>
                <p>This collection has been selected by EHRI. Only file descriptions with relevant keywords have been included.</p>
            </processinfo>
            <dsc>
                <c01 level="series">
                    <did>
                        <unitid label="ehri_structure">{ $struc }</unitid>
                        <unittitle encodinganalog="collection_Titel">{ data($collection/*:collection_Titel) }</unittitle>
                    </did>
                    <bioghist encodinganalog="Vita">
                        <p>{ data($xtra_info/*:Vita) }</p>
                    </bioghist>
                    <scopecontent encodinganalog="Zum_Bestand">
                        <p>{ data($xtra_info/*:Zum_Bestand) }</p>
                    </scopecontent>
                    <accessrestrict encodinganalog="Bestandsnutzung">
                        <p>{ data($xtra_info/*:Bestandsnutzung) }</p>
                    </accessrestrict>
                    { local:transform-collections($collection, 2, $struc) }
                </c01>
            </dsc>
        </archdesc>
    </ead>
};

(: get the "Weitere Bestandsangaben" for a given collection :)
(: - $sig is the <Signatur> of the first <FAUST-Objekt> descendant (!) of a top-level (!) <collection> :)
declare function local:get-xtra-info($sig as xs:string, $xtra as document-node(), $mapp as document-node()) as element()? {
    let $mapping := $mapp/table/row[./value[1]/text() = concat($sig, " / 1 -")]/value[2]/text()
    let $xtra_info := $xtra/ED/FAUST-Objekt[./Weitere_Bestandsangaben/text() = concat("Objekt ", $mapping, " / ED")]
    return $xtra_info
};

(: generate the <eadheader> element (copied from example) :)
declare function local:generate-header() as element() {
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


(: recursively transform all <collection> elements in a given element :)
declare function local:transform-collections($parent as element(), $level as xs:integer, $parent_struc as xs:string) as element()* {
    let $tag := concat("c", local:pad-with-zeroes(string($level), 2))

    for $collection at $count in $parent/collection
    let $struc := concat($parent_struc, ".", string($count))
    return element { $tag } {
        attribute level { "series" },
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
            <unittitle encodinganalog="collection_Titel">{ data($collection/collection_Titel) }</unittitle>
        </did>,
        local:transform-faustobjekte($collection, $level + 1, $struc),
        local:transform-collections($collection, $level + 1, $struc)
    }
};

(: transform all <FAUST-Objekt> elements in a given element :)
declare function local:transform-faustobjekte($parent as element(), $level as xs:integer, $parent_struc as xs:string) as element()* {
    let $tag := concat("c", local:pad-with-zeroes(string($level), 2))

    for $faustobjekt at $count in $parent/FAUST-Objekt
    let $struc := concat($parent_struc, ".", string($count))
    return element { $tag } {
        attribute level { "file" },
        <did>
            <unitid label="ehri_structure">{ $struc }</unitid>
            <unitid label="ehri_main_identifier">{ data($faustobjekt/ED/FAUSTObjekt/Signatur) } / { data($faustobjekt/ED/FAUSTObjekt/Bandnummer) }</unitid>
            <unitid encodinganalog="Signatur" identifier="{ data($faustobjekt/ED/FAUSTObjekt/Ref) }">{ data($faustobjekt/ED/FAUSTObjekt/Signatur) } / { data($faustobjekt/ED/FAUSTObjekt/Bandnummer) }</unitid>
            <unittitle encodinganalog="Titel">{ data($faustobjekt/ED/FAUSTObjekt/Titel) }</unittitle>
            <unitdate encodinganalog="Laufzeit">{ data($faustobjekt/ED/FAUSTObjekt/LaufzeitBeginn) }-{ data($faustobjekt/ED/FAUSTObjekt/LaufzeitEnde) }</unitdate>
        </did>,
        <scopecontent encodinganalog="Enthält">
            <p>{ data($faustobjekt/ED/FAUSTObjekt/Enthält) }</p>
        </scopecontent>,
        <controlaccess>
            {
            (: in the example output this is mapped to <subject> but the EAD documentation says this is better :)
            (: see http://eadiva.com/subject/ :)
                for $person in $faustobjekt/ED/FAUSTObjekt/Personenregister
                return <persname role="subject" encodinganalog="Personenregister">{ data($person) }</persname>
            }
            {
                for $subject in $faustobjekt/ED/FAUSTObjekt/Subject
                return <subject encodinganalog="Subject">{ data($subject) }</subject>
            }
            {
                for $subject in $faustobjekt/ED/FAUSTObjekt/Thesaurus
                return <subject encodinganalog="Thesaurus">{ data($subject) }</subject>
            }
        </controlaccess>
    }
};

(: helper function which pads a number with leading zeroes :)
(: - $number is the string representation of the number :)
(: - $length is the total number of digits the padded number will have :)
declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
    if (string-length($number) = $length)
    then $number
    else local:pad-with-zeroes(concat("0", $number), $length)
};

(: serialization parameters used by BaseX file module, see http://docs.basex.org/wiki/Serialization :)
let $params := map { "omit-xml-declaration": "no" }

(: script arguments, consider binding to external variables :)
let $input_main := "/home/georgi/IdeaProjects/TestBaseX/data/EHRI_Export_personalpapers.xml"
let $input_xtra := "/home/georgi/IdeaProjects/TestBaseX/data/EHRI_weitereBestandsang.xml"
let $input_mapp := "/home/georgi/IdeaProjects/TestBaseX/data/mappingSignaturRefWeitere.xml"
let $output_dir := "/home/georgi/IdeaProjects/TestBaseX/data/out/"
let $output_pre := "ead_"
let $output_suf := ".xml"
let $num_zeroes := 4

(: transform and write each EAD to separate file :)
for $ead at $count in local:transform-faust(doc($input_main), doc($input_xtra), doc($input_mapp))
let $file := concat($output_dir, $output_pre, local:pad-with-zeroes(string($count), $num_zeroes), $output_suf)
return file:write($file, $ead, $params)
