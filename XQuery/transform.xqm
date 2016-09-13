xquery version "3.0";

(: module for configurable XML transformations :)
module namespace transform = "transform";

declare function transform:transform($source-document as document-node(), $configuration-path as xs:string, $namespaces as map(xs:string, xs:string)) as document-node()* {
  let $configuration := csv:parse(file:read-text($configuration-path), map { "separator": "tab", "header": "yes", "quotes": "no" })
    for $target-root-node in transform:make-children("/", $source-document, $configuration, $namespaces)
    return document { $target-root-node }
};

declare function transform:make-children($target-path as xs:string, $source-node as node(), $configuration as document-node(), $namespaces as map(xs:string, xs:string)) as node()* {
  for $configuration-record in $configuration/csv/record[target-path/text() = $target-path]
    for $child-source-node in transform:evaluate-xquery($configuration-record/source-node/text(), $source-node)
    let $child-value := transform:evaluate-xquery($configuration-record/value/text(), $child-source-node)
    let $child-name := $configuration-record/target-node/text()
    return
      if (fn:starts-with($child-name, "@")) then
        let $child-name := fn:substring($child-name, 2)
        let $child := attribute { $child-name } { $child-value }
        return if ($child-value) then $child else ()
      else
        let $child-children := transform:make-children(fn:concat($target-path, $child-name, "/"), $child-source-node, $configuration, $namespaces)
        let $child := element { transform:qualify-name($child-name, $namespaces) } { $child-children, $child-value }
        return if ($child-children or $child-value) then $child else ()
};

declare function transform:evaluate-xquery($xquery as xs:string?, $context as node()) as item()* {
  if ($xquery) then xquery:eval($xquery, map { "": $context }) else ()
};

declare function transform:qualify-name($name as xs:string, $namespaces as map(xs:string, xs:string)) as xs:QName {
  if (fn:contains($name, ":")) then
    fn:QName($namespaces(fn:substring-before($name, ":")), $name)
  else
    fn:QName($namespaces(""), $name)
};
