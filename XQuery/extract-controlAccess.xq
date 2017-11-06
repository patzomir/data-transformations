
declare namespace ead = "urn:isbn:1-931666-22-9";
declare option saxon:output "method=text";
declare variable $input external;

let $tab := "&#9;"
let $new := "&#10;"

for $x in doc($input)//ead:controlaccess/*
  return fn:concat(fn:node-name($x), $tab, $x/text(), $tab, $x/@authfilenumber, $new)

