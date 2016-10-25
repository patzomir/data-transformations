xquery version "3.0";

(: module for configurable XML transformations :)
(: configuration is a TSV file with four columns: :)
(: 1. target-path: path defining the location of the target node (slash-separated list of element names; must end with slash) :)
(: 2. target-node: name of the target node (attributes prefixed with "@"; may contain defined namespace prefixes) :)
(: 3. source-node: XQuery expression returning the corresponding node in the source document (evaluated in the context of the parent source node) :)
(: 4. value: XQuery expression returning the value of the target node (evaluated in the context of the source node; may be empty) :)
module namespace transform = "transform";

(: transform a source document into target documents with the given configuration and namespaces :)
(: $source-document: the source document as a single document node (if you need to transform multiple documents at the same time, you can wrap them in a single document node) :)
(: $configuration-path: the path to the transformation configuration :)
(: $namespaces: map from namespace prefix to namespace URI :)
(: returns: a list of target the document nodes :)
declare function transform:transform(
  $source-document as document-node(),
  $configuration as xs:string,
  $namespaces as map(xs:string, xs:string)
) as document-node()* {
  let $configuration := csv:parse($configuration, map { "separator": "tab", "header": "yes", "quotes": "no" })
    for $target-root-node in transform:make-children("/", $source-document, $configuration, $namespaces)
    return document { $target-root-node }
};

(: like the above function but checks the structure of the configured target elements :)
declare function transform:transform(
  $source-document as document-node(),
  $configuration as xs:string,
  $namespaces as map(xs:string, xs:string),
  $structure-path as xs:string
) as document-node()* {
  let $configuration := csv:parse($configuration, map { "separator": "tab", "header": "yes", "quotes": "no" })
  let $errors := transform:check-configuration($configuration, $structure-path)
    for $target-root-node in transform:make-children("/", $source-document, $configuration, $namespaces)
    return document { $target-root-node }
};

(: check the structure of the configured target elements :)
declare function transform:check-configuration(
  $configuration as document-node(),
  $structure-path as xs:string
) {
  
  (: parse the structure file :)
  let $structure := map:merge(
    for $line in file:read-text-lines($structure-path)
    let $element := fn:substring-before($line, " => ")
    let $parents := fn:tokenize(fn:substring-after($line, " => "), " | ")
    return map { $element: map:merge(
      for $parent in $parents
      return map { $parent: fn:true() }
    ) }
  )
  
  (: check if each target element exists and has an appropriate parent :)
  for $element in $configuration/csv/record/target-node[fn:not(fn:starts-with(text(), "@"))]/text()
  let $path := $element/../../target-path/text()
  let $parent := if ($path = "/") then $path else fn:replace(fn:replace($path, "/$", ""), ".*/", "")
  return if (fn:not(map:contains($structure, $element))) then
    fn:error(
      fn:QName("http://example.org/", "no-such-element"),
      fn:concat("ERROR: element """, $element, """ does not exist in EAD2002")
    )
  else if (fn:not($structure($element)($parent))) then
    fn:error(
      fn:QName("http://example.org/", "no-such-parent"),
      fn:concat("ERROR: element """, $element, """ cannot have element """, $parent, """ as parent in EAD2002")
    )
  else ()
};

(: make children for the given target path in the configuration :)
(: $target-path: the target path for which to make children as in the configuration :)
(: $source-node: the node (e.g. element) in the source document that corresponds to the given target path :)
(: $configuration: the parsed configuration file as a document node :)
(: $namespaces: map from namespace prefix to namespace URI :)
(: returns: a list of children nodes (attributes or elements) for the given target path :)
declare function transform:make-children(
  $target-path as xs:string,
  $source-node as item(),
  $configuration as document-node(),
  $namespaces as map(xs:string, xs:string)
) as node()* {

  (: go through the target nodes defined for this target path in order of configuration :)
  for $configuration-record in $configuration/csv/record[target-path/text() = $target-path]

    (: go through the source nodes corresponding to each target node :)
    for $child-source-node in transform:evaluate-xquery($configuration-record/source-node/text(), $source-node)
    let $child-value := transform:evaluate-xquery($configuration-record/value/text(), $child-source-node)
    let $child-name := $configuration-record/target-node/text()
    return

      (: return an attribute :)
      if (fn:starts-with($child-name, "@")) then
        let $child-name := fn:substring($child-name, 2)
        let $name-prefix := fn:substring-before($child-name, ":")
        let $child-qname := if ($name-prefix) then fn:QName($namespaces($name-prefix), $child-name) else $child-name
        let $child := attribute { $child-qname } { $child-value }
        return if ($child-value) then $child else ()

      (: return an element :)
      else
        let $name-prefix := fn:substring-before($child-name, ":")
        let $child-qname := fn:QName($namespaces($name-prefix), $child-name)
        let $child-children := transform:make-children(fn:concat($target-path, $child-name, "/"), $child-source-node, $configuration, $namespaces)
        let $child := element { $child-qname } { $child-children, $child-value }
        return if ($child-children or transform:efb($child-value)) then $child else ()
};

(: evaluate an XQuery expression within a given context node :)
(: $xquery: the XQuery expression to evalute as a string :)
(: $context: the node (e.g. element) to use as context for the XQuery expression :)
(: returns: the list of atomic values or nodes that the XQuery expression evaluated to :)
declare function transform:evaluate-xquery(
  $xquery as xs:string?,
  $context as item()
) as item()* {
  if ($xquery) then xquery:eval($xquery, map { "": $context }) else ()
};

declare function transform:efb(
  $item as item()*
) as xs:boolean {
  if (fn:count($item) > 1) then
    let $ones := for $element in $item return if (transform:efb($element)) then 1 else ()
    return (fn:count($ones) > 0)
  else fn:boolean($item)
};
