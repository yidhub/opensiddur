xquery version "3.1";
(:~ support functions for the REST API for data retrieval
 :
 : Open Siddur Project
 : Copyright 2011-2014 Efraim Feinstein <efraim@opensiddur.org>
 : Licensed under the GNU Lesser General Public License, version 3 or later
 :
 :) 
module namespace data="http://jewishliturgy.org/modules/data";

import module namespace api="http://jewishliturgy.org/modules/api"
  at "api.xqm";
import module namespace app="http://jewishliturgy.org/modules/app"
	at "app.xqm";
import module namespace didx="http://jewishliturgy.org/modules/docindex"
	at "docindex.xqm";
  
declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace j="http://jewishliturgy.org/ns/jlptei/1.0";
declare namespace error="http://jewishliturgy.org/errors";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

(:~ base of all data paths :)
declare variable $data:path-base := "/db/data";

(:~ convert a given path from an API path (may begin / or /api) to a database path
 : works only to find the resource. 
 : @param $api-path API path
 : @return The database path or empty sequence if a db path cannot be found  
 :)
declare function data:api-path-to-db(
	$api-path as xs:string 
	) as xs:string? {
	let $doc := data:doc($api-path)
	where exists($doc)
	return document-uri($doc)
};

(:~ Convert a database path to a path in the API
 : @param $db-path database path to convert
 : @return the API path or empty sequence 
 :)
declare function data:db-path-to-api(
	$db-path as xs:string
	) as xs:string? {
	let $norm-db-path := replace($db-path, "^(/db)?/", "/db/")
	let $doc-query := didx:query-by-path($norm-db-path)
	where exists($doc-query)
	return
        api:uri-of(string-join(
        ("/api",
         if ($doc-query/@data-type = "user")
         then ()
         else "data",
         $doc-query/@data-type,
         $doc-query/@resource), "/"))
};

(: Find the API path of a user by name
 : @param user name
 : @return API path of a user by name
 :)
declare function data:user-api-path(
  $name as xs:string
  ) as xs:string? {
  let $doc := collection("/db/data/user")//tei:idno[.=$name]/root(.)
  where $doc
  return
    concat(api:uri-of("/api/user/"), 
      encode-for-uri(
        replace(util:document-name($doc), "\.xml$", "")
      )
    )
};

declare function data:resource-name-from-title-and-number(
  $title as xs:string,
  $number as xs:integer
  ) as xs:string {
  string-join(
    ( (: remove diacritics in resource names and replace some special characters 
       : like strings of ,;=$:@ with dashes. The latter characters have special 
       : meanings in some URIs and are not always properly encoded on the client side
       :)
      encode-for-uri(replace(replace(normalize-space($title), "\p{M}", ""), "[,;:$=@]+", "-")), 
      if ($number)
      then ("-", string($number))
      else (), ".xml"
    ),
  "")
};

declare function data:find-duplicate-number(
  $type as xs:string,
  $title as xs:string,
  $n as xs:integer
  ) as xs:integer {
  if (exists(collection(concat($data:path-base, "/", $type))
    [util:document-name(.)=
      data:resource-name-from-title-and-number($title, $n)]
    ))
  then data:find-duplicate-number($type, $title, $n + 1)
  else $n
};

(:~ make the path of a new resource
 : @param $type The category of the resource (original|transliteration, eg)
 : @param $title The resource's human-readable title
 : @return (collection, resource)
 :)
declare function data:new-path-to-resource(
  $type as xs:string,
  $title as xs:string
  ) as xs:string+ {
  let $date := current-date()
  let $resource-name := 
    data:resource-name-from-title-and-number($title, 
      data:find-duplicate-number($type, $title, 0))
  return (
    app:concat-path(($data:path-base, $type, format-date($date, "[Y0001]/[M01]"))),
    $resource-name
  ) 
};

(:~ make the path of a new resource
 : @param $type The category of the resource (original|transliteration, eg)
 : @param $title The resource's human-readable title
 :)
declare function data:new-path(
  $type as xs:string,
  $title as xs:string
  ) as xs:string {
  let $new-paths := data:new-path-to-resource($type, $title)
  return
    string-join($new-paths, "/")
};

(:~ return a document from the collection hierarchy for $type 
 : given a resource name $name (without extension) :)
declare function data:doc(
  $type as xs:string,
  $name as xs:string
  ) as document-node()? {
  doc(didx:query-path($type, $name))
};

(:~ get a document using an api path, with or without /api :)
declare function data:doc(
  $api-path as xs:string
  ) as document-node()? {
  let $path := replace($api-path, "^((" || api:uri-of("/api") || ")|(/api))?/", "")
  let $tokens := tokenize($path, "/")
  let $token-offset := if ($tokens[1] = "data") then 1 else 0
  let $data-type := $tokens[1 + $token-offset]
  let $resource := $tokens[2 + $token-offset]
  return
    doc(didx:query-path($data-type, $resource))
};
