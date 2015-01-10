<?xml version="1.0"?>
<!--
 * Distribution License:
 * JSword is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License, version 2.1 or later
 * as published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The License is available on the internet at:
 *       http://www.gnu.org/copyleft/lgpl.html
 * or by writing to:
 *      Free Software Foundation, Inc.
 *      59 Temple Place - Suite 330
 *      Boston, MA 02111-1307, USA
 *
 * Copyright: 2005
 *     The copyright to this program is held by it's authors.
 *
 * ID: $Id$
 -->
 <!--
 * Transforms OSIS to HTML for viewing within JSword browsers.
 * Note: There are custom protocols which the browser must handle.
 * 
 * @see gnu.lgpl.License for license details.
 *      The copyright to this program is held by it's authors.
 * @author Joe Walker [joe at eireneh dot com]
 * @author DM Smith [dmsmith555 at yahoo dot com]
 -->
 <xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0"
  xmlns:jsword="http://xml.apache.org/xalan/java"
  extension-element-prefixes="jsword">

  <!--  Version 3.0 is necessary to get br to work correctly. -->
  <xsl:output method="html" version="3.0" omit-xml-declaration="yes" indent="no"/>

  <!-- Be very careful about introducing whitespace into the document.
       strip-space merely remove space between one tag and another tag.
       This may cause significant whitespace to be removed.
       
       It is easy to have apply-templates on a line to itself which if
       it encounters text before anything else will introduce whitespace.
       With the browser we are using, span will introduce whitespace
       but font does not. Therefore we use font as a span.
    -->
  <!-- gdef and hdef refer to hebrew and greek definitions keyed by strongs -->
  <xsl:param name="greek.def.protocol" select="'gdef:'"/>
  <xsl:param name="hebrew.def.protocol" select="'hdef:'"/>
  <xsl:param name="lex.def.protocol" select="'lex:'"/>
  <!-- currently these are not used, but they are for morphologic forms -->
  <xsl:param name="greek.morph.protocol" select="'gmorph:'"/>
  <xsl:param name="hebrew.morph.protocol" select="'hmorph:'"/>

  <!-- The absolute base for relative references. -->
  <xsl:param name="baseURL" select="''"/>

  <!-- Whether to show Strongs or not -->
  <xsl:param name="Strongs" select="'false'"/>

  <!-- Whether to show morphologic forms or not -->
  <xsl:param name="Morph" select="'false'"/>

  <!-- Whether to start each verse on an new line or not -->
  <xsl:param name="VLine" select="'false'"/>

  <!-- Whether to show non-canonical "headings" or not -->
  <xsl:param name="Headings" select="'true'"/>

  <!-- Whether to show notes or not -->
  <xsl:param name="Notes" select="'true'"/>

  <!-- Whether to have linking cross references or not -->
  <xsl:param name="XRef" select="'true'"/>

  <!-- Whether to output no Verse numbers -->
  <xsl:param name="NoVNum" select="'false'"/>

  <!-- Whether to output Verse numbers or not -->
  <xsl:param name="VNum" select="'true'"/>

  <!-- Whether to output Chapter and Verse numbers or not -->
  <xsl:param name="CVNum" select="'false'"/>

  <!-- Whether to output Book, Chapter and Verse numbers or not -->
  <xsl:param name="BCVNum" select="'false'"/>

  <!-- Whether to output superscript verse numbers or normal size ones -->
  <xsl:param name="TinyVNum" select="'true'"/>

  <!-- Whether to output superscript verse numbers or normal size ones -->
  <xsl:param name="Variant" select="'x-1'"/>

  <!-- The CSS stylesheet to use. The url must be absolute. -->
  <xsl:param name="css"/>
  
  <!-- The order of display. Hebrew is rtl (right to left) -->
  <xsl:param name="v11n" select="'KJV'"/>

  <!-- The order of display. Hebrew is rtl (right to left) -->
  <xsl:param name="direction" select="'ltr'"/>

  <!-- The font that is passed in is in one of two forms:
    FamilyName-STYLE-size, where STYLE is either PLAIN, BOLD, ITALIC or BOLDITALIC
    or
    FamilyName,style,size, where STYLE is 0 for PLAIN, 1 for BOLD, 2 for ITALIC or 3 for BOLDITALIC.
    This needs to be changed into a CSS style specification
  -->
  <xsl:param name="font" select="Serif"/>

  <xsl:variable name="fontspec">
      <xsl:call-template name="generateFontStyle">
        <xsl:with-param name="fontspec" select="$font"/>
        <xsl:with-param name="style">css</xsl:with-param>
      </xsl:call-template>
  </xsl:variable>

  <!-- Create a versification from which verse numbers are understood -->
  <xsl:variable name="v11nf" select="jsword:org.crosswire.jsword.versification.system.Versifications.instance()"/>
  <!-- Create a global key factory from which OSIS ids will be generated -->
  <xsl:variable name="keyf" select="jsword:org.crosswire.jsword.passage.PassageKeyFactory.instance()"/>
  <!-- Create a global number shaper that can transform 0-9 into other number systems. -->
  <xsl:variable name="shaper" select="jsword:org.crosswire.common.icu.NumberShaper.new()"/>

  <!--=======================================================================-->
  <xsl:template match="/">
    <html dir="{$direction}">
      <head>
        <base href="{$baseURL}"/>
        <style type="text/css">
          BODY { background:white; <xsl:value-of select="$fontspec" /> }
          A { text-decoration: none; }
          A.strongs { color: black; text-decoration: none; }
          SUB.strongs { font-size: 75%; color: red; }
          SUB.morph { font-size: 75%; color: blue; }
          SUB.lemma { font-size: 75%; color: red; }
          SUP.verse { font-size: 75%; color: gray; }
          SUP.note { font-size: 75%; color: green; }
          FONT.lex { color: red; }
          FONT.jesus { color: red; }
          FONT.speech { color: blue; }
          FONT.strike { text-decoration: line-through; }
          FONT.small-caps { font-variant: small-caps; }
          FONT.inscription { font-weight: bold; font-variant: small-caps; }
          FONT.divineName { font-variant: small-caps; }
          FONT.normal { font-variant: normal; }
          FONT.caps { text-transform: uppercase; }
          H1 { font-size: 115%; font-weight: bold; }
          H2 { font-size: 110%; font-weight: bold; }
          H3 { font-size: 100%; font-weight: bold; }
          H4 { font-size:  90%; font-weight: bold; }
          H5 { font-size:  85%; font-weight: bold; }
          H6 { font-size:  80%; font-weight: bold; }
          .heading { color: #669966; text-align: center; }
          .canonical { color: #666699; }
          .gen { color: #996666; }
          div.margin { font-size:90%; }
          TABLE { width:100% }
          TD.notes { width:20%; background:#f4f4e8; }
          TD.text { width:80%; }
          <!-- the following are for dictionary entries -->
          FONT.orth { font-weight: bold; }
          FONT.pron { font-style: italic; }
          FONT.def  { font-style: italic; }
          FONT.usg  { font-style: plain; }
        </style>
        <!-- Always include the user's stylesheet even if "" -->
        <link rel="stylesheet" type="text/css" href="{$css}" title="styling" />
      </head>
      <body>
        <!-- If there are notes, output a table with notes in the 2nd column. -->
        <!-- There is a rendering bug which prevents the notes from adhering to the right edge. -->
        <xsl:choose>
          <xsl:when test="$Notes = 'true' and //note[not(@type = 'x-strongsMarkup')]">
            <xsl:choose>
              <xsl:when test="$direction != 'rtl'">
                <table cols="2" cellpadding="5" cellspacing="5">
                  <tr>
                    <!-- The two rows are swapped until the bug is fixed. -->
                    <td valign="top" class="notes">
                      <p>&#160;</p>
                      <xsl:apply-templates select="//verse" mode="print-notes"/>
                    </td>
                    <td valign="top" class="text">
                      <xsl:apply-templates/>
                    </td>
                  </tr>
                </table>
              </xsl:when>
              <xsl:otherwise>
                <!-- reverse the table for Right to Left languages -->
                <table cols="2" cellpadding="5" cellspacing="5">
                  <!-- In a right to left, the alignment should be reversed too -->
                  <tr align="right">
                    <td valign="top" class="text">
                      <xsl:apply-templates/>
                    </td>
                    <td valign="top" class="notes">
                      <p>&#160;</p>
                      <xsl:apply-templates select="//note" mode="print-notes"/>
                    </td>
                  </tr>
                </table>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates/>
          </xsl:otherwise>
        </xsl:choose>
      </body>
    </html>
  </xsl:template>

  <!--=======================================================================-->
  <!--
    == A proper OSIS document has osis as it's root.
    == We dig deeper for it's content.
    -->
  <xsl:template match="osis">
    <xsl:apply-templates/>
  </xsl:template>

  <!--=======================================================================-->
  <!--
    == An OSIS document may contain more that one work.
    == Each work is held in an osisCorpus element.
    == If there is only one work, then this element will (should) be absent.
    == Process each document in turn.
    == It might be reasonable to dig into the header element of each work
    == and get its title.
    == Otherwise, we ignore the header and work elements and just process
    == the osisText elements.
    -->
  <xsl:template match="osisCorpus">
    <xsl:apply-templates select="osisText"/>
  </xsl:template>

  <!--=======================================================================-->
  <!--
    == Each work has an osisText element.
    == We ignore the header and work elements and process its div elements.
    == While divs can be milestoned, the osisText element requires container
    == divs.
    -->
  <xsl:template match="osisText">
    <xsl:apply-templates select="div"/>
  </xsl:template>
  
  <!-- Ignore headers and its elements -->
  <xsl:template match="header"/>
  <xsl:template match="revisionDesc"/>
  <xsl:template match="work"/>
   <!-- <xsl:template match="title"/> who's parent is work -->
  <xsl:template match="contributor"/>
  <xsl:template match="creator"/>
  <xsl:template match="subject"/>
  <!-- <xsl:template match="date"/> who's parent is work -->
  <xsl:template match="description"/>
  <xsl:template match="publisher"/>
  <xsl:template match="type"/>
  <xsl:template match="format"/>
  <xsl:template match="identifier"/>
  <xsl:template match="source"/>
  <xsl:template match="language"/>
  <xsl:template match="relation"/>
  <xsl:template match="coverage"/>
  <xsl:template match="rights"/>
  <xsl:template match="scope"/>
  <xsl:template match="workPrefix"/>
  <xsl:template match="castList"/>
  <xsl:template match="castGroup"/>
  <xsl:template match="castItem"/>
  <xsl:template match="actor"/>
  <xsl:template match="role"/>
  <xsl:template match="roleDesc"/>
  <xsl:template match="teiHeader"/>
  <xsl:template match="refSystem"/>


  <!-- Ignore titlePage -->
  <xsl:template match="titlePage"/>

  <!--=======================================================================-->
  <!-- 
    == Div provides the major containers for a work.
    == Divs are milestoneable.
    -->
  <xsl:template match="div[@type='x-center']">
    <div align="center">
      <xsl:apply-templates/>
    </div>
  </xsl:template>

  <xsl:template match="div">
    <xsl:apply-templates/>
  </xsl:template>

  <xsl:template match="div" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>

  <!--=======================================================================-->
  <!-- Handle verses as containers and as a start verse.                     -->
  <xsl:template match="verse[not(@eID)]">
    <!-- Handle the KJV paragraph marker. -->
    <xsl:if test="milestone[@type = 'x-p'] or q[@who = 'Jesus']/milestone[@type = 'x-p']"><br/><br/></xsl:if>
    <!-- If the verse doesn't start on its own line and -->
    <!-- the verse is not the first verse of a set of siblings, -->
    <!-- output an extra space. -->
    <xsl:if test="$VLine = 'false' and preceding-sibling::*[local-name() = 'verse']">
      <xsl:text>&#160;</xsl:text>
    </xsl:if>
    <!-- Always output the verse -->
    <xsl:choose>
      <xsl:when test="$VLine = 'true'">
        <div class="l"><a name="{@osisID}"><xsl:call-template name="versenum"/></a><xsl:apply-templates/></div>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="versenum"/><xsl:apply-templates/>
        <!-- Follow the verse with an extra space -->
        <!-- when they don't start on lines to themselves -->
        <xsl:text> </xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="verse[not(@eID)]" mode="jesus">
    <!-- output each preverse element in turn -->
    <xsl:for-each select=".//*[@subType = 'x-preverse' or @subtype = 'x-preverse']">
      <xsl:choose>
        <xsl:when test="local-name() = 'title'">
          <xsl:call-template name="render-title"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    <!-- Handle the KJV paragraph marker. -->
    <xsl:if test="milestone[@type = 'x-p']"><br/><br/></xsl:if>
    <!-- If the verse doesn't start on its own line and -->
    <!-- the verse is not the first verse of a set of siblings, -->
    <!-- output an extra space. -->
    <xsl:if test="$VLine = 'false' and preceding-sibling::*[local-name() = 'verse']">
      <xsl:text>&#160;</xsl:text>
    </xsl:if>
    <!-- Always output the verse -->
    <xsl:choose>
      <xsl:when test="$VLine = 'true'">
        <div class="l"><a name="{@osisID}"><xsl:call-template name="versenum"/></a><xsl:apply-templates mode="jesus"/></div>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="versenum"/><xsl:apply-templates mode="jesus"/>
        <!-- Follow the verse with an extra space -->
        <!-- when they don't start on lines to themselves -->
        <xsl:text> </xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="verse" mode="print-notes">
    <xsl:if test=".//note[not(@type) or not(@type = 'x-strongsMarkup')]">
      <xsl:variable name="versification" select="jsword:getVersification($v11nf, $v11n)"/>
      <xsl:variable name="passage" select="jsword:getValidKey($keyf, $versification, @osisID)"/>
      <a href="#{substring-before(concat(@osisID, ' '), ' ')}">
        <xsl:value-of select="jsword:getName($passage)"/>
      </a>
      <xsl:apply-templates select=".//note" mode="print-notes" />
      <div><xsl:text>&#160;</xsl:text></div>
    </xsl:if>
  </xsl:template>

  <xsl:template name="versenum">
    <!-- Are verse numbers wanted? -->
    <xsl:if test="$NoVNum = 'false'">
      <!-- An osisID can be a space separated list of them -->
      <xsl:variable name="firstOsisID" select="substring-before(concat(@osisID, ' '), ' ')"/>
      <xsl:variable name="book" select="substring-before($firstOsisID, '.')"/>
      <xsl:variable name="chapter" select="jsword:shape($shaper, substring-before(substring-after($firstOsisID, '.'), '.'))"/>
      <!-- If n is present use it for the number -->
      <xsl:variable name="verse">
        <xsl:choose>
          <xsl:when test="@n">
            <xsl:value-of select="jsword:shape($shaper, string(@n))"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="jsword:shape($shaper, substring-after(substring-after($firstOsisID, '.'), '.'))"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:variable>
      <xsl:variable name="versenum">
        <xsl:choose>
          <xsl:when test="$BCVNum = 'true'">
            <xsl:variable name="versification" select="jsword:getVersification($v11nf, $v11n)"/>
            <xsl:variable name="passage" select="jsword:getValidKey($keyf, $versification, @osisID)"/>
            <xsl:value-of select="jsword:getName($passage)"/>
          </xsl:when>
          <xsl:when test="$CVNum = 'true'">
            <xsl:value-of select="concat($chapter, ' : ', $verse)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$verse"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:variable>
      <!--
        == Surround versenum with something that forces a proper bidi context in Java.
        == Sup does not.
        -->
      <xsl:choose>
        <xsl:when test="$TinyVNum = 'true' and $Notes = 'true'">
          <a name="{@osisID}"><sup class="verse"><font><xsl:value-of select="$versenum"/></font></sup></a>
        </xsl:when>
        <xsl:when test="$TinyVNum = 'true' and $Notes = 'false'">
          <sup class="verse"><font><xsl:value-of select="$versenum"/></font></sup>
        </xsl:when>
        <xsl:when test="$TinyVNum = 'false' and $Notes = 'true'">
          <a name="{@osisID}">(<font><xsl:value-of select="$versenum"/></font>)</a>
          <xsl:text> </xsl:text>
        </xsl:when>
        <xsl:otherwise>
          (<font><xsl:value-of select="$versenum"/></font>)
          <xsl:text> </xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
    <xsl:if test="$VNum = 'false' and $Notes = 'true'">
      <a name="{@osisID}"></a>
    </xsl:if>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="a">
    <a href="{@href}"><xsl:apply-templates/></a>
  </xsl:template>

  <xsl:template match="a" mode="jesus">
    <a href="{@href}"><xsl:apply-templates mode="jesus"/></a>
  </xsl:template>

  <!--=======================================================================-->
  <!-- When we encounter a note, we merely output a link to the note. -->
  <xsl:template match="note[@type = 'x-strongsMarkup']"/>
  <xsl:template match="note[@type = 'x-strongsMarkup']" mode="jesus"/>
  <xsl:template match="note[@type = 'x-strongsMarkup']" mode="print-notes"/>

  <xsl:template match="note">
    <xsl:if test="$Notes = 'true'">
      <!-- If there is a following sibling that is a note, emit a separator -->
      <xsl:variable name="siblings" select="../child::node()"/>
      <xsl:variable name="next-position" select="position() + 1"/>
      <xsl:choose>
        <xsl:when test="name($siblings[$next-position]) = 'note'">
          <sup class="note"><a href="#note-{generate-id(.)}"><xsl:call-template name="generateNoteXref"/></a>, </sup>
        </xsl:when>
        <xsl:otherwise>
          <sup class="note"><a href="#note-{generate-id(.)}"><xsl:call-template name="generateNoteXref"/></a></sup>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <xsl:template match="note" mode="jesus">
    <xsl:if test="$Notes = 'true'">
      <!-- If there is a following sibling that is a note, emit a separator -->
      <xsl:variable name="siblings" select="../child::node()"/>
      <xsl:variable name="next-position" select="position() + 1"/>
      <xsl:choose>
        <xsl:when test="$siblings[$next-position] and name($siblings[$next-position]) = 'note'">
          <sup class="note"><a href="#note-{generate-id(.)}"><xsl:call-template name="generateNoteXref"/></a>, </sup>
        </xsl:when>
        <xsl:otherwise>
          <sup class="note"><a href="#note-{generate-id(.)}"><xsl:call-template name="generateNoteXref"/></a></sup>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="note" mode="print-notes">
    <div class="margin">
      <strong><xsl:call-template name="generateNoteXref"/></strong>
      <a name="note-{generate-id(.)}">
        <xsl:text> </xsl:text>
      </a>
      <xsl:apply-templates/>
    </div>
  </xsl:template>

  <!--
    == If the n attribute is present then use that for the cross ref otherwise create a letter.
    == Note: numbering restarts with each verse.
    -->
  <xsl:template name="generateNoteXref">
    <xsl:choose>
      <xsl:when test="@n">
        <xsl:value-of select="@n"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:number level="any" from="/osis//verse" format="a"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="p">
    <p><xsl:apply-templates/></p>
  </xsl:template>
  
  <xsl:template match="p" mode="jesus">
    <p><xsl:apply-templates mode="jesus"/></p>
  </xsl:template>
  
  <!--=======================================================================-->
  <xsl:template match="p" mode="print-notes">
    <!-- FIXME: This ignores text in the note. -->
    <!-- don't put para's in notes -->
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="w">
    <!-- Output the content followed by all the lemmas and then all the morphs. -->
    <xsl:apply-templates/>
    <xsl:if test="$Strongs = 'true' and (starts-with(@lemma, 'strong:') or starts-with(@lemma, 'x-Strongs:'))">
      <xsl:call-template name="lemma">
        <xsl:with-param name="lemma" select="@lemma"/>
      </xsl:call-template>
    </xsl:if>
    <xsl:if test="$Morph = 'true' and (starts-with(@morph, 'robinson:') or starts-with(@morph, 'x-Robinson:'))">
      <xsl:call-template name="morph">
        <xsl:with-param name="morph" select="@morph"/>
      </xsl:call-template>
    </xsl:if>
    <xsl:if test="$Strongs = 'true' and starts-with(@lemma, 'lemma.Strong:')">
      <xsl:call-template name="lemma">
        <xsl:with-param name="lemma" select="@lemma"/>
      </xsl:call-template>
    </xsl:if>
    <!--
        except when followed by a text node or non-printing node.
        This is true whether the href is output or not.
    -->
    <xsl:variable name="siblings" select="../child::node()"/>
    <xsl:variable name="next-position" select="position() + 1"/>
    <xsl:if test="$siblings[$next-position] and name($siblings[$next-position]) != ''">
      <xsl:text> </xsl:text>
    </xsl:if>
  </xsl:template>
  
  <xsl:template match="w" mode="jesus">
    <!-- Output the content followed by all the lemmas and then all the morphs. -->
    <xsl:apply-templates mode="jesus"/>
    <xsl:if test="$Strongs = 'true' and (starts-with(@lemma, 'strong:') or starts-with(@lemma, 'x-Strongs:'))">
      <xsl:call-template name="lemma">
        <xsl:with-param name="lemma" select="@lemma"/>
      </xsl:call-template>
    </xsl:if>
    <xsl:if test="$Morph = 'true' and (starts-with(@morph, 'robinson:') or starts-with(@morph, 'x-Robinson:'))">
      <xsl:call-template name="morph">
        <xsl:with-param name="morph" select="@morph"/>
      </xsl:call-template>
    </xsl:if>
    <!--
        except when followed by a text node or non-printing node.
        This is true whether the href is output or not.
    -->
    <xsl:variable name="siblings" select="../child::node()"/>
    <xsl:variable name="next-position" select="position() + 1"/>
    <xsl:if test="$siblings[$next-position] and name($siblings[$next-position]) != ''">
      <xsl:text> </xsl:text>
    </xsl:if>
  </xsl:template>
  
  <xsl:template name="lemma">
    <xsl:param name="lemma"/>
    <xsl:param name="part" select="0"/>
    <xsl:variable name="orig-lemma" select="substring-after($lemma, ':')"/>
    <xsl:variable name="protocol">
      <xsl:choose>
        <xsl:when test="substring($orig-lemma, 1, 1) = 'H'">
          <xsl:value-of select="$hebrew.def.protocol"/>
        </xsl:when>
        <xsl:when test="substring($orig-lemma, 1, 1) = 'G'">
          <xsl:value-of select="$greek.def.protocol"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$lex.def.protocol"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="separator">
      <xsl:choose>
        <xsl:when test="contains($orig-lemma, '|')">
          <xsl:value-of select="'|'"/>
        </xsl:when>
        <xsl:when test="contains($orig-lemma, ' ')">
          <xsl:value-of select="' '"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="sub">
      <xsl:choose>
        <xsl:when test="$separator != '' and $part = '0'">
          <xsl:value-of select="$part + 1"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$part"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$protocol = $lex.def.protocol">
        <font class="lex">[<xsl:value-of select="$orig-lemma"/>]</font>
      </xsl:when>
      <xsl:when test="$separator = ''">
        <!-- <sub class="strongs"><a href="{$protocol}{$orig-lemma}">S<xsl:number level="any" from="/osis//verse" format="1"/><xsl:number value="$sub" format="a"/></a></sub> -->
        <sub class="strongs"><a href="{$protocol}{$orig-lemma}"><xsl:value-of select="format-number(substring($orig-lemma,2),'#')"/></a></sub>
      </xsl:when>
      <xsl:otherwise>
        <!-- <sub class="strongs"><a href="{$protocol}{substring-before($orig-lemma, $separator)}">S<xsl:number level="single" from="/osis//verse" format="1"/><xsl:number value="$sub" format="a"/></a>, </sub> -->
        <sub class="strongs"><a href="{$protocol}{substring-before($orig-lemma, $separator)}"><xsl:value-of select="format-number(substring(substring-before($orig-lemma, $separator),2),'#')"/></a>, </sub>
        <xsl:call-template name="lemma">
          <xsl:with-param name="lemma" select="substring-after($lemma, $separator)"/>
          <xsl:with-param name="part">
            <xsl:choose>
              <xsl:when test="$sub">
                <xsl:value-of select="$sub + 1"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="1"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="morph">
    <xsl:param name="morph"/>
    <xsl:param name="part" select="0"/>
    <xsl:variable name="orig-work" select="substring-before($morph, ':')"/>
    <xsl:variable name="orig-morph" select="substring-after($morph, ':')"/>
    <xsl:variable name="protocol">
      <xsl:choose>
        <xsl:when test="starts-with($orig-work, 'robinson') or starts-with($orig-work, 'x-Robinson')">
          <xsl:value-of select="$greek.morph.protocol"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$hebrew.morph.protocol"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="separator">
      <xsl:choose>
        <xsl:when test="contains($orig-morph, '|')">
          <xsl:value-of select="'|'"/>
        </xsl:when>
        <xsl:when test="contains($orig-morph, ' ')">
          <xsl:value-of select="' '"/>
        </xsl:when>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="sub">
      <xsl:choose>
        <xsl:when test="$separator != '' and $part = '0'">
          <xsl:value-of select="$part + 1"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$part"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$separator = ''">
        <!-- <sub class="morph"><a href="{$protocol}{$orig-morph}">M<xsl:number level="any" from="/osis//verse" format="1"/><xsl:number value="$sub" format="a"/></a></sub> -->
        <sub class="morph"><a href="{$protocol}{$orig-morph}"><xsl:value-of select="$orig-morph"/></a></sub>
      </xsl:when>
      <xsl:otherwise>
        <!-- <sub class="morph"><a href="{$protocol}{substring-before($orig-morph, $separator)}">M<xsl:number level="single" from="/osis//verse" format="1"/><xsl:number value="$sub" format="a"/></a>, </sub> -->
        <sub class="morph"><a href="{$protocol}{substring-before($orig-morph, $separator)}"><xsl:value-of select="substring-before($orig-morph, $separator)"/></a>, </sub>
        <xsl:call-template name="morph">
          <xsl:with-param name="morph" select="substring-after($morph, $separator)"/>
          <xsl:with-param name="part">
            <xsl:choose>
              <xsl:when test="$sub">
                <xsl:value-of select="$sub + 1"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="1"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="seg">
    <xsl:choose>
      <xsl:when test="starts-with(@type, 'color:')">
        <font color="{substring-before(substring-after(@type, 'color: '), ';')}"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="starts-with(@type, 'font-size:')">
        <font size="{substring-before(substring-after(@type, 'font-size: '), ';')}"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="@type = 'x-variant'">
        <xsl:if test="@subType = $Variant">
          <xsl:apply-templates/>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="seg" mode="jesus">
    <xsl:choose>
      <xsl:when test="starts-with(@type, 'color:')">
        <font color="{substring-before(substring-after(@type, 'color: '), ';')}"><xsl:apply-templates mode="jesus"/></font>
      </xsl:when>
      <xsl:when test="starts-with(@type, 'font-size:')">
        <font size="{substring-before(substring-after(@type, 'font-size: '), ';')}"><xsl:apply-templates mode="jesus"/></font>
      </xsl:when>
      <xsl:when test="@type = 'x-variant'">
        <xsl:if test="@subType = $Variant">
          <xsl:apply-templates mode="jesus"/>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates mode="jesus"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <!--=======================================================================-->
  <!-- expansion is OSIS, expan is TEI -->
  <xsl:template match="abbr">
    <font class="abbr">
      <xsl:if test="@expansion">
        <xsl:attribute name="title">
          <xsl:value-of select="@expansion"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@expan">
        <xsl:attribute name="title">
          <xsl:value-of select="@expan"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:apply-templates/>
    </font>
  </xsl:template>

  <xsl:template match="abbr" mode="jesus">
    <font class="abbr">
      <xsl:if test="@expansion">
        <xsl:attribute name="title">
          <xsl:value-of select="@expansion"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@expan">
        <xsl:attribute name="title">
          <xsl:value-of select="@expan"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:apply-templates mode="jesus"/>
    </font>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="speaker[@who = 'Jesus']">
    <font class="jesus"><xsl:apply-templates mode="jesus"/></font>
  </xsl:template>

  <xsl:template match="speaker">
    <font class="speech"><xsl:apply-templates/></font>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="title">
    <xsl:call-template name="render-title"/>
  </xsl:template>

  <xsl:template match="title" mode="jesus">
    <xsl:call-template name="render-title"/>
  </xsl:template>

 <!--=======================================================================-->
  <xsl:template name="render-title">
    <!-- Always show canonical titles or if headings is turned on -->
    <xsl:if test="@canonical = 'true' or $Headings = 'true'">
      <xsl:variable name="heading">
        <xsl:choose>
          <xsl:when test="@canonical = 'true'">canonical</xsl:when>
          <xsl:when test="@type = 'x-gen'">gen</xsl:when>
          <xsl:otherwise>heading</xsl:otherwise>
        </xsl:choose>
      </xsl:variable>
      <xsl:choose>
        <xsl:when test="@level = '1'">
          <h1 class="{$heading}"><xsl:apply-templates/></h1>
        </xsl:when>
        <xsl:when test="@level = '2'">
          <h2 class="{$heading}"><xsl:apply-templates/></h2>
        </xsl:when>
        <xsl:when test="@level = '3'">
          <h3 class="{$heading}"><xsl:apply-templates/></h3>
        </xsl:when>
        <xsl:when test="@level = '4'">
          <h4 class="{$heading}"><xsl:apply-templates/></h4>
        </xsl:when>
        <xsl:when test="@level = '5'">
          <h5 class="{$heading}"><xsl:apply-templates/></h5>
        </xsl:when>
        <xsl:when test="@level = '6'">
          <h6 class="{$heading}"><xsl:apply-templates/></h6>
        </xsl:when>
        <xsl:when test="@type = 'x-gen'">
          <h4 class="{$heading}"><xsl:apply-templates/></h4>
        </xsl:when>
        <xsl:otherwise>
          <h3 class="{$heading}"><xsl:apply-templates /></h3>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <!--=======================================================================-->
  <xsl:template match="reference">
    <xsl:choose>
      <xsl:when test="$XRef = 'true'">
        <a href="bible://{@osisRef}"><xsl:apply-templates/></a>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="reference" mode="jesus">
    <xsl:choose>
      <xsl:when test="$XRef = 'true'">
        <a href="bible://{@osisRef}"><xsl:apply-templates mode="jesus"/></a>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates mode="jesus"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <!--=======================================================================-->
  <xsl:template match="caption">
    <div class="caption"><xsl:apply-templates/></div>
  </xsl:template>
  
  <xsl:template match="caption" mode="jesus">
    <div class="caption"><xsl:apply-templates/></div>
  </xsl:template>
  
  <xsl:template match="catchWord">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="catchWord" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>
  
  <!--
      <cell> is handled shortly after <table> below and thus does not appear
      here.
  -->
  
  <xsl:template match="closer">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="closer" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>
  
  <xsl:template match="date">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="date" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>
  
  <xsl:template match="divineName">
    <xsl:apply-templates mode="small-caps"/>
  </xsl:template>
  
  <xsl:template match="divineName" mode="jesus">
    <xsl:apply-templates mode="small-caps"/>
  </xsl:template>
  
  <xsl:template match="figure">
    <div class="figure">
      <xsl:choose>
        <xsl:when test="starts-with(@src, '/')">
          <img src="{concat($baseURL, @src)}"/>   <!-- FIXME: Not necessarily an image... -->
        </xsl:when>
        <xsl:otherwise>
          <img src="{concat($baseURL, '/',  @src)}"/>   <!-- FIXME: Not necessarily an image... -->
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates/>
    </div>
  </xsl:template>
  
  <xsl:template match="figure" mode="jesus">
    <div class="figure">
      <xsl:choose>
        <xsl:when test="starts-with(@src, '/')">
          <img src="{concat($baseURL, @src)}"/>   <!-- FIXME: Not necessarily an image... -->
        </xsl:when>
        <xsl:otherwise>
          <img src="{concat($baseURL, '/',  @src)}"/>   <!-- FIXME: Not necessarily an image... -->
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates mode="jesus"/>
    </div>
  </xsl:template>
  
  <xsl:template match="foreign">
    <em class="foreign"><xsl:apply-templates/></em>
  </xsl:template>
  
  <xsl:template match="foreign" mode="jesus">
    <em class="foreign"><xsl:apply-templates mode="jesus"/></em>
  </xsl:template>
  
  <!-- This is a subheading. -->
  <xsl:template match="head//head">
    <h5 class="head"><xsl:apply-templates/></h5>
  </xsl:template>
  
  <!-- This is a top-level heading. -->
  <xsl:template match="head">
    <h4 class="head"><xsl:apply-templates/></h4>
  </xsl:template>
  
  <xsl:template match="index">
    <a name="index{@id}" class="index"/>
  </xsl:template>

  <xsl:template match="inscription">
    <xsl:apply-templates mode="small-caps"/>
  </xsl:template>

  <xsl:template match="inscription" mode="jesus">
    <xsl:apply-templates mode="small-caps"/>
  </xsl:template>

  <xsl:template match="item">
    <li class="item"><xsl:apply-templates/></li>
  </xsl:template>

  <xsl:template match="item" mode="jesus">
    <li class="item"><xsl:apply-templates mode="jesus"/></li>
  </xsl:template>
  
  <!--
      <item> and <label> are covered by <list> below and so do not appear here.
  -->

  <xsl:template match="lg">
    <div class="lg"><xsl:apply-templates/></div>
  </xsl:template>
  
  <xsl:template match="lg" mode="jesus">
    <div class="lg"><xsl:apply-templates mode="jesus"/></div>
  </xsl:template>
  
  <xsl:template match="lg[@sID or @eID]"/>
  <xsl:template match="lg[@sID or @eID]" mode="jesus"/>

  <xsl:template match="l[@sID]">
	<xsl:call-template name="indent"/>
  </xsl:template>

  <xsl:template match="l[@sID]" mode="jesus">
    <xsl:call-template name="indent"/>
  </xsl:template>

  <xsl:template match="l[@eID]"><br/></xsl:template>
  <xsl:template match="l[@eID]" mode="jesus"><br/></xsl:template>

  <xsl:template match="l">
	<xsl:call-template name="indent"/><xsl:apply-templates/><br/>
  </xsl:template>
  
  <xsl:template match="l" mode="jesus">
    <xsl:apply-templates mode="jesus"/><br/>
  </xsl:template>

  <!-- Generate poetry indent. The x-indent values are from an old ESV module.
       This mechanism is not ideal. The visual appearance does not account for verse numbers.
    -->
  <xsl:template name="indent">
      <xsl:choose>
        <xsl:when test="@level = '1'">
          <xsl:text>&#160;&#160;&#160;&#160;</xsl:text>
        </xsl:when>
        <xsl:when test="@level = '2' or @type = 'x-indent'">
          <xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text>
        </xsl:when>
        <xsl:when test="@level = '3' or @type = 'x-indent-2'">
          <xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text>
        </xsl:when>
        <xsl:when test="@level = '4'">
          <xsl:text>&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>&#160;&#160;&#160;&#160;</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
  </xsl:template>

  <!-- While a BR is a break, if it is immediately followed by punctuation,
       indenting this rule can introduce whitespace.
    -->
  <xsl:template match="lb"><br/></xsl:template>
  <xsl:template match="lb" mode="jesus"><br/></xsl:template>

  <xsl:template match="list">
    <xsl:choose>
      <xsl:when test="label">
        <!-- If there are <label>s in the list, it's a <dl>. -->
        <dl class="list">
          <xsl:for-each select="node()">
            <xsl:choose>
              <xsl:when test="self::label">
                <dt class="label"><xsl:apply-templates/></dt>
              </xsl:when>
              <xsl:when test="self::item">
                <dd class="item"><xsl:apply-templates/></dd>
              </xsl:when>
              <xsl:when test="self::list">
                <dd class="list-wrapper"><xsl:apply-templates select="."/></dd>
              </xsl:when>
              <xsl:otherwise>
                <xsl:apply-templates/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </dl>
      </xsl:when>

      <xsl:otherwise>
        <!-- If there are no <label>s in the list, it's a plain old <ul>. -->
        <ul class="list">
          <xsl:for-each select="node()">
            <xsl:choose>
              <xsl:when test="self::item">
                <li class="item"><xsl:apply-templates/></li>
              </xsl:when>
              <xsl:when test="self::list">
                <li class="list-wrapper"><xsl:apply-templates select="."/></li>
              </xsl:when>
              <xsl:otherwise>
                <xsl:apply-templates/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </ul>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>


  <xsl:template match="list" mode="jesus">
    <xsl:choose>
      <xsl:when test="label">
        <!-- If there are <label>s in the list, it's a <dl>. -->
        <dl class="list">
          <xsl:for-each select="node()">
            <xsl:choose>
              <xsl:when test="self::label">
                <dt class="label"><xsl:apply-templates mode="jesus"/></dt>
              </xsl:when>
              <xsl:when test="self::item">
                <dd class="item"><xsl:apply-templates mode="jesus"/></dd>
              </xsl:when>
              <xsl:when test="self::list">
                <dd class="list-wrapper"><xsl:apply-templates select="." mode="jesus"/></dd>
              </xsl:when>
              <xsl:otherwise>
                <xsl:apply-templates mode="jesus"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </dl>
      </xsl:when>

      <xsl:otherwise>
        <!-- If there are no <label>s in the list, it's a plain old <ul>. -->
        <ul class="list">
          <xsl:for-each select="node()">
            <xsl:choose>
              <xsl:when test="self::item">
                <li class="item"><xsl:apply-templates mode="jesus"/></li>
              </xsl:when>
              <xsl:when test="self::list">
                <li class="list-wrapper"><xsl:apply-templates select="." mode="jesus"/></li>
              </xsl:when>
              <xsl:otherwise>
                <xsl:apply-templates mode="jesus"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:for-each>
        </ul>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="mentioned">
    <xsl:apply-templates/>
  </xsl:template>
  
  <xsl:template match="mentioned" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>
  
  <!-- Milestones represent characteristics of the original manuscript.
    == that are being preserved. For this reason, most are ignored.
    ==
    == The defined types are:
    == column   Marks the end of a column where there is a multi-column display.
    == footer   Marks the footer region of a page.
    == halfLine Used to mark half-line units if not otherwise encoded.
    == header   Marks the header region of a page.
    == line     Marks line breaks, particularly important in recording appearance of an original text, such as a manuscript.
    == pb       Marks a page break in a text.
    == screen   Marks a preferred place for breaks in an on-screen rendering of the text.
    == cQuote   Marks the location of a continuation quote mark, with marker containing the publishers mark.
    -->
  <!--  This is used by the KJV for paragraph markers. -->
  <xsl:template match="milestone[@type = 'x-p']"><xsl:text> </xsl:text><xsl:value-of select="@marker"/><xsl:text> </xsl:text></xsl:template>
  <xsl:template match="milestone[@type = 'x-p']" mode="jesus"><xsl:text> </xsl:text><xsl:value-of select="@marker"/><xsl:text> </xsl:text></xsl:template>

  <xsl:template match="milestone[@type = 'cQuote']">
    <xsl:value-of select="@marker"/>
  </xsl:template>

  <xsl:template match="milestone[@type = 'cQuote']" mode="jesus">
    <xsl:value-of select="@marker"/>
  </xsl:template>

  <xsl:template match="milestone[@type = 'line']"><br/></xsl:template>

  <xsl:template match="milestone[@type = 'line']" mode="jesus"><br/></xsl:template>

  <!--
    == Milestone start and end are deprecated.
    == At this point we expect them to not be in the document.
    == These have been replace with milestoneable elements.
    -->
  <xsl:template match="milestoneStart"/>
  <xsl:template match="milestoneEnd"/>
  
  <xsl:template match="name">
    <xsl:apply-templates/>
  </xsl:template>

  <xsl:template match="name" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>

  <!-- If there is a milestoned q then just output a quotation mark -->
  <xsl:template match="q[@sID or @eID]">
    <xsl:choose>
      <xsl:when test="@marker"><xsl:value-of select="@marker"/></xsl:when>
      <!-- The chosen mark should be based on the work's author's locale. -->
      <xsl:otherwise>"</xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="q[@sID or @eID]" mode="jesus">
    <xsl:choose>
      <xsl:when test="@marker"><xsl:value-of select="@marker"/></xsl:when>
      <!-- The chosen mark should be based on the work's author's locale. -->
      <xsl:otherwise>"</xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="q[@who = 'Jesus']">
    <font class="jesus"><xsl:value-of select="@marker"/><xsl:apply-templates mode="jesus"/><xsl:value-of select="@marker"/></font>
  </xsl:template>

  <xsl:template match="q[@type = 'blockquote']">
    <blockquote class="q"><xsl:value-of select="@marker"/><xsl:apply-templates/><xsl:value-of select="@marker"/></blockquote>
  </xsl:template>

  <xsl:template match="q[@type = 'blockquote']" mode="jesus">
    <blockquote class="q"><xsl:value-of select="@marker"/><xsl:apply-templates mode="jesus"/><xsl:value-of select="@marker"/></blockquote>
  </xsl:template>

  <xsl:template match="q[@type = 'citation']">
    <blockquote class="q"><xsl:value-of select="@marker"/><xsl:apply-templates/><xsl:value-of select="@marker"/></blockquote>
  </xsl:template>

  <xsl:template match="q[@type = 'citation']" mode="jesus">
    <blockquote class="q"><xsl:value-of select="@marker"/><xsl:apply-templates mode="jesus"/><xsl:value-of select="@marker"/></blockquote>
  </xsl:template>

  <xsl:template match="q[@type = 'embedded']">
    <xsl:choose>
      <xsl:when test="@marker">
        <xsl:value-of select="@marker"/><xsl:apply-templates/><xsl:value-of select="@marker"/>
      </xsl:when>
      <xsl:otherwise>
        <quote class="q"><xsl:apply-templates/></quote>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <xsl:template match="q[@type = 'embedded']" mode="jesus">
    <xsl:choose>
      <xsl:when test="@marker">
      <xsl:value-of select="@marker"/><xsl:apply-templates mode="jesus"/><xsl:value-of select="@marker"/>
      </xsl:when>
      <xsl:otherwise>
        <quote class="q"><xsl:apply-templates/></quote>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <!-- An alternate reading. -->
  <xsl:template match="rdg">
    <xsl:apply-templates/>
  </xsl:template>

   <xsl:template match="rdg" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>

  <!--
      <row> is handled near <table> below and so does not appear here.
  -->
  
  <xsl:template match="salute">
    <xsl:apply-templates/>
  </xsl:template>
  
 <!-- Avoid adding whitespace -->
  <xsl:template match="salute" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>

  <xsl:template match="signed">
    <xsl:apply-templates/>
  </xsl:template>

  <xsl:template match="signed" mode="jesus">
    <xsl:apply-templates mode="jesus"/>
  </xsl:template>

  <xsl:template match="speech">
    <div class="speech"><xsl:apply-templates/></div>
  </xsl:template>
  
  <xsl:template match="speech" mode="jesus">
    <div class="speech"><xsl:apply-templates mode="jesus"/></div>
  </xsl:template>

  <xsl:template match="table">
    <table class="table">
      <xsl:copy-of select="@rows|@cols"/>
      <xsl:if test="head">
        <thead class="head"><xsl:apply-templates select="head"/></thead>
      </xsl:if>
      <tbody><xsl:apply-templates select="row"/></tbody>
    </table>
  </xsl:template>

  <xsl:template match="row">
    <tr class="row"><xsl:apply-templates/></tr>
  </xsl:template>
  
  <xsl:template match="cell">
    <xsl:variable name="element-name">
      <xsl:choose>
        <xsl:when test="@role = 'label'">
          <xsl:text>th</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>td</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="cell-direction">
      <xsl:if test="@xml:lang">
        <xsl:call-template name="getDirection">
         <xsl:with-param name="lang"><xsl:value-of select="@xml:lang"/></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:variable>
    <xsl:element name="{$element-name}">
      <xsl:attribute name="class">cell</xsl:attribute>
      <xsl:attribute name="valign">top</xsl:attribute>
      <xsl:if test="@xml:lang">
        <xsl:attribute name="dir">
          <xsl:value-of select="$cell-direction"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="$cell-direction = 'rtl'">
        <xsl:attribute name="align">
          <xsl:value-of select="'right'"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@rows">
        <xsl:attribute name="rowspan">
          <xsl:value-of select="@rows"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@cols">
        <xsl:attribute name="colspan">
          <xsl:value-of select="@cols"/>
        </xsl:attribute>
      </xsl:if>
      <!-- hack alert -->
      <xsl:choose>
        <xsl:when test="$cell-direction = 'rtl'">
          <xsl:text>&#8235;</xsl:text><xsl:apply-templates/><xsl:text>&#8236;</xsl:text>
        </xsl:when>
        <xsl:when test="$cell-direction = 'ltr'">
          <xsl:text>&#8234;</xsl:text><xsl:apply-templates/><xsl:text>&#8236;</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:element>
  </xsl:template>

  <xsl:template match="transChange">
    <em><xsl:apply-templates/></em>
  </xsl:template>
  <xsl:template match="transChange" mode="jesus">
    <em><xsl:apply-templates/></em>
  </xsl:template>
  
  <!-- @type is OSIS, @rend is TEI -->
  <xsl:template match="hi">
    <xsl:variable name="style">
      <xsl:choose>
        <xsl:when test="@type">
          <xsl:value-of select="@type"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@rend"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$style = 'acrostic'">
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:when test="$style = 'bold'">
        <strong><xsl:apply-templates/></strong>
      </xsl:when>
      <xsl:when test="$style = 'emphasis'">
        <em><xsl:apply-templates/></em>
      </xsl:when>
      <xsl:when test="$style = 'illuminated'">
        <strong><em><xsl:apply-templates/></em></strong>
      </xsl:when>
      <xsl:when test="$style = 'italic'">
        <em><xsl:apply-templates/></em>
      </xsl:when>
      <xsl:when test="$style = 'line-through'">
        <font class="strike"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'normal'">
        <font class="normal"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'small-caps'">
        <font class="small-caps"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'sub'">
        <sub><xsl:apply-templates/></sub>
      </xsl:when>
      <xsl:when test="$style = 'super'">
        <sup><xsl:apply-templates/></sup>
      </xsl:when>
      <xsl:when test="$style = 'underline'">
        <u><xsl:apply-templates/></u>
      </xsl:when>
      <xsl:when test="$style = 'x-caps'">
        <font class="caps"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="hi" mode="jesus">
    <xsl:variable name="style">
      <xsl:choose>
        <xsl:when test="@type">
          <xsl:value-of select="@type"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="@rend"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$style = 'acrostic'">
        <xsl:apply-templates/>
      </xsl:when>
      <xsl:when test="$style = 'bold'">
        <strong><xsl:apply-templates/></strong>
      </xsl:when>
      <xsl:when test="$style = 'emphasis'">
        <em><xsl:apply-templates/></em>
      </xsl:when>
      <xsl:when test="$style = 'illuminated'">
        <strong><em><xsl:apply-templates/></em></strong>
      </xsl:when>
      <xsl:when test="$style = 'italic'">
        <em><xsl:apply-templates/></em>
      </xsl:when>
      <xsl:when test="$style = 'line-through'">
        <font class="strike"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'normal'">
        <font class="normal"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'small-caps'">
        <font class="small-caps"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:when test="$style = 'sub'">
        <sub><xsl:apply-templates/></sub>
      </xsl:when>
      <xsl:when test="$style = 'super'">
        <sup><xsl:apply-templates/></sup>
      </xsl:when>
      <xsl:when test="$style = 'underline'">
        <u><xsl:apply-templates/></u>
      </xsl:when>
      <xsl:when test="$style = 'x-caps'">
        <font class="caps"><xsl:apply-templates/></font>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--
    The following elements are actually TEI and there is some expectation
    that these will make it into OSIS.
  -->
  <xsl:template match="superentry">
    <!-- output each preverse element in turn -->
    <xsl:for-each select="entry|entryFree">
      <xsl:apply-templates/><br/><br/>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="entry">
    <xsl:apply-templates/>
  </xsl:template>

  <xsl:template match="entryFree">
    <xsl:apply-templates/>
  </xsl:template>

  <xsl:template match="form">
    <xsl:apply-templates/><br/>
  </xsl:template>

  <xsl:template match="orth">
    <font class="orth"><xsl:apply-templates/></font>
  </xsl:template>

  <xsl:template match="pron">
    <font class="pron"><xsl:apply-templates/></font>
  </xsl:template>

  <xsl:template match="etym">
    <font class="etym"><xsl:apply-templates/></font>
  </xsl:template>

  <xsl:template match="def">
    <font class="def"><xsl:apply-templates/></font>
  </xsl:template>

  <xsl:template match="usg">
    <font class="usg"><xsl:apply-templates/></font>
  </xsl:template>

  <xsl:template match="@xml:lang">
    <xsl:variable name="dir">
      <xsl:if test="@xml:lang">
        <xsl:call-template name="getDirection">
         <xsl:with-param name="lang"><xsl:value-of select="@xml:lang"/></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:variable>
    <xsl:if test="$dir">
      <xsl:attribute name="dir">
        <xsl:value-of select="$dir"/>
      </xsl:attribute>
    </xsl:if>
  </xsl:template>

  <xsl:template match="text()" mode="small-caps">
  <xsl:value-of select="translate(., 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/>
  </xsl:template>

  <!--
    Generate a css or an inline style representation of a font spec.
    The fontspec that is passed in is in one of two forms:
    FamilyName-STYLE-size, where STYLE is either PLAIN, BOLD, ITALIC or BOLDITALIC
    or
    FamilyName,style,size, where STYLE is 0 for PLAIN, 1 for BOLD, 2 for ITALIC or 3 for BOLDITALIC.

    The style attribute is css for a css style specification or anything else for an inline style one.
  -->
  <xsl:template name="generateFontStyle">
    <xsl:param name="fontspec"/>
    <xsl:param name="style"/>
    <xsl:variable name="fontSeparator">
      <xsl:choose>
        <xsl:when test="contains($fontspec, ',')">
          <xsl:value-of select="','"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="'-'"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="aFont">
      <xsl:choose>
        <xsl:when test="substring-before($fontspec, $fontSeparator) = ''"><xsl:value-of select="$fontspec"/>,0,16</xsl:when>
        <xsl:otherwise><xsl:value-of select="$fontspec"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="fontfamily" select="substring-before($aFont, $fontSeparator)" />
    <xsl:variable name="fontsize" select="substring-after(substring-after($aFont, $fontSeparator), $fontSeparator)" />
    <xsl:variable name="styling" select="substring-before(substring-after($aFont, $fontSeparator), $fontSeparator)" />
    <xsl:variable name="fontweight">
      <xsl:choose>
        <xsl:when test="$styling = '1' or $styling = '3' or contains($styling, 'bold')">bold</xsl:when>
        <xsl:otherwise>normal</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:variable name="fontstyle">
      <xsl:choose>
        <xsl:when test="$styling = '2' or $styling = '3' or contains($styling, 'italic')">italic</xsl:when>
        <xsl:otherwise>normal</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$style = 'css'">
        <xsl:value-of select='concat("font-family: &apos;", $fontfamily, "&apos;, Serif; ",
                                     "font-size:   ",       $fontsize,   "pt; ",
                                     "font-weight: ",       $fontweight, "; ",
                                     "font-style:  ",       $fontstyle,  ";")'/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select='concat("font-family=&apos;",  $fontfamily, "&apos;, Serif; ",
                                     "font-size=",          $fontsize,   "pt; ",
                                     "font-weight=",        $fontweight, "; ",
                                     "font-style=",         $fontstyle,  "; ")'/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--
    The direction is deduced from the xml:lang attribute and is assumed to be meaningful for those elements.
    Note: there is a bug that prevents dir=rtl from working.
    see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4296022 and 4866977
  -->
  <xsl:template name="getDirection">
    <xsl:param name="lang"/>
    <xsl:choose>
      <xsl:when test="$lang = 'he' or $lang = 'ar' or $lang = 'fa' or $lang = 'ur' or $lang = 'syr'">
        <xsl:value-of select="'rtl'"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'ltr'"/>
      </xsl:otherwise>
    </xsl:choose>
   </xsl:template>
  
</xsl:stylesheet>
