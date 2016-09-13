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
        let $name-prefix := fn:substring-before($child-name, ":")
        let $child-qname := if ($name-prefix) then fn:QName($namespaces($name-prefix), $child-name) else $child-name
        let $child := attribute { $child-qname } { $child-value }
        return if ($child-value) then $child else ()
      else
        let $name-prefix := fn:substring-before($child-name, ":")
        let $child-qname := fn:QName($namespaces($name-prefix), $child-name)
        let $child-children := transform:make-children(fn:concat($target-path, $child-name, "/"), $child-source-node, $configuration, $namespaces)
        let $child := element { $child-qname } { $child-children, $child-value }
        return if ($child-children or $child-value) then $child else ()
};

declare function transform:evaluate-xquery($xquery as xs:string?, $context as node()) as item()* {
  if ($xquery) then xquery:eval($xquery, map { "": $context }) else ()
};
