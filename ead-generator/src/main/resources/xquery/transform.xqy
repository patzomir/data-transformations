xquery version "3.0";

import module namespace transform = "transform" at "ead-generator/src/main/resources/xquery/transform.xqm";

declare variable $namespaces as map(xs:string, xs:string) external;
declare variable $structure-path as xs:string external;
declare variable $configuration as xs:string external;
declare variable $source-dir as xs:string external;
declare variable $target-dir as xs:string external;

declare function local:pad-with-zeroes(
  $number as xs:string,
  $length as xs:integer
) as xs:string {
  if (fn:string-length($number) = $length) then $number
  else local:pad-with-zeroes(fn:concat("0", $number), $length)
};

for $source-path-relative in file:list($source-dir, fn:false(), "*.xml,*.XML")
  let $source-document := fn:doc(fn:concat($source-dir, $source-path-relative))
  for $target-document at $count in transform:transform($source-document, $configuration, $namespaces, $structure-path)
    let $target-path := fn:concat(
      $target-dir,
      fn:substring-before($source-path-relative, "."),
      "_", local:pad-with-zeroes(fn:string($count), 9),
      ".", fn:substring-after($source-path-relative, ".")
    )
  
    return file:write($target-path, $target-document, map { "omit-xml-declaration": "no" })
