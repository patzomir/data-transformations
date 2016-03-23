xquery version "3.0";

module namespace shared = "shared";

(: default namespace for EAD elements :)
declare default element namespace "urn:isbn:1-931666-22-9";

(: pad a number with leading zeroes :)
(: - $number: the number as a string :)
(: - $length: the total length of the padded number as integer :)
declare function shared:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
    if (fn:string-length($number) = $length)
    then $number
    else shared:pad-with-zeroes(fn:concat("0", $number), $length)
};

(: generate a sequence of elements, wrapping all children with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - attributes: a map from attribute name to corresponding value :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function shared:wrap-all($tag as xs:string, $attributes as map(xs:string, xs:string), $children as item()*) as element()? {
    if (fn:empty($children))
    then ()
    else element { $tag }

    {
        for $key in map:keys($attributes)
        return attribute { $key } { $attributes($key) },
        $children
    }
};

(: generate a sequence of elements, wrapping all children with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function shared:wrap-all($tag as xs:string, $children as item()*) as element()? {
    shared:wrap-all($tag, map { }, $children)
};

(: generate a sequence of elements, wrapping each child with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - attributes: a map from attribute name to corresponding value :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function shared:wrap-each($tag as xs:string, $attributes as map(xs:string, xs:string), $children as item()*) as element()* {
    for $child in $children
    return element { $tag }

    {
        for $key in map:keys($attributes)
        return attribute { $key } { $attributes($key) },
        $child
    }
};

(: generate a sequence of elements, wrapping each child with the given tag :)
(: - $tag: the tag of the elements to generate as string :)
(: - $children: a sequence of children (e.g. text nodes or other element nodes) :)
declare function shared:wrap-each($tag as xs:string, $children as item()*) as element()* {
    shared:wrap-each($tag, map { }, $children)
};
