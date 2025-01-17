package org.opensiddur.api.data

import org.opensiddur.DbTest

trait CommonTestNotes {
  val prolog =
    """xquery version '3.1';
      import module namespace tcommon="http://jewishliturgy.org/test/tcommon"
       at "xmldb:exist:/db/apps/opensiddur-server/test/tcommon.xqm";

      import module namespace notes="http://jewishliturgy.org/api/data/notes"
        at "xmldb:exist:///db/apps/opensiddur-server/api/data/notes.xqm";
      import module namespace magic="http://jewishliturgy.org/magic"
        at "xmldb:exist:///db/apps/opensiddur-server/magic/magic.xqm";
                
      declare namespace html="http://www.w3.org/1999/xhtml";
      declare namespace tei="http://www.tei-c.org/ns/1.0";
      declare namespace j="http://jewishliturgy.org/ns/jlptei/1.0";
      declare namespace http="http://expath.org/ns/http-client";
      declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";
      declare namespace a="http://jewishliturgy.org/ns/access/1.0";
      """
}

class TestNotes extends DbTest with CommonTestNotes {
  override def beforeAll()  {
    super.beforeAll()

    setupUsers(2)
    setupResource("src/test/resources/api/data/notes/existing.xml", "existing", "notes", 1, Some("en"))
    setupResource("src/test/resources/api/data/notes/existing.xml", "existing_put", "notes", 1, Some("en"))
    setupResource("src/test/resources/api/data/notes/query.xml", "query", "notes", 1, Some("en"))
    setupResource("src/test/resources/api/data/notes/existing.xml", "no_access", "notes", 2,
      Some("en"), Some("everyone"), Some("rw-------"))
    setupResource("src/test/resources/api/data/notes/existing.xml", "no_write_access", "notes", 2,
      Some("en"), Some("everyone"), Some("rw-r--r--"))
  }

  override def afterAll()  {
    teardownResource("no_write_access", "notes", 2)
    teardownResource("no_access", "notes", 2)
    teardownResource("query", "notes", 1)
    teardownResource("existing_put", "notes", 1)
    teardownResource("existing", "notes", 1)
    teardownResource("valid", "notes", 1)
    teardownUsers(2)

    super.afterAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
  }

  describe("notes:get") {
    it("gets an existing resource") {
      xq("""notes:get("existing")""")
        .assertXPath("""exists($output/tei:TEI)""", "Returns a TEI resource")
        .go
    }
    
    it("fails to get a nonexisting resource") {
      xq("""notes:get("does_not_exist")""")
        .assertHttpNotFound
        .go
    }
    
    it("fails to get a resource with no read access") {
      xq("""notes:get("no_access")""")
        .assertHttpNotFound
        .go
    }
  }  

  describe("notes:list") {
    it("lists all resources") {
      xq("""notes:list("", 1, 100)""")
        .user("xqtest1")
        .assertSearchResults
        .assertXPath("""count($output//html:li[@class="result"])>=1""", "returns at least 1 result")
        .assertXPath("""
          every $li in $output//html:li[@class="result"]
          satisfies exists($li/html:a[@class="alt"][@property="access"])
        """, "results include a pointer to access API")
        .go
    }

    it("does not list resources with no access when unauthenticated") {
      xq("""notes:list("", 1, 100)""")
        .assertSearchResults
        .assertXPath("""count($output//html:li[@class="result"])>=1""", "returns at least 1 result")
        .assertXPath("""empty($output//html:li[@class="result"]/html:a[@class="document"]/@href[contains(., "no_access")])""",
          "does not list resource with no read access")
        .go
    }
    
    it("lists some resources") {
      xq("""notes:list("", 1, 2)""")
        .user("xqtest1")
        .assertXPath("""count($output//html:li[@class="result"])=2""", "returns 2 results")
        .assertSearchResults
        .go
    }
    
    it("responds to a query") {
      xq("""notes:list("query", 1, 100)""")
        .assertXPath("""count($output//html:ol[@class="results"]/html:li)=1""", "returns 1 result")
        .assertSearchResults
        .go
    }
  }
  
  describe("notes:post") {
    val validDoc = readXmlFile("src/test/resources/api/data/notes/valid.xml")
    val invalidDoc = readXmlFile("src/test/resources/api/data/notes/invalid.xml")
    it("posts a valid resource") {
      xq(s"""notes:post(document { $validDoc })""")
        .user("xqtest1")
        .assertHttpCreated
        .assertXPathEquals("collection('/db/data/notes')[descendant::tei:title[@type='main'][.='Valid']]//tei:revisionDesc/tei:change[1]",
          "a change record has been added",
          """<tei:change xmlns:tei="http://www.tei-c.org/ns/1.0" type="created" who="/user/xqtest1"
                        when="..."/>"""
        )
        .go
    }
    
    it("fails to post a valid resource unauthenticated") {
      xq(s"""notes:post(document { $validDoc })""")
        .assertHttpUnauthorized
        .go
    }
    
    it("fails to post an invalid resource") {
      xq(s"""notes:post(document { $invalidDoc })""")
        .user("xqtest1")
        .assertHttpBadRequest
        .go
    }
  }
  
  describe("notes:post-note") {
    it("posts a valid new note to an existing resource") {
      xq("""notes:post-note("existing", <tei:note type="comment" xml:id="a-new-note">This is new.</tei:note>)""")
        .user("xqtest1")
        .assertHttpNoData
        .assertXPathEquals("doc('/db/data/notes/en/existing.xml')//tei:revisionDesc/tei:change[1]",
          "a change record has been added",
          """<tei:change xmlns:tei="http://www.tei-c.org/ns/1.0" type="edited" who="/user/xqtest1"
                          when="..."/>"""
        )
        .assertXPathEquals("doc('/db/data/notes/en/existing.xml')//j:annotations/tei:note[last()]",
          "a new note has been added",
          """<tei:note xmlns:tei="http://www.tei-c.org/ns/1.0"
                        type="comment" xml:id="a-new-note">This is new.</tei:note>"""
        )
        .assertXPath("""count(
          doc('/db/data/notes/en/existing.xml')//j:annotations/tei:note)=2""",
          "all the other notes remain undisturbed")
        .go
    }
    
    it("posts a valid replacement note to an existing resource") {
      xq("""notes:post-note("existing", <tei:note type="comment" xml:id="a-note">This is replaced.</tei:note>)""")
        .user("xqtest1")
        .assertHttpNoData
        .assertXPathEquals("doc('/db/data/notes/en/existing.xml')//tei:revisionDesc/tei:change[1]",
          "a change record has been added",
          """<tei:change xmlns:tei="http://www.tei-c.org/ns/1.0" type="edited" who="/user/xqtest1"
                          when="..."/>"""
        )
        .assertXPathEquals("doc('/db/data/notes/en/existing.xml')//j:annotations/tei:note[@xml:id='a-note']",
          "a new note has been replaced",
          """<tei:note xmlns:tei="http://www.tei-c.org/ns/1.0"
                        type="comment" xml:id="a-note">This is replaced.</tei:note>"""
        )
        .assertXPath("""count(
          doc('/db/data/notes/en/existing.xml')//j:annotations/tei:note[@xml:id='a-note'])=1""", "the existing note was the one that was edited")
        .go
    }

    it("fails to post a valid note to a nonexisting resource") {
      xq("""notes:post-note("Does-Not-Exist", <tei:note type="comment" xml:id="a-note">This is new.</tei:note>)""")
        .user("xqtest1")
        .assertHttpNotFound
        .go
    }
    
    it("fails to post an invalid note (lacking xml:id) to an existing resource") {
      xq("""notes:post-note("existing", <tei:note type="comment">This is new.</tei:note>)""")
        .user("xqtest1")
        .assertHttpBadRequest
        .go
    }
    
    it("fails to post a note to a resource without access") {
      xq("""notes:post-note("no_write_access", <tei:note type="comment" xml:id="a-note">This is new.</tei:note>)""")
        .user("xqtest1")
        .assertHttpForbidden
        .go
    }
  }
  
  describe("notes:put") {
    
    it("puts a valid resource to an existing resource") {
      val validDoc = readXmlFile("src/test/resources/api/data/notes/existing_after_put.xml")
      xq(s"""notes:put("existing_put", document { $validDoc })""")
        .user("xqtest1")
        .assertHttpNoData
        .assertXPathEquals("""doc('/db/data/notes/en/existing_put.xml')//tei:revisionDesc/tei:change[1]""",
          "a change record has been added",
          """<tei:change xmlns:tei="http://www.tei-c.org/ns/1.0" type="edited" who="/user/xqtest1"
                        when="..."/>"""
        )
        .go
    }
    
    it("fails to put a resource when unauthenticated") {
        val validDoc = readXmlFile("src/test/resources/api/data/notes/existing_after_put.xml")
        xq(s"""notes:put("existing", document { $validDoc })""")
          .assertHttpUnauthorized
          .go
    }
    
    it("fails to put a valid resource to a nonexisting resource") {
      val validDoc = readXmlFile("src/test/resources/api/data/notes/valid.xml")
      xq(s"""notes:put("does_not_exist", document { $validDoc })""")
        .user("xqtest1")
        .assertHttpNotFound
        .go
    }
    
    it("fails to put an invalid resource") {
      val invalidDoc = readXmlFile("src/test/resources/api/data/notes/invalid.xml")
      xq(s"""notes:put("existing", document { $invalidDoc })""")
        .user("xqtest1")
        .assertHttpBadRequest
        .go
    }

    it("fails to put an resource that was invalidated by an illegal change") {
      val invalidDoc = readXmlFile("src/test/resources/api/data/notes/invalid_after_put_illegal_revisiondesc.xml")
      xq(s"""notes:put("existing", document { $invalidDoc })""")
        .user("xqtest1")
        .assertHttpBadRequest
        .go
    }
  }

  describe("notes:get-access") {
    it("gets an access document for an existing resource") {
      xq("""notes:get-access("existing", ())""")
        .assertXPath("""$output/self::a:access""", "an access structure is returned")
        .go
    }

    it("fails to get access for a nonexistent resource") {
      xq("""notes:get-access("does_not_exist", ())""")
        .assertHttpNotFound
        .go
    }
  }
  
  describe("notes:put-access") {
    it("sets access with a valid access structure") {
      xq("""notes:put-access("existing", document{
        <a:access>
          <a:owner>xqtest1</a:owner>
          <a:group write="true">everyone</a:group>
          <a:world write="true" read="true"/>
        </a:access>
      })""")
        .user("xqtest1")
        .assertHttpNoData
        .go
    }

    it("fails with an invalid access structure") {
      xq("""notes:put-access("existing", document { <a:access>
          <a:invalid/>
        </a:access> })""")
        .user("xqtest1")
        .assertHttpBadRequest
        .go
    }

    it("fails for a resource with no write access") {
      xq("""notes:put-access("no_write_access", document{
        <a:access>
          <a:owner>xqtest1</a:owner>
          <a:group write="true">everyone</a:group>
          <a:world write="true" read="true"/>
        </a:access>
      })""")
        .user("xqtest1")
        .assertHttpForbidden
        .go
    }

    it("fails for a nonexistent resource") {
      xq("""notes:put-access("does_not_exist", document{
        <a:access>
          <a:owner>xqtest1</a:owner>
          <a:group write="true">everyone</a:group>
          <a:world write="true" read="true"/>
        </a:access>
      })""")
        .user("xqtest1")
        .assertHttpNotFound
        .go
    }
    
    it("fails to change access unauthenticated") {
      xq("""notes:put-access("existing", document{
        <a:access>
          <a:owner>xqtest1</a:owner>
          <a:group write="false">everyone</a:group>
          <a:world read="false" write="false"/>
        </a:access>
        })""")
        .assertHttpUnauthorized
        .go
    }
  }

}

class TestNotesDelete extends DbTest with CommonTestNotes {

  override def beforeEach(): Unit = {
    super.beforeEach()

    setupUsers(2)
    setupResource("src/test/resources/api/data/notes/existing.xml", "existing", "notes", 1, Some("en"))
    setupResource("src/test/resources/api/data/notes/existing.xml", "no_write_access", "notes", 2,
      Some("en"), Some("everyone"), Some("rw-r--r--"))
  }

  override def afterEach(): Unit = {
    super.afterEach()

    teardownResource("existing", "notes", 1)
    teardownResource("no_write_access", "notes", 2)
    teardownUsers(2)
  }

  describe("notes:delete") {
    it("removes an existing resource") {
      xq("""notes:delete("existing")""")
        .user("xqtest1")
        .assertHttpNoData
        .go
    }
    
    it("does not remove an existing resource when unauthenticated") {
      xq("""notes:delete("existing")""")
        .assertHttpUnauthorized
        .go
    }

    it("fails to remove a nonexisting resource") {
      xq("""notes:delete("does_not_exist")""")
        .user("xqtest1")
        .assertHttpNotFound
        .go
    }

    it("fails to remove a resource without write access") {
      xq("""notes:delete("no_write_access")""")
        .user("xqtest1")
        .assertHttpForbidden
        .go
    }
  }
}