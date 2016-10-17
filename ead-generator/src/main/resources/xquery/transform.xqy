xquery version "3.0";

import module namespace transform = "transform" at "ead-generator/src/main/resources/xquery/transform.xqm";

declare variable $namespaces as map(xs:string, xs:string) external;
declare variable $structure-path as xs:string external;
declare variable $configuration as xs:string external;
declare variable $source-path as xs:string external;
declare variable $target-path as xs:string external;

declare function local:pad-with-zeroes($number as xs:string, $length as xs:integer) as xs:string {
  if (fn:string-length($number) = $length) then $number
  else local:pad-with-zeroes(fn:concat("0", $number), $length)
};

let $source-document := fn:doc($source-path)
for $target-document at $count in transform:transform($source-document, $configuration, $namespaces, $structure-path)
  let $target-path-with-count := fn:concat(
    fn:substring-before($target-path, "."),
    "_", local:pad-with-zeroes(fn:string($count), 3),
    ".", fn:substring-after($target-path, ".")
  )

  return file:write($target-path-with-count, $target-document, map { "omit-xml-declaration": "no" })
