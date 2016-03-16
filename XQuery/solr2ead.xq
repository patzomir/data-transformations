xquery version "3.0";

(: input file passed by BaseX :)
declare variable $input as xs:string external;

(: helper function to generate a sequence of elements:
 - $item is a <doc> element
 - $field is the value of the @name attribute of a <field> element
 - $tag is the tag of the elements to generate
:)
declare function local:gen-elements($item as element(), $field as xs:string, $tag as xs:string) as element()* {
  for $value in $item/*:field[@name=$field]
  return element {$tag} {data($value)}
};

let $collection := doc($input)/*:add
return <ead xmlns="urn:isbn:1-931666-22-9" xsi:schemaLocation="urn:isbn:1-931666-22-9 http://www.loc.gov/ead/ead.xsd" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <eadheader countryencoding="iso3166-1" dateencoding="iso8601" langencoding="iso639-2b" repositoryencoding="iso15511" scriptencoding="iso15924">
    <eadid countrycode="US">USHMM</eadid>
    <filedesc>
      <titlestmt>
        <titleproper>USHMM Files</titleproper>
      </titlestmt>
    </filedesc>
    <profiledesc>
      <creation>Created by EHRI on <date>{current-date()}</date>.</creation>
      <langusage>
        <language langcode="eng">English</language>
      </langusage>
    </profiledesc>
  </eadheader>
  <archdesc level="collection">
    <did>
      <abstract>Files transformed from Solr using XQuery.</abstract>
      <unitid>ushmm_041213</unitid>
      <unittitle>USHMM Files</unittitle>
    </did>
    <dsc>
      {
        for $file in $collection/*:doc
        return <c01 level="file">
          <did>
            {
              local:gen-elements($file, "brief_desc", "abstract")
            }
            <physdesc>
              {
                local:gen-elements($file, "dimensions", "dimensions")
              }
            </physdesc>
            {
              local:gen-elements($file, "id", "unitid"),
              local:gen-elements($file, "title", "unittitle"),
              local:gen-elements($file, "display_date", "unitdate")
            }
          </did>
          {
            local:gen-elements($file, "creator_bio", "bioghist")
          }
        </c01>
      }
    </dsc>
  </archdesc>
</ead>
